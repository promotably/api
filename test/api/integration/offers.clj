(ns api.integration.offers
  (:require
   [api.fixtures.offers :as offers-fixture]
   [api.fixtures.offers.html-css-theme :as offers-f-hct]
   [api.integration.helper :refer :all]
   [api.route :as route]
   [api.system :as system]
   [api.core :as core]
   [api.models.site]
   [api.models.promo :as promo]
   [api.models.offer :as offer]
   [clj-http.client :as client]
   [cheshire.core :refer :all]
   [korma.core :refer :all]
   [clojure.data.json :as json]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-or-truncate)
                                 (load-fixture-set offers-fixture/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (def site (api.models.site/find-by-name "site-1"))
  (def site-id (:site-id site))
  (def promos (api.models.promo/find-by-site-uuid site-id false))
  (defn- default-offer []
    {:site-id (str site-id)
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
                   :end-date "2014-11-29T04:59:59Z"}]
     :html "<html></html>"
     :css "body {}"
     :theme "theme"})

  (defn- create-offer
    [new-offer]
    (client/post "http://localhost:3000/api/v1/offers"
                 {:body (json/write-str new-offer)
                  :headers {"Cookie" (build-auth-cookie-string)}
                  :content-type :json
                  :accept :json
                  :throw-exceptions false}))
  (defn- update-offer
    [offer-id offer]
    (client/put (str "http://localhost:3000/api/v1/offers/" offer-id)
                {:body (json/write-str offer)
                 :headers {"Cookie" (build-auth-cookie-string)}
                 :content-type :json
                 :accept :json
                 :throw-exceptions false}))
  (defn- get-rcos
    [site-id site-shopper-id & {:keys [cookies] :as opts}]
    (client/get "http://localhost:3000/api/v1/rco"
                (assoc-in (merge {:throw-exceptions false
                                  :query-params {"site-id" (str site-id)
                                                 "site-shopper-id" (str site-shopper-id)}}
                                 opts)
                          [:query-params "xyzzy"]
                          1)))

  (fact-group :integration

              (facts "Offer Create"
                     (:status (create-offer (default-offer))) => 201)

              (facts "Offer Create Fails with Bad Param"
                     (let [r (create-offer (assoc-in (default-offer) [:reward :promo-id] "invalid-id"))]
                       (:status r) => 400
                       (.contains (:body r) ":error") => true
                       (.contains (:body r) ":promo-id") => true))

              (facts "Offer Create with no html param"
                     (let [r (create-offer (offers-f-hct/no-html-offer))]
                       (:status r) => 201))

              (facts "List Offers"
                (let [url (str "http://localhost:3000/api/v1/offers/?site-id="
                               (:site-id site))
                      r (client/get url {:headers {"cookie" (build-auth-cookie-string)}})
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
                                                   :end-date "2014-11-29T04:59:59Z"}]
                                                  :in-any-order)
                                     :html "<html></html>"
                                     :css "body {}"
                                     :theme "theme"})
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
                               (str site-id))
                      r (client/get url {:headers {"cookie" (build-auth-cookie-string)}})
                      listed (parse-string (:body r) keyword)
                      updated-offer {:site-id (str site-id)
                                     :offer-id (-> listed first :offer-id)
                                     :active false
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
                                                   :product-views 3}]
                                     :html "<html></html>"
                                     :css "body {}"
                                     :theme "theme"}
                      r1 (update-offer (-> listed first :offer-id) updated-offer)
                      r2 (client/get url {:headers {"cookie" (build-auth-cookie-string)}})
                      listed (parse-string (:body r2) keyword)]
                  (:status r) => 200
                  listed => (just [(contains
                                    {:display-text "display text again"
                                     :name "Old Visitor Offer"
                                     :presentation {:display-text "foo"
                                                    :page "search-results"
                                                    :type "fixed-div"}
                                     :active false
                                     :reward {:type "dynamic-promo"
                                              :promo-id (-> promos first :uuid str)
                                              :expiry-in-minutes 10}
                                     :code "OLD-VISITOR"
                                     :conditions [{:product-views 3
                                                   :type "product-views"}]
                                     :html "<html></html>"
                                     :css "body {}"
                                     :theme "theme"})
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
                                     :conditions []
                                     :html "<html></html>"
                                     :css "body {}"
                                     :theme "theme"})])))

              (facts "Offer with dates condition"

                ;; Tests that only the offer with valid dates is
                ;; returned from the RCO call. There's also an offer
                ;; in the db for the same site that has invalid dates,
                ;; so it should not get returned here.

                (let [r (get-rcos offers-fixture/site-2-id
                                  (str (java.util.UUID/randomUUID)))
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => (contains {:code "EASTER PROMO FOR SITE 2"})
                  pr => (contains {:code "EASTER PROMO FOR SITE 2"
                                   :active true
                                   :promo {:conditions []}
                                   :presentation {:display-text nil
                                                  :page "product-detail"
                                                  :type "lightbox"}})))

              (facts "Offer with number of cart adds condition"
                (let [r (get-rcos offers-fixture/site-3-id
                                  offers-fixture/site-shopper-id)
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => (contains {:code "SITE 3 PROMO"})))

              (facts "Offer with min orders condition"
                (let [r (get-rcos (java.util.UUID/randomUUID)
                                  (java.util.UUID/randomUUID))
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => nil))

              (facts "Offer with min orders condition"
                (let [r (get-rcos offers-fixture/minorder-site-id
                                  offers-fixture/minorder-site-shopper-id)
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => (contains {:code "EASTER PROMO FOR SITE 4"})))

              (facts "Offer with max orders condition"
                (let [r (get-rcos offers-fixture/maxorder-site-id
                                  offers-fixture/minorder-site-shopper-id)
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => (contains {:code "EASTER PROMO FOR SITE 4"})))

              (facts "Offer with product-views condition"
                (let [r (get-rcos offers-fixture/site-4-id
                                  offers-fixture/site-shopper-2-id)
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => (contains {:code "EASTER PROMO FOR SITE 4"})))

              (facts "Don't make a second offer"
                (let [r (get-rcos offers-fixture/second-offer-site-id
                                  offers-fixture/site-shopper-2-id)
                      pr (json/read-str (:body r) :key-fn keyword)
                      r2 (get-rcos offers-fixture/second-offer-site-id
                                   offers-fixture/site-shopper-2-id
                                   :cookies (:cookies r))
                      pr2 (json/read-str (:body r2) :key-fn keyword)]
                  (:status r) => 200
                  pr => (contains {:code "EASTER PROMO FOR SITE SECOND OFFER"})
                  (:status r2) => 200
                  pr2 => nil))

              (facts "Offer with days-since-last-offer condition NOT offered as expected"
                (let [r (get-rcos offers-fixture/last-offer-site-id
                                  offers-fixture/last-offer-site-shopper-id-1)
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => nil))

              (facts "Offer with days-since-last-offer condition offered as expected"
                (let [r (get-rcos offers-fixture/last-offer-site-id
                                  offers-fixture/last-offer-site-shopper-id-2)
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => (contains {:code "EASTER PROMO FOR SITE LAST OFFER"})))

              (facts "Dynamic offer"
                (let [r (get-rcos offers-fixture/dynamic-site-id
                                  offers-fixture/dynamic-site-shopper-id)
                      pr (json/read-str (:body r) :key-fn keyword)]
                  (:status r) => 200
                  pr => (contains {:expires string?
                                   :promo {:conditions []},
                                   :presentation {:display-text nil,
                                                  :page "product-detail",
                                                  :type "lightbox"},
                                   :is-limited-time true,
                                   :code string?,
                                   :active true})))))
