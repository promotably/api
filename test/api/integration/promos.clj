(ns api.integration.promos
  (:require
   [api.fixtures.basic :as base]
   [api.integration.helper :refer :all]
   [api.route :as route]
   [api.core :as core]
   [api.models.site]
   [clojure.tools.logging :as log]
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? route/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set base/fixture-set)))
                     (after :contents
                            (migrate-down))]

  (fact-group :integration

              (facts "Promo Create"
                (let [site (api.models.site/find-by-name "site-1")
                      site-id (:site-id site)
                      new-promo {:site-id (str site-id)
                                 :name "Twenty Off"
                                 :code "TWENTYOFF"
                                 :description "You get 20% off. Bitches."
                                 :reward-amount 20.0
                                 :reward-type :percent
                                 :reward-tax :after-tax
                                 :reward-applied-to :cart
                                 :exceptions nil
                                 :conditions []}
                      response (client/post "http://localhost:3000/v1/promos"
                                            {:body (json/write-str new-promo)
                                             :headers {"Content-Type" "application/json"
                                                       "Accept" "application/json"}
                                             :throw-exceptions false})]
                  (:status response) => 201))))
