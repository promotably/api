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

  (defn- lookup-promos
    [sid]
    (client/get "http://localhost:3000/v1/promos"
                {:query-params {:site-id sid}
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
                (let [r (lookup-promos site-id)
                      b (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  b => (just [(contains {:active true
                                         :code "EASTER"
                                         :conditions []
                                         :description "This is a description"
                                         :exceptions nil
                                         :linked-products []
                                         :name "Easter Coupon"
                                         :reward-amount 20.0
                                         :reward-applied-to "cart"
                                         :reward-tax "after-tax"
                                         :reward-type "percent"})
                              (contains {:name "Twenty Off"
                                         :code "TWENTYOFF"
                                         :description "You get 20% off. Bitches."
                                         :reward-amount 20.0
                                         :reward-type "percent"
                                         :reward-tax "after-tax"
                                         :reward-applied-to "cart"
                                         :exceptions nil
                                         :conditions []
                                         :promo-id string?})])))

              (facts "Promo Update Happy Path"
                (let [b (json/read-str (:body (lookup-promos site-id)) :key-fn keyword)
                      promo-id (:promo-id (first b))
                      r (client/put (str "http://localhost:3000/v1/promos/" promo-id)
                                    {:body (json/write-str {:site-id (str site-id)
                                                            :name "Eye-catching name here"
                                                            :description "alsdkfjlaksdjf"
                                                            :code "EYECATCH"
                                                            :reward-amount 10.0
                                                            :reward-type :percent
                                                            :reward-tax :after-tax
                                                            :reward-applied-to :cart
                                                            :exceptions nil
                                                            :conditions []})
                                     :content-type :json
                                     :accept :json
                                     :throw-exceptions false})
                      u (json/read-str (:body (lookup-promos site-id)) :key-fn keyword)]
                  (:status r) => 204
                  u => (just [(contains {:name "Eye-catching name here"
                                         :description "alsdkfjlaksdjf"
                                         :code "EYECATCH"
                                         :reward-amount 10.0
                                         :reward-type "percent"
                                         :reward-tax "after-tax"
                                         :reward-applied-to "cart"
                                         :exceptions nil
                                         :conditions []
                                         :promo-id string?})
                              (contains {:active true
                                         :code "TWENTYOFF"
                                         :conditions []
                                         :description "You get 20% off. Bitches."
                                         :exceptions nil
                                         :linked-products []
                                         :name "Twenty Off"
                                         :reward-amount 20.0
                                         :reward-applied-to "cart"
                                         :reward-tax "after-tax"
                                         :reward-type "percent"})])))))
