(ns api.integration.offers
  (:require
   [api.fixtures.basic :as base]
   [api.integration.helper :refer :all]
   [api.route :as route]
   [api.core :as core]
   [api.models.site]
   [api.models.promo :as promo]
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
  (def promos (api.models.promo/find-by-site-uuid site-id))
  (defn- create-offer
    [new-offer]
    (client/post "http://localhost:3000/v1/offers"
                 {:body (json/write-str new-offer)
                  :content-type :json
                  :accept :json
                  :throw-exceptions false}))

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
                                 :conditions []}
                      r (create-offer new-offer)]
                  (:status r) => 201))))
