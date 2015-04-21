(ns api.route
  (:import [java.io ByteArrayInputStream]
           [java.util UUID]
           [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
           [com.amazonaws.auth.profile ProfileCredentialsProvider])
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.core.match :as match :refer (match)]
            [clojure.repl :refer [pst]]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as strng]
            [compojure.core :refer [routes GET PUT HEAD POST DELETE ANY context defroutes]
             :as compojure]
            [compojure.route :refer [not-found]]
            [compojure.handler :as handler]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.jsonp :as jsonp]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.util.response :refer [response content-type]]
            [ring.middleware.permacookie :refer [wrap-permacookie]]
            [ring.middleware.anti-forgery :as ring-anti-forgery
             :refer [wrap-anti-forgery]]
            [api.version]
            [api.session :as session]
            [api.authentication :as auth]
            [api.events :as events]
            [api.kinesis :as kinesis]
            [api.controllers.users :refer [create-new-user! get-user update-user!]]
            [api.controllers.promos :refer [create-new-promo! show-promo query-promo
                                            validate-promo calculate-promo
                                            update-promo! delete-promo!
                                            lookup-promos]]
            [api.controllers.offers :refer [create-new-offer! show-offer
                                            update-offer! delete-offer!
                                            lookup-offers get-available-offers
                                            wrap-record-rco-events]]
            [api.controllers.accounts :refer [get-account create-new-account!
                                              update-account! create-site-for-account!
                                              update-site-for-account!]]
            [api.controllers.email-subscribers :refer [create-email-subscriber!]]
            [api.controllers.metrics :refer [get-revenue get-additional-revenue get-lift get-promos get-rco]]
            [api.lib.detector :as detector]
            [api.system :refer [current-system]]
            [api.vbucket :refer [wrap-vbucket wrap-record-vbucket-assignment]]
            [clj-time.core :refer [before? after? now] :as t]
            [clj-time.coerce :as t-coerce]
            [amazonica.aws.s3]
            [amazonica.aws.s3transfer]
            [slingshot.slingshot :refer [try+]]))

(defonce cached-index (atom {:cached-at nil :content nil}))
(defonce cached-register (atom {:cached-at nil :content nil}))
(defonce cached-login (atom {:cached-at nil :content nil}))
(def promotably-session-cookie-name "promotably-session")

;;;;;;;;;;;;;;;;;;;
;;
;; Routes
;;
;;;;;;;;;;;;;;;;;;;

(defn- get-api-secret
  []
  (get-in current-system [:config :auth-token-config :api :api-secret]))

(def promo-code-regex #"[a-zA-Z0-9-_]{1,}")
(def offer-code-regex #"[a-zA-Z0-9-_]{1,}")

(defroutes promo-routes
  (context "/promos" []
           (POST ["/validation/:code", :code promo-code-regex]
                 [code] validate-promo)
           (GET ["/query/:code", :code promo-code-regex] [code] query-promo)
           (POST ["/calculation/:code", :code promo-code-regex]
                 [code] calculate-promo)))

(defroutes offer-routes
  (context "/offers" []
           (POST "/" [] create-new-offer!)
           (GET "/" [] lookup-offers)
           (DELETE ["/:offer-id", :offer-id offer-code-regex] [offer-id] delete-offer!)
           (GET ["/:offer-id", :offer-id offer-code-regex] [offer-id] show-offer)
           (PUT ["/:offer-id", :offer-id offer-code-regex] [offer-id] update-offer!)))

(defroutes api-routes
  (context "/api/v1" []
           (GET "/track" req (fn [r] (events/record-event (:kinesis current-system) r)))
           (POST "/email-subscribers" [] create-email-subscriber!)
           (GET "/rco" req (fn [r] (get-available-offers (:kinesis current-system) r)))
           (POST "/login" req (fn [r]
                                (let [auth-config (get-in current-system [:config :auth-token-config])]
                                  (auth/authenticate r auth-config get-user))))
           (POST "/logout" [] auth/invalidate-auth-cookies)
           (POST "/register" req (fn [r]
                                   (let [auth-config (get-in current-system [:config :auth-token-config])]
                                     (auth/validate-and-create-user r auth-config create-new-user!))))
           promo-routes))

;; TODO: secure-routes - wrapped in auth/wrap-authorized

(defroutes promo-secure-routes
  (context "/promos" []
           (POST "/" [] (fn [r] (create-new-promo! (merge
                                                    (:kinesis current-system)
                                                    (-> current-system :config :kinesis))
                                                   r)))
           (GET "/" [] lookup-promos)
           (DELETE ["/:promo-id", :promo-id promo-code-regex] [promo-id] delete-promo!)
           (GET ["/:promo-id", :promo-id promo-code-regex] [promo-id] show-promo)
           (PUT ["/:promo-id", :promo-id promo-code-regex] [promo-id] update-promo!)))

(defroutes metrics-secure-routes
  (context "/:site-id/metrics" []
           (GET "/additional-revenue" [] get-additional-revenue)
           (GET "/revenue" [] get-revenue)
           (GET "/lift" [] get-lift)
           (GET "/promos" [] get-promos)
           (GET "/rco" [] get-rco)))

(defroutes secure-routes
  (context "/api/v1" []
           (GET "/accounts" [] get-account)
           (POST "/accounts" [] create-new-account!)
           (PUT "/accounts/:account-id" [] update-account!)
           (GET "/users/:user-id" [user-id] (get-user user-id))
           (POST "/users" [] create-new-user!)
           (PUT "/users/:user-id" [] update-user!)
           (context "/sites" []
                    (POST "/" [] create-site-for-account!)
                    (PUT "/:site-id" [] update-site-for-account!)
                    metrics-secure-routes)
           promo-secure-routes
           offer-routes))

(defn- fetch-static
  [cloudwatch-recorder profile-name bucket filename cached]
  (try
    (cloudwatch-recorder "static-fetch" 1 :Count)
    (let [^com.amazonaws.auth.AWSCredentialsProvider cp
          (if profile-name
            (ProfileCredentialsProvider. profile-name)
            (DefaultAWSCredentialsProviderChain.))
          _ (log/logf :info "Fetching s3://%s/%s using credentials '%s'."
                      bucket
                      filename
                      profile-name)
          resp (if profile-name
                 (amazonica.aws.s3/get-object cp
                                              :bucket-name bucket
                                              :key filename)
                 (amazonica.aws.s3/get-object bucket filename))
          content (slurp (:object-content resp))]
      (if cached
        (reset! cached {:content content :cached-at (now)})
        content))
    (catch Throwable t
      (cloudwatch-recorder "static-missing" 1 :Count)
      (log/logf :error
                "Can't fetch static file. Bucket %s, file '%s' exception %s."
                bucket
                filename
                t))))

(defn serve-cached-static
  [{:keys [cloudwatch-recorder] :as req} profile-name bucket filename cached]
  ;; if it's old, refresh it, but still return current copy
  (let [expires (t/plus (now) (t/minutes 5))]
    (if (or (nil? (:content @cached))
            (after? (:cached-at @cached) expires))
      (future (fetch-static cloudwatch-recorder profile-name bucket filename cached))))
  (if (:content @cached)
    (:content @cached)
    {:status 404}))

(defn serve-cached-index
  [{:keys [cloudwatch-recorder] :as req}]
  (cloudwatch-recorder "serve-index" 1 :Count)
  (let [c (-> current-system :config)
        api-secret (get-api-secret)]
    (if (auth/authorized? req api-secret)
      (serve-cached-static req
                           (-> c :kinesis :aws-credential-profile)
                           (-> c :dashboard :artifact-bucket)
                           (-> c :dashboard :index-filename)
                           cached-index)
      {:status 303
       :headers {"Location" "/login"}})))

(defn serve-cached-register
  [{:keys [cloudwatch-recorder] :as req}]
  (cloudwatch-recorder "serve-register" 1 :Count)
  (let [c (-> current-system :config)
        api-secret (get-api-secret)]
    (if-not (auth/authorized? req api-secret)
      (serve-cached-static req
                           (-> c :kinesis :aws-credential-profile)
                           (-> c :dashboard :artifact-bucket)
                           (-> c :dashboard :register-filename)
                           cached-register)
      {:status 303
       :headers {"Location" "/"}})))

(defn serve-cached-login
  [{:keys [cloudwatch-recorder] :as req}]
  (cloudwatch-recorder "serve-login" 1 :Count)
  (let [c (-> current-system :config)
        api-secret (get-api-secret)]
    (if-not (auth/authorized? req api-secret)
      (serve-cached-static req
                           (-> c :kinesis :aws-credential-profile)
                           (-> c :dashboard :artifact-bucket)
                           (-> c :dashboard :login-filename)
                           cached-login)
      {:status 303
       :headers {"Location" "/"}})))

(defn serve-404-page
  [req]
  {:status 404 :body "<h1>Not Found</h1>"})

(defn serve-404-or-index
  [req]
  (if (.contains (:uri req) ".")
    (serve-404-page req)
    (serve-cached-index req)))

(defn health-check
  [request]
  {:status 200
   :headers {"Content-Type" "application/json; charset=UTF-8"}
   :body (json/write-str {:version api.version/version
                          :control-group (= (:test-bucket (:session request)))})})

(defroutes all-routes
  (GET "/health-check" [] health-check)
  api-routes
  (GET "/" [] serve-cached-index)
  (GET "/register" [] serve-cached-register)
  (GET "/login" [] serve-cached-login)
  (auth/wrap-authorized secure-routes get-api-secret)
  (GET "*" [] serve-404-or-index))

;;;;;;;;;;;;;;;;;;
;;
;; Middleware
;;
;;;;;;;;;;;;;;;;;;

(defn wrap-if
  [handler pred wrapper & args]
  (if pred
    (apply wrapper handler args)
    handler))

(defn wrap-cloudwatch [handler]
  (fn [req]
    (handler (assoc req :cloudwatch-recorder (get-in current-system [:cloudwatch :recorder])))))

(defn wrap-request-logging [handler]
  (fn [{:keys [request-method uri cloudwatch-recorder params] :as req}]
    (let [start  (System/currentTimeMillis)
          {:keys [context status] :as resp} (handler req)
          finish (System/currentTimeMillis)
          total  (- finish start)]
      (when #((get-in current-system [:config :env]) #{:dev :test :integration})
        (log/info (format "%-6s %-4d %s (%dms)"
                          request-method
                          (:status resp)
                          uri
                          total)))
      (when-let [ep (:cloudwatch-endpoint context)]
        (cloudwatch-recorder "response-time" total :Milliseconds :dimensions {:endpoint ep})
        (cloudwatch-recorder (str "status-" status) 1 :Count :dimensions {:endpoint ep})
        (when-let [site-id (:site-id params)]
          (cloudwatch-recorder "response-time" total :Milliseconds :dimensions {:endpoint ep :site-id site-id})
          (cloudwatch-recorder (str "status-"status) 1 :Count :dimensions {:endpoint ep :site-id site-id})))
      (dissoc resp :context))))


(defn wrap-argument-exception [handler]
  "Catch exceptions Schema throws when failing to validate passed parameters"
  (fn [req]
    (try+
      (handler req)
      (catch [:type :argument-error]
             {:keys [body-params error]}
        {:status 400
         :body error}))))

(defn wrap-stacktrace
  "ring.middleware.stacktrace only catches exception, not Throwable, so we replace it here."
  [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t
           (log/error t :request request)
           {:status 500
            :headers {"Content-Type" "text/plain; charset=UTF-8"}
            :body (with-out-str
                    (binding [*err* *out*]
                      (pst t)
                      (println "\n\nREQUEST:\n")
                      (pprint request)))}))))

(defn wrap-save-the-raw-body
  ""
  [handler]
  (fn [request]
    (if-let [slurped (if (:body request) (-> request :body slurp))]
      (handler (-> request
                   (assoc :raw-body slurped)
                   (assoc :body (ByteArrayInputStream. (.getBytes slurped)))))
      (handler request))))

(defn mark-new-session
  [response request sid ssid]
  (let [data (cond-> {:created-at (t-coerce/to-string (t/now))
                      :shopper-id (:shopper-id request)
                      :site-shopper-id ssid
                      :request-headers (:headers request)}
                     sid (assoc :site-id sid))]
    (-> response
        (assoc-in [:session :initial-request-headers]
                  (:headers request))
        (assoc :new-session-data data))))

(defn wrap-record-new-session
  "When a new session is started, record relevant data to kinesis."
  [handler & [{:keys [cookie-name]}]]
  (fn [{:keys [cloudwatch-recorder] :as request}]
    (let [response (handler request)
          session-id (:session/key response)]
      (if-let [k-data (:new-session-data response)]
        (let [control? (= :control (:test-bucket response))
              k-data* (-> k-data
                          (assoc :control-group control?)
                          (assoc :session-id session-id)
                          (assoc :event-format-version "1")
                          (assoc :event-name :session-start))
              dims {:site-id (-> k-data :site-id str)
                    :control (if control? "1" "0")}]
          (if session-id
            (do
              (cloudwatch-recorder "session-start" 1 :Count)
              (cloudwatch-recorder "session-start" 1 :Count
                                   :dimensions dims)
              (kinesis/record-event! (:kinesis current-system)
                                     :session-start
                                     k-data*))
            (do
              (log/logf :error "Error recording session start: missing session id.")
              (cloudwatch-recorder "session-start-missing-session-id" 1 :Count)
              (cloudwatch-recorder "session-start-missing-session-id" 1 :Count
                                   :dimensions dims)))))
      response)))

(defn wrap-ensure-session
  "Ensure that all relevant data is in the response session map and
  thence recorded to redis."
  [handler & [options]]
  (fn [request]
    (let [{:keys [include-routes exclude-routes]} options
          route-excluded? (if exclude-routes
                            (some map? (map #(% request) exclude-routes))
                            false)
          route-included? (if include-routes
                            (some map? (map #(% request) include-routes))
                            true)]
      (if (and (not route-excluded?) route-included?)
        (let [new? (or (empty? (:session request)) (nil? (:session request)))
              sid (or
                   (-> request :form-params :site-id)
                   (-> request :query-params :site-id)
                   (-> request :multipart-params :site-id)
                   (-> request :body-params :site-id)
                   (-> request :params :site-id))
              ssid (or
                    (-> request :form-params :site-shopper-id)
                    (-> request :query-params :site-shopper-id)
                    (-> request :multipart-params :site-shopper-id)
                    (-> request :body-params :site-shopper-id)
                    (-> request :params :site-shopper-id))
              s (-> current-system :config :session-length-in-seconds)
              expires (t-coerce/to-string (t/plus (t/now) (t/seconds s)))
              response (handler request)
              response (cond->
                        response
                        new? (update-in [:session :started-at]
                                        (constantly (t-coerce/to-string (t/now))))
                        sid (update-in [:session :site-id]
                                       (constantly sid))
                        ssid (update-in [:session :site-shopper-id]
                                        (constantly ssid))
                        true (update-in [:session :last-request-at]
                                        (constantly (t-coerce/to-string (t/now))))
                        true (update-in [:session :expires]
                                        (constantly expires))
                        true (update-in [:session :shopper-id]
                                        (constantly (:shopper-id request))))]
          (if new?
            (mark-new-session response request sid ssid)
            response))
        (handler request)))))

(defn wrap-detect-user-agent
  "Add a :user-agent key to the session map indicating the requestor's device type."
  [handler]
  (fn [{:keys [cloudwatch-recorder] :as request}]
    (if (-> request :session :user-agent)
      (handler request)
      (let [ua (detector/user-agent cloudwatch-recorder
                                    (get (:headers request) "user-agent"))
            response (handler (assoc-in request [:session :user-agent] ua))]
        (assoc-in response [:session :user-agent] ua)))))

(defn wrap-token
  "Add a unique token identifier to each request for easy debugging."
  [handler]
  (fn [request]
    (let [request-token (str (UUID/randomUUID))
          tokenized-request (assoc request :token request-token)]
      (log/debug (format "\n Start: %s \n Time: %s \n Request: \n %s"
                         request-token (t/now) request))
      (let [response (handler tokenized-request)]
        (log/debug (format "\n End: %s \n Time: %s \n Response: \n %s"
                           request-token (t/now) response))
        response))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Main handler entry point
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn app
  [{:keys [config session-cache] :as options}]
  (-> all-routes
      wrap-vbucket
      wrap-detect-user-agent
      (wrap-ensure-session {:include-routes
                            [(GET ["/api/v1/promos/query/:code",
                                   :code promo-code-regex] [code] "ok")
                             (POST ["/api/v1/promos/calculation/:code",
                                    :code promo-code-regex] [code] "ok")
                             (POST ["/api/v1/promos/validation/:code",
                                    :code promo-code-regex] [code] "ok")
                             (GET "/api/v1/track" [] "ok")
                             (GET "/api/v1/track" [] "ok")
                             (GET "/api/v1/rco" [] "ok")]})
      (wrap-permacookie {:name "promotably" :request-key :shopper-id})
      (wrap-restful-format :formats [:json-kw :edn])
      jsonp/wrap-json-with-padding
      (session/wrap-session {:store session-cache
                             :cookie-name promotably-session-cookie-name})
      (wrap-record-new-session {:cookie-name promotably-session-cookie-name})
      (wrap-record-vbucket-assignment (GET "/api/v1/rco" [] "ok")
                                      (GET "/api/v1/track" [] "ok")
                                      (POST ["/api/v1/validation/:code"
                                             :code promo-code-regex]
                                            [code] "ok")
                                      (GET ["/api/v1/query/:code"
                                            :code promo-code-regex]
                                           [code] "ok")
                                      (POST ["/api/v1/calculation/:code"
                                             :code promo-code-regex]
                                            [code] "ok"))
      wrap-record-rco-events
      wrap-cookies
      wrap-request-logging
      wrap-keyword-params
      wrap-multipart-params
      wrap-params
      wrap-nested-params
      wrap-token
      wrap-save-the-raw-body
      wrap-argument-exception
      wrap-stacktrace
      wrap-gzip
      wrap-content-type
      wrap-cloudwatch))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; http-kit routes
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- ring-routes
  "Returns all routes, with CSRF protection where applicable."
  [config session-cache]
  (let [app-routes (app {:config config
                         :session-cache session-cache})
        csrf-routes (-> (compojure/routes
                         ;; All application routes here that you want
                         ;; to have csrf protection...

                         ;; FOR EXAMPLE:
                         ;; authorized-routes
                         ;; (GET "/"    [] homepage/render)
                         ;; (GET "/sessions" [] session/session)

                         (ring-anti-forgery/wrap-anti-forgery
                          {:read-token (fn [req] (-> req :params :csrf-token))})))]

    (-> (compojure/routes
         ;; These routes get NO csrf protection.
         app-routes
         ;; Serve static resources.
         ;; These routes get NO csrf protection.
         (compojure.route/resources "/")

         ;; Fall back to routes with csrf protection
         (ANY "*" [] csrf-routes)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Router [config logging session-cache cloudwatch]
  component/Lifecycle
  (start [component]
    (let [cloudwatch-recorder (:recorder cloudwatch)]
      (if (:stop! component)
        component
        (do
          (fetch-static cloudwatch-recorder
                        (-> config :kinesis :aws-credential-profile)
                        (-> config :dashboard :artifact-bucket)
                        (-> config :dashboard :index-filename)
                        cached-index)
          (fetch-static cloudwatch-recorder
                        (-> config :kinesis :aws-credential-profile)
                        (-> config :dashboard :artifact-bucket)
                        (-> config :dashboard :register-filename)
                        cached-register)
          (fetch-static cloudwatch-recorder
                        (-> config :kinesis :aws-credential-profile)
                        (-> config :dashboard :artifact-bucket)
                        (-> config :dashboard :login-filename)
                        cached-login)
          (assoc component :ring-routes (ring-routes config session-cache))))))
  (stop
   [component]
    component))
