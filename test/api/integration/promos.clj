(ns api.integration.promos
  (:require [api.config :as config]
            [api.fixtures.basic :as base]
            [api.integration.helper :refer :all]
            [api.models.promo :as promo]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [midje.sweet :refer :all]))


(background (before :contents (do (start-test-server)
                                  (load-fixture-set base/fixture-set))))

(let [site (api.models.site/find-by-name "site-1")
      site-id (:site-id site)]
  (defn- create-promo
    [new-promo]
    (client/post "http://localhost:3000/v1/promos"
                 {:body (json/write-str new-promo)
                  :headers {"Content-Type" "application/json"
                            "Accept" "application/json"}
                  :throw-exceptions false}))

  (fact "Promo Create Happy Path"
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
          response (create-promo new-promo)]
      (:status response) => 201))

  (tabular
   (fact "Promo Create Missing Required Fields"
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
   :conditions))

