(ns api.integration.promos
  (:require
   [api.fixtures.basic :as base]
   [api.integration.helper :refer :all]
   [api.lib.seal :refer [hmac-sha1 url-encode]]
   [api.route :as route]
   [api.system :as system]
   [api.core :as core]
   [api.models.site]
   [api.models.promo :as promo]
   [clojure.tools.logging :as log]
   [clj-http.client :as client]
   [clj-time.core :as t]
   [clj-time.format :as tf]
   [clojure.data.json :as json]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-or-truncate)
                                 (load-fixture-set base/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (def site (api.models.site/find-by-name "site-1"))
  (def site-id (:site-id site))

  (fact-group :integration

              (facts "Promo Create"
                (let [new-promo {:site-id (str site-id)
                                 :active true
                                 :code "TWENTYOFF"
                                 :description "You get 20% off. Bitches."
                                 :reward-amount 20.0
                                 :reward-type :percent
                                 :reward-tax :after-tax
                                 :reward-applied-to :cart
                                 :conditions [{:type "dates"
                                               :start-date "2014-11-27T05:00:00Z"
                                               :end-date "2014-11-29T04:59:59Z"}]}
                      r (create-promo new-promo)
                      b (json/read-str (:body r) :key-fn keyword)]
                  b => (contains {:site-id (str site-id)
                                  :code "TWENTYOFF"
                                  :conditions (just [(contains {:type "dates"})])})
                  (:status r) => 201))

              (tabular
               (facts "Promo Create Missing Required Fields"
                 (let [bc (promo/count-by-site site-id)
                       np {:site-id (str site-id)
                           :code "TWENTYOFF"
                           :description "You get 20% off. Bitches."
                           :reward-amount 20.0
                           :reward-type :percent
                           :reward-tax :after-tax
                           :reward-applied-to :cart
                           :conditions [{:end-date "2014-11-29T04:59:59Z"
                                         :start-date "2014-11-27T05:00:00Z"
                                         :type "dates"}]}
                       r (create-promo (dissoc np ?remove))
                       ac (promo/count-by-site site-id)]
                   (:status r) => 400
                   ac => bc))
               ?remove
               :site-id
               :code
               :description
               :reward-amount
               :reward-type
               :reward-tax
               :reward-applied-to
               :conditions)

              (facts "Promo Lookup Happy Path"
                (let [r (lookup-promos site-id)
                      b (json/read-str (:body r) :key-fn keyword)]
                  ;; (clojure.pprint/pprint b)
                  (:status r) => 200
                  b => (just [(contains {:active true
                                         :promo-id string?
                                         :code "EASTER"
                                         :description "Easter Coupon"
                                         :seo-text "Best effing coupon evar"
                                         :linked-products []
                                         :conditions [{:end-date "2014-11-28T23:59:59Z"
                                                       :start-date "2014-11-27T00:00:00Z"
                                                       :type "dates"}]
                                         :reward-amount 20.0
                                         :reward-applied-to "cart"
                                         :reward-tax "after-tax"
                                         :reward-type "percent"})
                              (contains {:promo-id string?
                                         :code "TWENTYOFF"
                                         :description "You get 20% off. Bitches."
                                         :linked-products []
                                         :reward-amount 20.0
                                         :reward-type "percent"
                                         :reward-tax "after-tax"
                                         :reward-applied-to "cart"
                                         :conditions [{:type "dates"
                                                       :start-date "2014-11-27T05:00:00Z"
                                                       :end-date "2014-11-29T04:59:59Z"}]})])))

              (facts "Promo Lookup Site Doesn't Exist"
                (let [bad-site-id (str (java.util.UUID/randomUUID))
                      r (lookup-promos bad-site-id)]
                  (:status r) => 404))

              (facts "Promo Update Happy Path"
                (let [b (json/read-str (:body (lookup-promos site-id)) :key-fn keyword)
                      promo-id (:promo-id (first b))
                      r (update-promo promo-id {:site-id (str site-id)
                                                :description "alsdkfjlaksdjf"
                                                :seo-text "duckah p duckah"
                                                :code "EYECATCH"
                                                :active false
                                                :reward-amount 10.0
                                                :reward-type :percent
                                                :reward-tax :after-tax
                                                :reward-applied-to :cart
                                                :conditions [{:type :no-sale-items}
                                                             {:type :not-category-ids
                                                              :not-category-ids ["1"]}
                                                             {:type :times
                                                              :end-time "24:00"
                                                              :start-time "00:00"}]})
                      lookup-body (:body (lookup-promos site-id))
                      u (json/read-str lookup-body :key-fn keyword)
                      filtered (filter #(= (:promo-id %) promo-id) u)
                      b (json/read-str (:body r) :key-fn keyword)]
                  b => (contains {:code "EYECATCH"
                                  :site-id (str site-id)
                                  :conditions vector?})
                  (:status r) => 200
                  (first filtered) => (contains
                                       {:description "alsdkfjlaksdjf",
                                        :reward-applied-to "cart",
                                        :seo-text "duckah p duckah",
                                        :reward-tax "after-tax",
                                        :reward-amount 10.0,
                                        :active false,
                                        :code "EYECATCH",
                                        :reward-type "percent",
                                        :promo-id string?})
                  (-> filtered first :conditions) => (contains
                                                      [{:type "no-sale-items"}
                                                       {:type "not-category-ids"
                                                        :not-category-ids ["1"]}
                                                       {:type "times"
                                                        :end-time "24:00"
                                                        :start-time "00:00"}])
                  (update-promo promo-id {:site-id (str site-id)
                                          :description "alsdkfjlaksdjf"
                                          :seo-text "duckah p duckah"
                                          :code "EYECATCH"
                                          :active true
                                          :reward-amount 10.0
                                          :reward-type :percent
                                          :reward-tax :after-tax
                                          :reward-applied-to :cart
                                          :conditions [{:type :no-sale-items}
                                                       {:type :not-category-ids
                                                        :not-category-ids ["1"]}
                                                       {:type :times
                                                        :end-time "24:00"
                                                        :start-time "00:00"}]})))

              (tabular
               (facts "Promo Update Missing Fields"
                 (let [b (json/read-str (:body (lookup-promos site-id)) :key-fn keyword)
                       promo-id (:promo-id (first b))
                       p (dissoc {:site-id (str site-id)
                                  :code "TWENTYOFF"
                                  :description "You get 20% off."
                                  :active false
                                  :reward-amount 20.0
                                  :reward-type :percent
                                  :reward-tax :after-tax
                                  :reward-applied-to :cart
                                  :conditions []}
                                 ?remove)
                       r (update-promo promo-id p)]
                   (:status r) => 400))
               ?remove
               :site-id
               :code
               :description
               :reward-amount
               :reward-type
               :reward-tax
               :reward-applied-to
               :conditions)

              (facts "Show Promo Happy Path"
                (let [promos (json/read-str
                              (:body (lookup-promos site-id))
                              :key-fn keyword)
                      promo-id (-> promos second :promo-id)
                      r (show-promo site-id promo-id)
                      promo (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  promos => (contains
                             #{(contains
                                {:description "You get 20% off. Bitches.",
                                 :reward-applied-to "cart",
                                 :reward-tax "after-tax",
                                 :reward-amount 20.0,
                                 :linked-products [],
                                 :active true,
                                 :code "TWENTYOFF",
                                 :conditions [{:start-date "2014-11-27T05:00:00Z",
                                              :type "dates",
                                               :end-date "2014-11-29T04:59:59Z"}]
                                 :reward-type "percent",
                                 :promo-id string?})
                               (contains
                                {:active true,
                                 :code "EYECATCH",
                                 :conditions [{:type "no-sale-items"}
                                              {:not-category-ids ["1"],
                                               :type "not-category-ids"}
                                              {:end-time "24:00"
                                               :start-time "00:00",
                                               :type "times"}],
                                 :created-at string?
                                 :description "alsdkfjlaksdjf",
                                 :linked-products [],
                                 :promo-id string?
                                 :reward-amount 10.0,
                                 :reward-applied-to "cart",
                                 :reward-tax "after-tax",
                                 :reward-type "percent",
                                 :seo-text "duckah p duckah",
                                 :updated-at string?})
                               }
                             :in-any-order)))))
