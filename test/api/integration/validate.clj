(ns api.integration.validate
  (:require
   [api.fixtures.validate]
   [api.integration.helper :refer :all]
   [api.lib.seal :refer [hmac-sha1 url-encode]]
   [api.route :as route]
   [api.system :as system]
   [api.core :as core]
   [api.models.site]
   [api.models.promo :as promo]
   [clojure.tools.logging :as log]
   [clj-http.client :as client]
   [clj-time.core :as t]
   [clj-time.format :as tf]
   [clojure.data.json :as json]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set api.fixtures.validate/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (def site (api.models.site/find-by-name "site-1"))
  (def site-id (:site-id site))

  (fact-group :integration

              (facts "Validate Promo Happy Path"
                (let [b (json/read-str (:body (lookup-promos site-id)) :key-fn keyword)
                      code (:code (first b))
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str {:site-id (str site-id)
                                               :code code
                                               :shopper-email "shopper@shop.com"})
                      body-hash (hmac-sha1 (.getBytes api-secret)
                                           (.getBytes rq-body))
                      time-val (tf/unparse (tf/formatters :basic-date-time-no-ms)
                                           (t/now))
                      sig-str (hmac-sha1 (.getBytes api-secret)
                                         (.getBytes (apply str
                                                           (str site-id) "\n"
                                                           api-secret "\n"
                                                           "localhost" "\n"
                                                           "POST" "\n"
                                                           (url-encode (str "/api/v1/promos/validation/" code)) "\n"
                                                           time-val "\n"
                                                           body-hash "\n"
                                                           "" "\n"
                                                           "" "\n")))
                      sig-hash (str "hmac-sha1///" time-val "/" sig-str)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code "EASTER"
                                              :valid false
                                              :messages ["The coupon has expired."]})
                  (:status r) => 201))

              (facts "Validate Promo 403 if auth not properly formed"
                (let [b (json/read-str (:body (lookup-promos site-id)) :key-fn keyword)
                      code (:code (first b))
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str {:site-id (str site-id)
                                               :code code
                                               :shopper-email "shopper@shop.com"})
                      body-hash (hmac-sha1 (.getBytes api-secret)
                                           (.getBytes rq-body))
                      time-val (tf/unparse (tf/formatters :basic-date-time-no-ms)
                                           (t/now))
                      sig-str (hmac-sha1 (.getBytes api-secret)
                                         (.getBytes (apply str
                                                           (str site-id) "\n"
                                                           api-secret "\n"
                                                           "localhost" "\n"
                                                           "GET" "\n"
                                                           (url-encode (str "/api/v1/promos/validation/" code)) "\n"
                                                           time-val "\n"
                                                           body-hash "\n"
                                                           "" "\n"
                                                           "" "\n")))
                      sig-hash (str "hmac-sha1///" time-val "/" sig-str)
                      r (validate-promo code (str site-id) rq-body sig-hash)]
                  (:status r) => 403))))
