(ns api.integration.metrics
  (:require
    [clojure.data.json :as json]
    [clj-http.client :as client]
    [midje.sweet :refer :all]
    [api.fixtures.metrics :as fix]
    [api.integration.helper :refer :all]
    [api.system :refer [current-system] :as system]
    [api.core :as core]))

(defn request-metrics [path site-id start end]
  (let [start_param (str "?start=" start)
        end_param (str "&end=" end)]
        (client/get (str "http://localhost:3000/api/v1/sites/" site-id path start_param end_param)
                    {:body nil
                     :headers {"Cookie" (build-auth-cookie-string)}
                     :content-type :json
                     :accept :json
                     :throw-exceptions true})))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-or-truncate)
                                 (load-fixture-set fix/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (fact-group :integration
              (fact "Can route to controller.api.metrics.get-additional-revenue"
                    (let [r (request-metrics "/metrics/additional-revenue" fix/site-id "20150220" "20150224")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => {:number-of-orders 3,
                            :discount 7.5,
                            :revenue 30.0,
                            :promotably-commission 3.0,
                            :less-commission-and-discount 19.5}
                      (:status r) => 200))


              (fact "Can route to controller.api.metrics.get-revenue"
                    (let [r (request-metrics "/metrics/revenue" fix/site-id "20150220" "20150225")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => {:avg-order-revenue {:average 8.0,
                                                :daily [0 0 30.0 0 10.0]},
                            :discount {:average 2.0,
                                       :daily [0 0 7.5 0 2.5]},
                            :revenue-per-visit {:average 8.0,
                                                :daily [0 0 30.0 0 10.0]},
                            :total-revenue {:average 8.0,
                                            :daily [0 0 30.0 0 10.0]}}
                      (:status r) => 200))

              (fact "/metrics/*/revenue will produce empty results for queries with no data"
                    (let [r (request-metrics "/metrics/revenue" fix/site-id "20150228" "20150306")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => {:total-revenue {
                              :daily [0 0 0 0 0 0]
                              :average 0.0},
                            :discount {
                              :daily [0 0 0 0 0 0]
                              :average 0.0},
                            :avg-order-revenue {
                              :daily [0 0 0 0 0 0]
                              :average 0.0 },
                            :revenue-per-visit {
                              :daily [0 0 0 0 0 0]
                              :average 0.0}}
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-lift"
                    (let [r (request-metrics "/metrics/lift" fix/site-id "20150220" "20150224")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => {:avg-order-revenue {:average {:exc 1.25,
                                                          :inc 1.25},
                                                :daily {:exc [0 0 4.0 1.0],
                                                        :inc [0 0 4.0 1.0]}},
                            :order-count {:average {:exc 1.25,
                                                    :inc 1.25},
                                          :daily {:exc [0 0 4 1],
                                                  :inc [0 0 4 1]}},
                            :revenue-per-visit {:average {:exc 1.25,
                                                          :inc 1.25},
                                                :daily {:exc [0 0 4.0 1.0],
                                                        :inc [0 0 4.0 1.0]}},
                            :total-revenue {:average {:exc 1.25,
                                                      :inc 1.25},
                                            :daily {:exc [0 0 4.0 1.0],
                                                    :inc [0 0 4.0 1.0]}}}
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-promos"
                    (let [r (request-metrics "/metrics/promos" fix/site-id "20150220" "20150223")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => [{:id (str fix/promo-id-duo),
                             :deleted true,
                             :redemptions 20,
                             :discount 20.0,
                             :revenue 200.0,
                             :code "C2",
                             :revenue-per-order 10.0},
                            {:id (str fix/promo-id-uno),
                             :deleted true,
                             :redemptions 6,
                             :discount 7.5,
                             :revenue 30.0,
                             :code "C1",
                             :revenue-per-order 5.0}]
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-rco"
                    (let [r (request-metrics "/metrics/rco" fix/site-id "20150220" "20150223")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => [{:id (str fix/offer-id-duo),
                             :deleted true,
                             :code "C2",
                             :visits 200,
                             :qualified 20,
                             :offered 10,
                             :orders 6,
                             :redeemed 4,
                             :redemption-rate 40.00,
                             :conversion-rate 3.0,
                             :avg-items-in-cart 3,
                             :avg-revenue 33.0,
                             :revenue 200.0,
                             :avg-discount 6.0,
                             :discount 41.0},
                            {:id (str fix/offer-id-uno),
                             :deleted true,
                             :code "C1",
                             :visits 300,
                             :qualified 30,
                             :offered 15,
                             :orders 9,
                             :redeemed 6,
                             :redemption-rate 40.00,
                             :conversion-rate 3.0,
                             :avg-items-in-cart 3,
                             :avg-revenue 18.0,
                             :revenue 170.0,
                             :avg-discount 4.0,
                             :discount 43.5}]
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-insights"
                    (let [r (request-metrics "/metrics/insights" fix/site-id "20150220" "20150223")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => {:total-discount 0.0
                            :visits 9
                            :average-session-length 3.0
                            :abandon-count 9
                            :engagements 9
                            :cart-adds 0
                            :total-revenue 3.0
                            :checkouts 9
                            :abandon-value 9
                            :revenue-per-order 0.0
                            :order-count 9
                            :ssids 9
                            :product-views 9
                            :average-items-per-order 3.0
                            :total-items 9}
                      (:status r) => 200))))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-or-truncate)))
                     (after :contents
                            (comment migrate-down))]

                    (fact-group :integration
                                (fact "Can serve empty metrics"
                                      (let [r (request-metrics "/metrics/insights" fix/site-id "20150220" "20150223")
                                            b (json/read-str (:body r) :key-fn keyword)]
                                        b => {:total-discount 0.0
                                              :visits 0
                                              :average-session-length 0.0
                                              :abandon-count 0
                                              :engagements 0
                                              :cart-adds 0
                                              :total-revenue 0.0
                                              :checkouts 0
                                              :abandon-value 0
                                              :revenue-per-order 0.0
                                              :order-count 0
                                              :ssids 0
                                              :product-views 0
                                              :average-items-per-order 0.0
                                              :total-items 0}
                                        (:status r) => 200))))

