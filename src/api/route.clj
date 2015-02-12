(ns api.route
  (:import [java.io ByteArrayInputStream]
           [java.util UUID])
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clojure.core.match :as match :refer (match)]
            [clojure.repl :refer [pst]]
            [clojure.pprint :refer [pprint]]
            [compojure.core :refer [routes GET PUT HEAD POST DELETE ANY context defroutes]
             :as compojure]
            [compojure.route :refer [not-found]]
            [compojure.handler :as handler]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.session :as session]
            [ring.middleware.jsonp :as jsonp]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.util.response :refer [response content-type]]
            [ring.middleware.permacookie :refer [wrap-permacookie]]
            [ring.middleware.anti-forgery :as ring-anti-forgery
             :refer [wrap-anti-forgery]]
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
            [api.controllers.metrics :refer [get-revenue get-lift get-promos get-rco]]
            [api.cloudwatch :as cw]
            [api.lib.detector :as detector]
            [api.system :refer [current-system]]
            [api.sorting-hat :refer [wrap-sorting-hat wrap-record-bucket-assignment]]
            [clj-time.core :refer [before? after? now] :as t]
            [clj-time.coerce :as t-coerce]
            [amazonica.aws.s3]
            [amazonica.aws.s3transfer]
            [slingshot.slingshot :refer [try+]]))

(defonce cached-index (atom {:cached-at nil :index nil}))
(def promotably-session-cookie-name "promotably-session")

;;;;;;;;;;;;;;;;;;;
;;
;; Routes
;;
;;;;;;;;;;;;;;;;;;;

(defn- get-api-secret
  []
  (get-in current-system [:config :auth-token-config :api :api-secret]))

(def promo-code-regex #"[a-zA-Z0-9-]{1,}")
(def offer-code-regex #"[a-zA-Z0-9-]{1,}")

(defroutes promo-routes
  (context "/promos" []
           (POST ["/validation/:code", :code promo-code-regex]
                 [code] validate-promo)
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
           (GET "/realtime-conversion-offers" req (fn [r] (get-available-offers (:kinesis current-system) r)))
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
           (PUT ["/:promo-id", :promo-id promo-code-regex] [promo-id] update-promo!)
           (GET ["/query/:code", :code promo-code-regex] [code] query-promo)))

(defroutes metrics-secure-routes
           (context "/:site-id/metrics" []
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

(defn- fetch-index
  [config]
  (let [bucket (-> config :dashboard :artifact-bucket)
        filename (-> config :dashboard :index-filename)]
    (try
      (cw/put-metric "index-fetch" {:config config})
      (let [resp (amazonica.aws.s3/get-object bucket filename)
            content (slurp (:object-content resp))]
        (reset! cached-index {:index content :cached-at (now)}))
      (catch Throwable t
        (cw/put-metric "index-missing" {:config config})
        (log/logf :error "Can't fetch index file '%s'." filename)))))

(defn serve-cached-index
  [req]
  ;; if it's old, refresh it, but still return current copy
  (let [expires (t/plus (now) (t/minutes 5))]
    (if (or (nil? (:index @cached-index))
            (after? (:cached-at @cached-index) expires))
      (future (fetch-index (:artifact-bucket current-system)
                           (:index-filename current-system)))))
  (cw/put-metric "index-serve")
  (:index @cached-index))

(defn serve-404-page
  [req]
  {:status 404 :body "<h1>Not Found</h1>"})

(defroutes all-routes
  (GET "/health-check" [] "<h1>I'm here</h1>")
  api-routes
  (GET "/" [] serve-cached-index)
  (auth/wrap-authorized secure-routes get-api-secret)
  (GET "*" [] (not-found serve-404-page)))

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

(defn wrap-request-logging [handler]
  (fn [{:keys [request-method uri] :as req}]
    (let [start  (System/currentTimeMillis)
          resp   (handler req)
          finish (System/currentTimeMillis)
          total  (- finish start)]
      (log/info (format "%-6s %-4d %s (%dms)"
                        request-method
                        (:status resp)
                        uri
                        total))
      resp)))

(defn wrap-exceptions
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch clojure.lang.ExceptionInfo ex
        (cw/put-metric "exception")
        (log/logf :error (println (ex-data ex)))
        (when-let [exdata (ex-data ex)]
          (assoc-in (assoc-in (:response exdata) [:body]
                              (pprint (:error exdata)))
                    [:headers "X-Error"] (.getMessage ex)))))))

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
  (let [data {:created-at (t-coerce/to-string (t/now))
              :shopper-id (:shopper-id request)
              :site-shopper-id ssid
              :request-headers (:headers request)
              :session-id nil}
        data (if sid (assoc data :site-id sid) data)]
    (-> response
        (assoc-in [:session :initial-request-headers]
                  (:headers request))
        (assoc :new-session-data data))))

(defn wrap-record-new-session
  "When a new session is started, record relevant data to kinesis."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if-let [k-data (:new-session-data response)]
        (let [set-cookies (get (:headers response) "Set-Cookie")
              set-cookies (reduce
                           #(let [parts (clojure.string/split %2 #";")
                                  [cookie-name cookie-val] (clojure.string/split (first parts) #"=")]
                              (assoc %1 cookie-name cookie-val))
                           {}
                           set-cookies)
              session-id (get set-cookies promotably-session-cookie-name)
              k-data* (-> (assoc k-data :session-id session-id)
                          (assoc :event-format-version "1")
                          (assoc :event-name "session-start"))]
          (kinesis/record-event! (:kinesis current-system) "session-start" k-data*)
          (assoc response :session/key session-id))
        response))))

(defn wrap-ensure-session
  "Ensure that all relevant data is in the response session map and
  thence recorded to redis."
  [handler]
  (fn [request]
    (let [sid (or
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
          response (cond-> response
                           sid (update-in [:session :site-id] (constantly sid))
                           ssid (update-in [:session :site-shopper-id] (constantly ssid))
                           true (update-in [:session :last-request-at] (constantly (t-coerce/to-string (t/now))))
                           true (update-in [:session :expires] (constantly expires))
                           true (update-in [:session :shopper-id] (constantly (:shopper-id request))))]
      (if (empty? (:session request))
        (mark-new-session response request sid ssid)
        response))))

(defn wrap-detect-user-agent
  "Add a :user-agent key to the session map indicating the requestor's device type."
  [handler]
  (fn [request]
    (if (-> request :session :user-agent)
      (handler request)
      (let [ua (detector/user-agent (get (:headers request) "user-agent"))
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
      wrap-sorting-hat
      (wrap-permacookie {:name "promotably" :request-key :shopper-id})
      wrap-detect-user-agent
      wrap-ensure-session
      (wrap-restful-format :formats [:json-kw :edn])
      jsonp/wrap-json-with-padding
      (session/wrap-session {:store session-cache
                             :cookie-name promotably-session-cookie-name})
      wrap-record-new-session
      wrap-record-bucket-assignment
      wrap-record-rco-events
      wrap-cookies
      wrap-keyword-params
      wrap-multipart-params
      wrap-params
      wrap-nested-params
      wrap-token
      wrap-save-the-raw-body
      ;; wrap-exceptions
      wrap-argument-exception
      wrap-stacktrace
      (wrap-if #((:env config) #{:dev :test :integration})
               wrap-request-logging)
      wrap-gzip
      wrap-content-type))


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

(defrecord Router [config logging session-cache]
  component/Lifecycle
  (start
   [component]
   (if (:stop! component)
     component
     (do
       (fetch-index config)
       (assoc component :ring-routes (ring-routes config session-cache)))))
  (stop
   [component]
    component))
