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
            [api.controllers.users :refer [create-new-user! get-user update-user!
                                           lookup-social-user]]
            [api.controllers.promos :refer [create-new-promo! show-promo query-promo
                                            validate-promo calculate-promo
                                            update-promo! delete-promo!
                                            lookup-promos]]
            [api.controllers.offers :refer [create-new-offer! show-offer
                                            update-offer! delete-offer!
                                            lookup-offers get-available-offers]]
            [api.controllers.accounts :refer [lookup-account create-new-account!
                                              update-account!]]
            [api.controllers.email-subscribers :refer [create-email-subscriber!]]
            [api.cloudwatch :as cw]
            [api.system :refer [current-system]]
            [clj-time.core :refer [before? after? now] :as t]
            [clj-time.coerce :as t-coerce]
            [amazonica.aws.s3]
            [amazonica.aws.s3transfer]
            [slingshot.slingshot :refer [try+]]))

(defonce cached-index (atom {:cached-at nil :index nil}))

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
           (GET "/realtime-conversion-offers" [] get-available-offers)
           (POST "/login" req (fn [r]
                                (let [auth-config (get-in current-system [:config :auth-token-config])]
                                  (auth/authenticate r auth-config get-user))))
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

(defroutes secure-routes
  (context "/api/v1" []
           (GET "/accounts" [] lookup-account)
           (POST "/accounts" [] create-new-account!)
           (PUT "/accounts/:account-id" [] update-account!)
           (GET "/users/:user-id" [user-id] (get-user user-id))
           (POST "/users" [] create-new-user!)
           (PUT "/users/:user-id" [] update-user!)
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
  (auth/wrap-authorized secure-routes get-api-secret)
  (GET "*" [] serve-cached-index)
  (not-found serve-404-page))

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

(defn wrap-schema-check [handler]
  "Catch exceptions Schema throws when failing to validate passed parameters"
  (fn [req]
    (try+
      (handler req)
      (catch [:type :api.controllers.offers/argument-error]
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

(defn wrap-ensure-session
  ""
  [handler]
  (fn [request]
    (let [sid (or
               (-> request :form-params :site-id)
               (-> request :query-params :site-id)
               (-> request :multipart-params :site-id)
               (-> request :body-params :site-id)
               (-> request :params :site-id))
          s (-> current-system :config :session-length-in-seconds)
          expires (t-coerce/to-string (t/plus (t/now) (t/seconds s)))
          response (cond->
                    (handler request)
                    sid (update-in [:session :site-id] (constantly sid))
                    true (update-in [:session :expires] (constantly expires))
                    true (update-in [:session :shopper-id] (constantly (:shopper-id request))))]
      response)))

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
      wrap-ensure-session
      (wrap-permacookie {:name "promotably" :request-key :shopper-id})
      (wrap-restful-format :formats [:json-kw :edn])
      jsonp/wrap-json-with-padding
      (session/wrap-session {:store session-cache
                             :cookie-name "promotably-session"})
      wrap-cookies
      wrap-keyword-params
      wrap-multipart-params
      wrap-params
      wrap-nested-params
      wrap-token
      wrap-save-the-raw-body
      ;; wrap-exceptions
      wrap-schema-check
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
