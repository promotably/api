(ns api.server
  (:require [clojure.tools.logging :as log]
            [clojure.repl :refer [pst]]
            [clojure.pprint :refer [pprint]]
            [compojure.core :refer [defroutes context GET POST PUT DELETE]]
            [compojure.route :refer [not-found]]
            [compojure.handler :as handler]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.session :as session]
            [ring.middleware.json :as json]
            [ring.middleware.jsonp :as jsonp]
            [ring.util.response :refer [response content-type]]
            [ring.middleware.permacookie :refer [wrap-permacookie]]
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
            [api.controllers.email-subscribers :refer [create-email-subscriber!]]))



(def ^:dynamic system nil)

;;;;;;;;;;;;;;;;;;;
;;
;; Routes
;;
;;;;;;;;;;;;;;;;;;;

(def promo-code-regex #"[a-zA-Z0-9-]{1,}")
(def offer-code-regex #"[a-zA-Z0-9-]{1,}")

(defroutes promo-routes
  (context "/promos" []
           (POST "/" [] create-new-promo!)
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
  (context "/v1" []
           (GET "/track" req events/record-event)
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

(defroutes anonymous-routes
  (GET "/health-check" [] "<h1>I'm here</h1>")
  api-routes
  (not-found "<h1>4-oh-4</h1>"))

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
        (println (ex-data ex))
        (when-let [exdata (ex-data ex)]
          (assoc-in (assoc-in (:response exdata) [:body] (pprint (:error exdata)))
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
;; Main ring handler entry point
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn app
  [options]
  (-> all-routes
      (json/wrap-json-body {:keywords? true})
      jsonp/wrap-json-with-padding
      handler/site
      wrap-params
      wrap-keyword-params
      (session/wrap-session {:cookie-name "promotably-session"})
      wrap-exceptions
      wrap-stacktrace
      wrap-request-logging
      wrap-content-type))
