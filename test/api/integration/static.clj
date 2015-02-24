(ns api.integration.static
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
                                 ;; (truncate)
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set base/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (facts "Index file"
    (let [resp (client/get "http://localhost:3000/")]
      (:body resp) => string?
      (:status resp) => 200
      (get (:cookies resp) "promotably") => truthy))
  (facts "Register file"
    (let [resp (client/get "http://localhost:3000/register"
                           {:throw-exceptions false})]
      (:status resp) => 200
      (get (:cookies resp) "promotably") => truthy))
  (facts "Login file"
    (let [resp (client/get "http://localhost:3000/login"
                           {:throw-exceptions false})]
      (:status resp) => 200
      (get (:cookies resp) "promotably") => truthy)))
