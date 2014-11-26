(ns api.integration.promos
  (:require
   [api.fixtures.basic :as base]
   [api.integration.helper :refer :all]
   [api.route :as route]
   [api.core :as core]
   [api.models.site]
   [api.models.promo :as promo]
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

  (def site (api.models.site/find-by-name "site-1"))
  (def site-id (:site-id site))
  (defn- create-promo
    [new-promo]
    (client/post "http://localhost:3000/v1/promos"
                 {:body (json/write-str new-promo)
                  :content-type :json
                  :accept :json
                  :throw-exceptions false}))

  (fact-group :integration

              (facts "Promo Create"
                (let [new-promo {:site-id (str site-id)
                                 :name "Twenty Off"
                                 :code "TWENTYOFF"
                                 :description "You get 20% off. Bitches."
                                 :reward-amount 20.0
                                 :reward-type :percent
                                 :reward-tax :after-tax
                                 :reward-applied-to :cart
                                 :exceptions nil
                                 :conditions []}
                      r (create-promo new-promo)]
                  (:status r) => 201))

              (tabular
               (facts "Promo Create Missing Required Fields"
                 (let [bc (promo/count-by-site site-id)
                       np {:site-id (str site-id)
                           :name "Twenty Off"
                           :code "TWENTYOFF"
                           :description "You get 20% off. Bitches."
                           :reward-amount 20.0
                           :reward-type :percent
                           :reward-tax :after-tax
                           :reward-applied-to :cart
                           :exceptions nil
                           :conditions []}
                       r (create-promo (dissoc np ?remove))
                       ac (promo/count-by-site site-id)]
                   (:status r) => 400
                   ac => bc))
               ?remove
               :site-id
               :name
               :code
               :description
               :reward-amount
               :reward-type
               :reward-tax
               :reward-applied-to
               :exceptions
               :conditions)

              (facts "Promo Lookup Happy Path"
                (let [r (client/get "http://localhost:3000/v1/promos"
                                    {:query-params {:site-id site-id}
                                     :content-type :json
                                     :accept :json
                                     :throw-exceptions false})
                      b (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  b => (just [(contains {:name "Twenty Off"
                                         :code "TWENTYOFF"
                                         :description "You get 20% off. Bitches."
                                         :reward-amount 20.0
                                         :reward-type "percent"
                                         :reward-tax "after-tax"
                                         :reward-applied-to "cart"
                                         :exceptions nil
                                         :conditions []
                                         :promo-id string?})])))))
