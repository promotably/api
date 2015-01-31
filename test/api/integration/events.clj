(ns api.integration.events
  (:import org.postgresql.util.PGobject)
  (:require
    [api.models.event :refer :all]
    [api.fixtures.event-data :as fix]
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
          (count-shopper-events-by-days fix/shopper-id "productadd" 1) => 2
          (count-shopper-events-by-days fix/shopper-id "productadd" 2) => 3
          (count-shopper-events-by-days fix/shopper-id "productadd" 3) => 4
          (count-shopper-events-by-days fix/shopper-id "thankyou" 30) => 1))

  (future-facts "Something"
    (let [resp (client/get "http://localhost:3000/health-check")]
      (:body resp) => "<h1>I'm here</h1>"
      (get (:cookies resp) "promotably") => truthy)))
