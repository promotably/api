(ns api.integration.metrics
  (:require
    [clojure.data.json :as json]
    [clj-http.client :as client]
    [midje.sweet :refer :all]
    [api.fixtures.event-data :as fix]
    [api.integration.helper :refer :all]
    [api.system :refer [current-system] :as system]
    [api.core :as core]))

(defn request-metrics [path site-id]
  (client/get (str "http://localhost:3000/api/v1/sites/" site-id path)
              {:body nil
               :headers {"Cookie" (build-auth-cookie-string)}
               :content-type :json
               :accept :json
               :throw-exceptions false}))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set fix/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (def site (api.models.site/find-by-name "site-1"))
  (def site-id (:site-id site))

  (fact-group :integration
              (fact "Can route to controller.api.metrics.get-revenue"
                    (let [r (request-metrics "/metrics/revenue" site-id)
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => {:discounts 25360,
                            :promotably-commission 38030,
                            :net-revenue 190170,
                            :total 253560}
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-lift"
                    (let [r (request-metrics "/metrics/lift" site-id)
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
                    (let [r (request-metrics "/metrics/promos" site-id)
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => [{:id "ff926081-a97a-4065-abe0-7da32064c3d8",
                             :code "APRILSALE",
                             :redemptions 159,
                             :revenue 6223.23,
                             :avg-order-value 39.14},
                            {:id "gh926071-b97b-5055-bdf1-8dd22057h2a9",
                             :code "SHIRTCLEARANCE",
                             :redemptions 111,
                             :revenue 4568.32,
                             :avg-order-value 41.15}]
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-rco"
                    (let [r (request-metrics "/metrics/rco" site-id)
                          b (json/read-str (:body r) :key-fn keyword)]
                      b => [{:id "df8236081-a97a-4065-abe0-7da320643ce9",
                             :code "INDECISION",
                             :visits 16546,
                             :qualified 9562,
                             :offered 8123,
                             :redeemed 1021,
                             :redemption-rate 41,
                             :avg-cart-size 3,
                             :avg-revenue 122.05,
                             :total-revenue 125462.34,
                             :avg-discount 12.32,
                             :total-discounts 12252},
                            {:id "eg9347081-a97a-4065-abe0-7da320612df8",
                             :code "NEW-CUSTOMER",
                             :visits 16546,
                             :qualified 9562,
                             :offered 8123,
                             :redeemed 1021,
                             :redemption-rate 41,
                             :avg-cart-size 3,
                             :avg-revenue 122.05,
                             :total-revenue 125462.34,
                             :avg-discount 12.32,
                             :total-discounts 12252}]
                      (:status r) => 200))))

