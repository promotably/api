(ns api.integration.basic
  (:require
   [api.integration.helper :refer :all]
   [api.fixtures.basic :as base]
   [api.route :as route]
   [api.system :as system]
   [api.core :as core]
   [api.db :as db]
   [cheshire.core :refer :all]
   [clj-http.client :as client]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-or-truncate)
                                 (load-fixture-set base/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (facts "Health check"
    (let [resp (client/get "http://localhost:3000/health-check")
          body (parse-string (:body resp) keyword)]
      body => map?
      (get (:cookies resp) "promotably") => truthy)))
