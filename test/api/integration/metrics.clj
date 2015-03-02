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
                     :throw-exceptions false})))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-or-truncate)
                                 (load-fixture-set fix/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (fact-group :integration
              (fact "Can route to controller.api.metrics.get-revenue"
                    (let [r (request-metrics "/metrics/revenue" fix/site-id "20150220" "20150223")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => {:number-of-orders 3,
                            :discount 7.5,
                            :promotably-commission 1.5,
                            :less-commission-and-discounts 21.0,
                            :revenue 30.0}
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-lift"
                    (let [r (request-metrics "/metrics/lift" fix/site-id "20150220" "20150223")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => {:conversion {:daily {:promotably [3.1, 3.31, 3.42, 2.91, 3.09, 3.12, 3.23],
                                                 :control [2.95, 3.07, 3.11, 2.8, 2.85, 2.97, 3.03]},
                                         :average {:promotably 3.17,
                                                   :control 2.97}},
                            :avg-order-value {:daily {:promotably [37.53, 40.01, 42.11, 45.92, 36.71, 38.05, 40.32],
                                                      :control [33.54, 37.22, 37.45, 40.11, 31.34, 32.54, 37.34]},
                                              :average {:promotably 40.09,
                                                        :control 35.65}},
                            :abandoned-carts {:daily {:promotably [55.6, 54.34, 56.83, 58.1, 53.21, 50.3, 56.1],
                                                      :control [60.3, 59.21, 61.2, 63.85, 58.8, 56.2, 63.12]},
                                              :average {:promotably 54.93,
                                                        :control 60.38}},
                            :revenue-per-visit {:daily {:promotably [1.03, 1.10, 1.01, 1.05, 1.11, 1.15, 1.09],
                                                        :control [0.96, 0.97, 0.91, 0.99, 1.00, 0.92, 0.98]},
                                                :average {:promotably 1.08,
                                                          :control 0.96}}}
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-promos"
                    (let [r (request-metrics "/metrics/promos" fix/site-id "20150220" "20150223")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => [{:id (str fix/promo-id-uno),
                             :redemptions 6,
                             :discount 7.5,
                             :revenue 30.0,
                             :code "C1",
                             :avg-revenue 5.0},
                            {:id (str fix/promo-id-duo),
                             :redemptions 20,
                             :discount 20.0,
                             :revenue 200.0,
                             :code "C2",
                             :avg-revenue 10.0}]
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-rco"
                    (let [r (request-metrics "/metrics/rco" fix/site-id "20150220" "20150223")
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => [{:id (str fix/offer-id-uno),
                             :code "C1",
                             :visits 300,
                             :qualified 30,
                             :offered 15,
                             :orders 9,
                             :redemptions 6,
                             :redemption-rate 40.00,
                             :conversion-rate 60.00,
                             :avg-items-in-cart 3,
                             :avg-revenue 28.0,
                             :revenue 170.0,
                             :avg-discount 4.0, ;;
                             :discount 43.5},
                            {:id (str fix/offer-id-duo),
                             :code "C2",
                             :visits 200,
                             :qualified 20,
                             :offered 10,
                             :orders 6,
                             :redemptions 4,
                             :redemption-rate 40.00,
                             :conversion-rate 60.00,
                             :avg-items-in-cart 3,
                             :avg-revenue 50.0,
                             :revenue 200.0,
                             :avg-discount 6.0, ;;
                             :discount 41.0}]
                      (:status r) => 200))))

