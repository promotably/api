(ns api.integration.basic
  (:require
   [api.integration.helper :refer :all]
   [api.fixtures.basic :as base]
   [api.route :as route]
   [api.db :as db]
   [api.integration.helper :refer [test-target-url init!]]
   [cheshire.core :refer :all]
   [clj-http.client :as client]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (init!)
                                 (load-fixture-set base/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (facts "Health check"
    (let [resp (client/get (str (test-target-url) "/health-check"))
          body (parse-string (:body resp) keyword)]
      body => map?
      (get (:cookies resp) "promotably") => truthy)))
