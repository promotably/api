(ns api.routes
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes context GET POST PUT DELETE]]
            [compojure.route :refer [not-found]]
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
                                            lookup-offers]]
            [api.controllers.accounts :refer [lookup-account create-new-account!
                                              update-account!]]
            [api.controllers.email-subscribers :refer [create-email-subscriber!]]))

(def js-content-type "text/javascript; charset=utf-8")
(def promo-code-regex #"[a-zA-Z0-9-]{1,}")
(def offer-code-regex #"[a-zA-Z0-9-]{1,}")

(defroutes promo-routes
  (context "/promos" []
           (POST "/" [] create-new-promo!)
           (GET "/" [] lookup-promos)
           (DELETE ["/:promo-id", :promo-id promo-code-regex] [promo-id] delete-promo!)
           (GET ["/:promo-id", :promo-id promo-code-regex] [promo-id] show-promo)
           (PUT ["/:promo-id", :promo-id promo-code-regex] [promo-id] update-promo!)
           (GET ["/query/:promo-code", :promo-code promo-code-regex]
                [promo-code] query-promo)
           (POST ["/validation/:promo-code", :promo-code promo-code-regex]
                 [promo-code] validate-promo)
           (POST ["/calculation/:promo-code", :promo-code promo-code-regex]
                 [promo-code] calculate-promo)))

(defroutes offer-routes
  (context "/offers" []
           (POST "/" [] create-new-offer!)
           (GET "/" [] lookup-offers)
           (DELETE ["/:offer-id", :offer-id offer-code-regex] [offer-id] delete-offer!)
           (GET ["/:offer-id", :offer-id offer-code-regex] [offer-id] show-offer)
           (PUT ["/:offer-id", :offer-id offer-code-regex] [offer-id] update-offer!)))

(defroutes api-routes
  (context "/v1" []
           (GET "/track" req #(if-let [res (events/record-event %)]
                                (-> (response "{status: 'success'}")
                                    (content-type js-content-type))
                                (throw (ex-info "Error recording event."
                                                {:reason "Cache insert failed."}))))
           (POST "/email-subscribers" [] create-email-subscriber!)
           (GET "/accounts" [] lookup-account)
           (POST "/accounts" [] create-new-account!)
           (PUT "/accounts/:account-id" [] update-account!)
           (GET "/users" [] lookup-user)
           (GET "/users/:user-id" [] get-user)
           (POST "/users" [] create-new-user!)
           (PUT "/users/:user-id" [] update-user!)
           offer-routes
           promo-routes))

(defroutes anonymous-routes
  (GET "/health-check" [] "<h1>I'm here</h1>")
  api-routes
  (not-found "<h1>4-oh-4</h1>"))

(defroutes all-routes
  (-> anonymous-routes
      (wrap-permacookie {:name "promotably"})))
