(ns api.integration.events
  (:require
    [api.models.event :refer :all]
    [api.fixtures.event-data :as fix]
    [api.lib.seal :refer [hmac-sha1 url-encode]]
    [api.fixtures.common :refer [site-uuid]]
    [clj-http.client :as client]
    [midje.sweet :refer :all]
    [api.integration.helper :refer :all]
    [api.route :as route]
    [api.system :refer [current-system] :as system]
    [api.config :as config]
    [api.core :as core]))

(defn track
  [sig params]
  (client/get "http://localhost:3000/api/v1/track"
              {:body nil
               :headers {:promotably-auth sig}
               :query-params params
               :content-type :json
               :cookies {config/session-cookie-name
                         {:discard true, :path "/", :value (str (java.util.UUID/randomUUID)), :version 0}}
               :accept :json
               :throw-exceptions false
               :socket-timeout 10000
               :conn-timeout 10000}))

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

    (fact "Can count shopper events by days"
      (count-shopper-events-by-days fix/site-shopper-id "productadd" 1) => 2
      (count-shopper-events-by-days fix/site-shopper-id "productadd" 2) => 3
      (count-shopper-events-by-days fix/site-shopper-id "productadd" 3) => 4
      (count-shopper-events-by-days fix/site-shopper-id "thankyou" 30) => 2)

    (fact "Count orders"
      (orders-since site-uuid fix/site-shopper-id 1) => 0
      (orders-since site-uuid fix/site-shopper-id (* 365 2)) => 1)

    (fact "Count visits"
      (count-shopper-events-by-days fix/site-shopper-id "session-start" (* 365 2)) => 1)

    (fact "Should work when :uuid is :site-id"
      (let [api-secret (str (:api-secret site))
            path (url-encode "/api/v1/track")
            sig-hash (compute-sig-hash "localhost"
                                       "GET"
                                       path
                                       nil
                                       site-id
                                       api-secret)
            params {:site-id (str site-id)
                    :site-shopper-id "6880a72f-4d33-4abb-ad2f-c88b51ebbe19"
                    :event-name "_trackProductView"
                    :sku "T100"
                    :user-id "1"
                    :promotably-auth sig-hash
                    :callback "jQuery1111034964492078870535_1423111893735"
                    "_" "1423111893736"}
            r (track sig-hash params)]
        (:status r) => 200))

    (fact "Track thankyou"
      (let [api-secret (str (:api-secret site))
            path (url-encode "/api/v1/track")
            sig-hash (compute-sig-hash "localhost"
                                       "GET"
                                       path
                                       nil
                                       site-id
                                       api-secret)
            params {:site-id (str site-id)
                    :site-shopper-id "6880a72f-4d33-4abb-ad2f-c88b51ebbe19"
                    :event-name "_trackThankYou"
                    "applied-coupon[]" "CODE,5"
                    "cart-item[]" "W100,WIDGET,,0,,1,10,10"
                    :user-id "1"
                    :promotably-auth sig-hash
                    "shipping-method[]" "free_shipping,0"
                    "discount" "5"
                    "tax" "0"
                    "shipping" "0"
                    "total" "10"
                    "order-id" "9"
                    "order-date" "2015-02-13%2017:00:40"
                    "billing-address" "Colin,Steele,Suite%201450,3811%20Turtle%20Creek%20Blvd,Dallas,TX,75219,US,"
                    "shipping-address" "Colin,Steele,Suite%201450,3811%20Turtle%20Creek%20Blvd,Dallas,TX,75219,US"
                    :callback "jQuery1111034964492078870535_1423111893735"
                    "_" "1423111893736"}
            r (track sig-hash params)]
        (Thread/sleep 5000)
        (:status r) => 200))

))
