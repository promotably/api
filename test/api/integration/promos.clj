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

  (defn- update-promo
    [promo-id value]
    (client/put (str "http://localhost:3000/v1/promos/" promo-id)
                {:body (json/write-str value)
                 :content-type :json
                 :accept :json
                 :throw-exceptions false}))

  (defn- show-promo
    [sid promo-id]
    (client/get (str "http://localhost:3000/v1/promos/" promo-id)
                {:query-params {:site-id sid}
                 :accept :json
                 :throw-exceptions false}))

  (defn- lookup-promo-by-code
    [code sid]
    (client/get (str "http://localhost:3000/v1/promos/query/" code)
                {:query-params {:site-id sid}
                 :content-type :json
                 :accept :json
                 :throw-exceptions false}))

  (fact-group :integration

              (facts "Promo Create"
                (let [new-promo {:site-id (str site-id)
                                 :code "TWENTYOFF"
                                 :description "You get 20% off. Bitches."
                                 :reward-amount 20.0
                                 :reward-type :percent
                                 :reward-tax :after-tax
                                 :reward-applied-to :cart
                                 :exceptions nil
                                 :conditions [{:type "dates"
                                               :start-date "2014-11-27T05:00:00Z"
                                               :end-date "2014-11-29T04:59:59Z"}]}
                      r (create-promo new-promo)]
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
                           :exceptions nil
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
               :exceptions
               :conditions)

              (facts "Promo Lookup Happy Path"
                (let [r (lookup-promos site-id)
                      b (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  b => (just [(contains {:active true
                                         :promo-id string?
                                         :code "EASTER"
                                         :conditions []
                                         :description "Easter Coupon"
                                         :seo-text "Best effing coupon evar"
                                         :exceptions nil
                                         :linked-products []
                                         :reward-amount 20.0
                                         :reward-applied-to "cart"
                                         :reward-tax "after-tax"
                                         :reward-type "percent"})
                              (contains {:promo-id string?
                                         :code "TWENTYOFF"
                                         :description "You get 20% off. Bitches."
                                         :reward-amount 20.0
                                         :reward-type "percent"
                                         :reward-tax "after-tax"
                                         :reward-applied-to "cart"
                                         :exceptions nil
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
                                                :code "EYECATCH"
                                                :reward-amount 10.0
                                                :reward-type :percent
                                                :reward-tax :after-tax
                                                :reward-applied-to :cart
                                                :exceptions nil
                                                :conditions []})

                      u (json/read-str (:body (lookup-promos site-id)) :key-fn keyword)]
                  (:status r) => 204
                  u => (just [(contains {:description "alsdkfjlaksdjf"
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
                                         :conditions [{:type "dates"
                                                            :start-date "2014-11-27T05:00:00Z"
                                                            :end-date "2014-11-29T04:59:59Z"}]
                                         :description "You get 20% off. Bitches."
                                         :exceptions nil
                                         :linked-products []
                                         :reward-amount 20.0
                                         :reward-applied-to "cart"
                                         :reward-tax "after-tax"
                                         :reward-type "percent"})])))

              (tabular
               (facts "Promo Update Missing Fields"
                 (let [b (json/read-str (:body (lookup-promos site-id)) :key-fn keyword)
                       promo-id (:promo-id (first b))
                       p (dissoc {:site-id (str site-id)
                                  :code "TWENTYOFF"
                                  :description "You get 20% off."
                                  :reward-amount 20.0
                                  :reward-type :percent
                                  :reward-tax :after-tax
                                  :reward-applied-to :cart
                                  :exceptions nil
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
               :exceptions
               :conditions)

              (facts "Show Promo Happy Path"
                (let [promo-id (:promo-id
                                (first
                                 (json/read-str
                                  (:body (lookup-promos site-id))
                                  :key-fn keyword)))
                      r (show-promo site-id promo-id)]
                  (:status r) => 200))

              (facts "Promo with Conditions Create & Show Roundtrip"
                (let [p {:site-id (str site-id)
                         :code "FALLSALE"
                         :description "Fall Sale"
                         :reward-amount 15.0
                         :reward-type :percent
                         :reward-tax :before-tax
                         :reward-applied-to :cart
                         :exceptions nil
                         :conditions [{:type :dates
                                       :start-date "2015-08-01T04:59:59Z"
                                       :end-date "2015-11-30T04:59:59Z"}]}
                      cr (create-promo p)
                      qr (json/read-str
                          (:body (lookup-promo-by-code "FALLSALE" site-id))
                          :key-fn keyword)
                      sr (json/read-str
                          (:body (show-promo site-id (:promo-id qr)))
                          :key-fn keyword)]
                  (:status cr) => 201
                  qr => (contains {:code "FALLSALE"
                                   :description "Fall Sale"
                                   :reward-amount 15.0
                                   :reward-type "percent"
                                   :reward-tax "before-tax"
                                   :reward-applied-to "cart"
                                   :conditions (just [(contains {:type "dates"
                                                                 :start-date "2015-08-01T04:59:59Z"
                                                                 :end-date "2015-11-30T04:59:59Z"})])})
                  sr => qr))))
