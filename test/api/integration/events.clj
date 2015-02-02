(ns api.integration.events
  (:import org.postgresql.util.PGobject)
  (:require
    [api.models.event :refer :all]
    [api.fixtures.event-data :as fix]
    [api.fixtures.common :refer [site-uuid]]
    [clj-http.client :as client]
    [midje.sweet :refer :all]
    [api.integration.helper :refer :all]
    [api.route :as route]
    [api.system :as system]
    [api.core :as core]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set fix/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (fact-group :integration
    (fact "Can count shopper events by days"
      (count-shopper-events-by-days fix/site-shopper-id "productadd" 1) => 2
      (count-shopper-events-by-days fix/site-shopper-id "productadd" 2) => 3
      (count-shopper-events-by-days fix/site-shopper-id "productadd" 3) => 4
      (count-shopper-events-by-days fix/site-shopper-id "thankyou" 30) => 2))

    (fact "Count orders"
      (orders-since site-uuid fix/site-shopper-id 1) => 0
      (orders-since site-uuid fix/site-shopper-id 90) => 1))

