(ns api.routes
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes context GET POST PUT]]
            [compojure.route :refer [not-found]]
            [ring.util.response :refer [response content-type]]
            [ring.middleware.permacookie :refer [wrap-permacookie]]
            [api.cache :as cache]
            [api.events :as events]
            [api.controllers.users :refer [create-new-user! get-user update-user!
                                           lookup-user]]
            [api.controllers.promos :refer [create-new-promo! show-promo query-promo
                                            validate-promo calculate-promo]]
            [api.controllers.accounts :refer [create-new-account! update-account!]]
            [api.controllers.email-subscribers :refer [create-email-subscriber!]]))

(def js-content-type "text/javascript; charset=utf-8")
(def promo-code-regex #"[a-zA-Z0-9]{1,}")

(defroutes promo-routes
  (context "/promos" []
    (POST "/" [] create-new-promo!)
    (GET ["/:promo-id", :promo-id #"[0-9]+"] [promo-id] show-promo)
    (GET ["/query/:promo-code", :promo-code promo-code-regex]
        [promo-code] query-promo)
    (POST ["/validation/:promo-code", :promo-code promo-code-regex]
        [promo-code] validate-promo)
    (POST ["/calculation/:promo-code", :promo-code promo-code-regex]
          [promo-code] calculate-promo))
  (context ["/promos/:promo-id", :promo-id #"[0-9]+"] [promo-id]))

(defroutes api-routes
  (context "/v1" []
           (GET "/track" req #(if-let [res (events/record-event %)]
                                (-> (response "{status: 'success'}")
                                    (content-type js-content-type))
                                (throw (ex-info "Error recording event." {:reason "Cache insert failed."}))))
           (POST "/email-subscribers" [] create-email-subscriber!)
           (POST "/accounts" [] create-new-account!)
           (PUT "/accounts/:account-id" [] update-account!)
           (GET "/users" [] lookup-user)
           (GET "/users/:user-id" [] get-user)
           (POST "/users" [] create-new-user!)
           (PUT "/users/:user-id" [] update-user!)
           promo-routes))

(defroutes anonymous-routes
  (GET "/health-check" [] "<h1>I'm here</h1>")
  api-routes
  (not-found "<h1>4-oh-4</h1>"))

(defroutes all-routes
  (-> anonymous-routes
      (wrap-permacookie {:name "promotably"})))
