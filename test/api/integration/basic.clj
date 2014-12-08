(ns api.integration.basic
  (:require
   [api.integration.helper :refer :all]
   [api.fixtures.basic :as base]
   [api.route :as route]
   [api.system :as system]
   [api.core :as core]
   [clj-http.client :as client]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set base/fixture-set)))
                     (after :contents
                            (migrate-down))]

  (facts "Health check"
    (let [resp (client/get "http://localhost:3000/health-check")]
      (:body resp) => "<h1>I'm here</h1>"
      (get (:cookies resp) "promotably") => truthy)))

