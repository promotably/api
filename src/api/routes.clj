(ns api.routes
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes context GET POST]]
            [cemerick.friend :as friend]
            [ring.util.response :refer [response content-type]]
            [ring.middleware.permacookie :refer [wrap-permacookie]]
            [api.cache :as cache]
            [api.events :as events]
            [api.controllers.users :refer [create-new-user! authenticate-user]]
            [api.controllers.promos :refer [create-new-promo! show-promo query-promo
                                            validate-promo calculate-promo]]
            [api.controllers.accounts :refer [create-new-account!]]
            [api.controllers.rules :refer [create-new-rule!
                                           show-rule]]
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
  (context ["/promos/:promo-id", :promo-id #"[0-9]+"] [promo-id]
    (context "/rules" []
      (POST "/" [promo-id] create-new-rule!)
      (GET ["/:rule-id", :rule-id #"[0-9]+"] [promo-id rule-id] show-rule))))

(defroutes api-routes
  (context "/v1" []
           (GET "/track" req #(if-let [res (events/record-event %)]
                                (-> (response "{status: 'success'}")
                                    (content-type js-content-type))
                                (throw (ex-info "Error recording event." {:reason "Cache insert failed."}))))
           (POST "/email-subscribers" [] create-email-subscriber!)
           promo-routes))

(defroutes anonymous-routes
  (GET "/health-check" [] "<h1>I'm here</h1>")
  (POST "/users" [] create-new-user!)

  api-routes)

(defroutes authenticated-routes
  (GET "/auth-required" req (friend/authenticated (format "<h1>Authenticated %s</h1>"
                                                          (friend/current-authentication))))
  (POST "/accounts" [] create-new-account!))

(defroutes all-routes
  (-> anonymous-routes
      (wrap-permacookie {:name "promotably"}))
  (-> authenticated-routes
      (friend/wrap-authorize #{::api})))
