(ns api.integration.static
  (:require
   [api.integration.helper :refer :all]
   [api.fixtures.basic :as base]
   [api.route :as route]
   [clj-http.client :as client]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (init!)
                                 (load-fixture-set base/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (facts "Index file"
    (let [resp (client/get (str (test-target-url) "/"))]
      (:body resp) => string?
      (:status resp) => 200
      (get (:cookies resp) "promotably") => truthy))
  (facts "Register file"
    (let [resp (client/get (str (test-target-url) "/register")
                           {:throw-exceptions false})]
      (:status resp) => 200
      (get (:cookies resp) "promotably") => truthy))
  (facts "Login file"
    (let [resp (client/get (str (test-target-url) "/login")
                           {:throw-exceptions false})]
      (:status resp) => 200
      (get (:cookies resp) "promotably") => truthy)))
