(ns api.route
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
            [ring.util.response :refer [response content-type]]
            [ring.middleware.permacookie :refer [wrap-permacookie]]
            [ring.middleware.anti-forgery :as ring-anti-forgery
             :refer [wrap-anti-forgery]]
            [api.events :as events]
            [api.controllers.users :refer [create-new-user! get-user update-user!
                                           lookup-user]]
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
            [amazonica.aws.s3]
            [amazonica.aws.s3transfer]))

(defonce cached-index (atom {:cached-at nil :index nil}))

;;;;;;;;;;;;;;;;;;;
;;
;; Routes
;;
;;;;;;;;;;;;;;;;;;;

(def promo-code-regex #"[a-zA-Z0-9-]{1,}")
(def offer-code-regex #"[a-zA-Z0-9-]{1,}")

(defroutes promo-routes
  (context "/promos" []
           (POST "/" [] (fn [r] (create-new-promo! (merge
                                                    (:kinesis current-system)
                                                    (-> current-system :config :kinesis))
                                                   r)))
           (GET "/" [] lookup-promos)
           (DELETE ["/:promo-id", :promo-id promo-code-regex] [promo-id] delete-promo!)
           (GET ["/:promo-id", :promo-id promo-code-regex] [promo-id] show-promo)
           (PUT ["/:promo-id", :promo-id promo-code-regex] [promo-id] update-promo!)
           (GET ["/query/:code", :code promo-code-regex]
                [code] query-promo)
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
           (GET "/track" req (fn [r] (let [k (:kinesis current-system)]
                                       (events/record-event k r))))
           (POST "/email-subscribers" [] create-email-subscriber!)
           (GET "/accounts" [] lookup-account)
           (POST "/accounts" [] create-new-account!)
           (PUT "/accounts/:account-id" [] update-account!)
           (GET "/users" [] lookup-user)
           (GET "/users/:user-id" [] get-user)
           (POST "/users" [] create-new-user!)
           (PUT "/users/:user-id" [] update-user!)
           (GET "/realtime-conversion-offers" [] get-available-offers)
           offer-routes
           promo-routes))

(defn- fetch-index
  [config]
  (let [bucket (:artifact-bucket config)
        filename (:index-filename config)]
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

(defroutes anonymous-routes
  (GET "/health-check" [] "<h1>I'm here</h1>")
  api-routes
  (not-found serve-cached-index))

(defroutes all-routes
  (-> anonymous-routes
      (wrap-permacookie {:name "promotably"})))

;;;;;;;;;;;;;;;;;;
;;
;; Middleware
;;
;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Main handler entry point
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn app
  [options]
  (-> all-routes
      (wrap-restful-format :formats [:json-kw :edn])
      jsonp/wrap-json-with-padding
      handler/site
      wrap-params
      wrap-keyword-params
      (session/wrap-session {:store (:session-cache options)
                             :cookie-name "promotably-session"})
      wrap-exceptions
      wrap-stacktrace
      wrap-request-logging
      wrap-content-type))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; http-kit routes
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- ring-routes
  "Returns all routes, with CSRF protection where applicable."
  [session-cache]
  (let [app-routes (app {:session-cache session-cache})
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
       (assoc component :ring-routes (ring-routes session-cache)))))
  (stop
   [component]
    component))
