(ns api.integration.events
  (:import org.postgresql.util.PGobject)
  (:require
   [clj-http.client :as client]
   [midje.sweet :refer :all]
   [api.integration.helper :refer :all]
   [api.fixtures.event-data :as data]
   [api.route :as route]
   [api.system :as system]
   [api.core :as core]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set data/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (future-facts "Something"
    (let [resp (client/get "http://localhost:3000/health-check")]
      (:body resp) => "<h1>I'm here</h1>"
      (get (:cookies resp) "promotably") => truthy)))

