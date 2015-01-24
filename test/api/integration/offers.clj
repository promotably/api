(ns api.integration.offers
  (:require
   [api.fixtures.offers :as offers-fixture]
   [api.integration.helper :refer :all]
   [api.route :as route]
   [api.system :as system]
   [api.core :as core]
   [api.models.site]
   [api.models.promo :as promo]
   [clj-http.client :as client]
   [cheshire.core :refer :all]
   [clojure.data.json :as json]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set offers-fixture/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (def site (api.models.site/find-by-name "site-1"))
  (def site-id (:site-id site))
  (def promos (api.models.promo/find-by-site-uuid site-id false))
  (defn- create-offer
    [new-offer]
    (client/post "http://localhost:3000/api/v1/offers"
                 {:body (json/write-str new-offer)
                  :content-type :json
                  :accept :json
                  :throw-exceptions false}))
  (defn- update-offer
    [offer-id offer]
    (client/put (str "http://localhost:3000/api/v1/offers/" offer-id)
                {:body (json/write-str offer)
                 :content-type :json
                 :accept :json
                 :throw-exceptions false}))
  (defn- get-rcos
    [site-id]
    )

  (fact-group :integration

              (facts "Offer Create"
                (let [new-offer {:site-id (str site-id)
                                 :name "New Visitor Offer"
                                 :code "NEW-VISITOR"
                                 :display-text "display text"
                                 :reward {:promo-id (-> promos first :uuid str)
                                          :type :dynamic-promo
                                          :expiry-in-minutes 10}
                                 :presentation {:type :lightbox
                                                :page :any
                                                :display-text "presentation text"}
                                 :conditions [{:type "dates"
                                               :start-date "2014-11-27T05:00:00Z"
                                               :end-date "2014-11-29T04:59:59Z"}
                                              {:type :minutes-since-last-offer
                                               :minutes-since-last-offer 10}]}
                      r (create-offer new-offer)]
                  (:status r) => 201))

              (facts "List Offers"
                (let [url (str "http://localhost:3000/api/v1/offers/?site-id="
                               (:site-id site))
                      r (client/get url)
                      listed (parse-string (:body r) keyword)]
                  listed => (just [(contains
                                    {:display-text "display text"
                                     :name "New Visitor Offer"
                                     :presentation {:display-text "presentation text"
                                                     :page "any"
                                                     :type "lightbox"}
                                     :active true
                                     :reward {:type "dynamic-promo"
                                              :promo-id (-> promos first :uuid str)
                                              :expiry-in-minutes 10}
                                     :code "NEW-VISITOR"
                                     :conditions (contains
                                                  [{:type "dates"
                                                   :start-date "2014-11-27T05:00:00Z"
                                                   :end-date "2014-11-29T04:59:59Z"}
                                                  {:type "minutes-since-last-offer"
                                                   :minutes-since-last-offer 10}]
                                                  :in-any-order)})
                                   (contains
                                    {:display-text "display text"
                                     :name "Easter Offer"
                                     :presentation {:display-text "presentation text"
                                                    :page "any"
                                                    :type "lightbox"}
                                     :active true
                                     :reward {:type "dynamic-promo"
                                              :promo-id (-> promos first :uuid str)
                                              :expiry-in-minutes 20}
                                     :code "E1"
                                     :conditions []})])
                  (:status r) => 200))

              (facts "Offer Update"
                (let [url (str "http://localhost:3000/api/v1/offers/?site-id="
                               (:site-id site))
                      r (client/get url)
                      listed (parse-string (:body r) keyword)
                      updated-offer {:site-id (str site-id)
                                     :offer-id (-> listed first :offer-id)
                                     :name "Old Visitor Offer"
                                     :code "OLD-VISITOR"
                                     :display-text "display text again"
                                     :reward {:promo-id (-> promos first :uuid str)
                                              :type :dynamic-promo
                                              :expiry-in-minutes 10}
                                     :presentation {:type :fixed-div
                                                    :page :search-results
                                                    :display-text "foo"}
                                     :conditions [{:type :product-views
                                                   :product-views 3}]}
                      r1 (update-offer (-> listed first :offer-id) updated-offer)
                      r2 (client/get url)
                      listed (parse-string (:body r2) keyword)]
                  (:status r) => 200
                  listed => (just [(contains
                                    {:display-text "display text again"
                                     :name "Old Visitor Offer"
                                     :presentation {:display-text "foo"
                                                    :page "search-results"
                                                    :type "fixed-div"}
                                     :active true
                                     :reward {:type "dynamic-promo"
                                              :promo-id (-> promos first :uuid str)
                                              :expiry-in-minutes 10}
                                     :code "OLD-VISITOR"
                                     :conditions [{:product-views 3
                                                   :type "product-views"}]})
                                   (contains
                                    {:display-text "display text"
                                     :name "Easter Offer"
                                     :presentation {:display-text "presentation text"
                                                    :page "any"
                                                    :type "lightbox"}
                                     :active true
                                     :reward {:type "dynamic-promo"
                                              :promo-id (-> promos first :uuid str)
                                              :expiry-in-minutes 20}
                                     :code "E1"
                                     :conditions []})])))

              (facts "Offer with dates condition"
                )))
