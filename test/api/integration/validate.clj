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

  (defn compute-sig-hash
    [host verb path body site-id api-secret]
    (let [body-hash (hmac-sha1 (.getBytes api-secret)
                               (.getBytes body))
          time-val (tf/unparse (tf/formatters :basic-date-time-no-ms)
                               (t/now))
          sig-str (hmac-sha1 (.getBytes api-secret)
                             (.getBytes (apply str
                                               (str site-id) "\n"
                                               api-secret "\n"
                                               host "\n"
                                               verb "\n"
                                               path "\n"
                                               time-val "\n"
                                               body-hash "\n"
                                               "" "\n"
                                               "" "\n")))]
      (str "hmac-sha1///" time-val "/" sig-str)))

  (defn basic-request-data
    [site-id code]
    {:site-id (str site-id)
     :applied-coupons ["other-coupon"],
     :code code
     :shopper-email "colin@promotably.com",
     :shopper-id nil,
     :cart-contents [{:product-id "W100",
                      :variation "",
                      :variation-id "",
                      :quantity 6,
                      :line-total 120,
                      :line-tax 0,
                      :line-subtotal 120,
                      :line-subtotal-tax 0,
                      :product-categories ["1"]}],
     :billing_address_1 "",
     :billing_city "",
     :billing_state "",
     :billing_country "US",
     :billing_postcode "",
     :billing_email "colin@promotably.com",
     :shipping_address_1 "",
     :shipping_city "",
     :shipping_state "",
     :shipping_country "US",
     :shipping_postcode "",
     :shipping_email "",
     :product-ids-on-sale []})

  (fact-group :integration

              (facts "Validate Expired Promo"
                (let [code "P1"
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str (basic-request-data site-id code))
                      path (url-encode (str "/api/v1/promos/validation/" code))
                      sig-hash (compute-sig-hash "localhost"
                                                 "POST"
                                                 path
                                                 rq-body
                                                 (str site-id)
                                                 api-secret)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code code
                                              :valid false
                                              :messages ["The coupon has expired."]})
                  (:status r) => 201))

              (facts "Validate Non-Expired Promo"
                (let [code "P2"
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str (basic-request-data site-id code))
                      path (url-encode (str "/api/v1/promos/validation/" code))
                      sig-hash (compute-sig-hash "localhost"
                                                 "POST"
                                                 path
                                                 rq-body
                                                 (str site-id)
                                                 api-secret)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code code
                                              :valid true
                                              :messages []})
                  (:status r) => 201))

              (facts "Validate Time Promo - Outside Valid Times"
                (let [code "P3"
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str (basic-request-data site-id code))
                      path (url-encode (str "/api/v1/promos/validation/" code))
                      sig-hash (compute-sig-hash "localhost"
                                                 "POST"
                                                 path
                                                 rq-body
                                                 (str site-id)
                                                 api-secret)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code code
                                              :valid false
                                              :messages ["The coupon is only valid between 00:00 and 00:00."]})
                  (:status r) => 201))

              (facts "Validate Time Promo - Inside Valid Times"
                (let [code "P4"
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str (basic-request-data site-id code))
                      path (url-encode (str "/api/v1/promos/validation/" code))
                      sig-hash (compute-sig-hash "localhost"
                                                 "POST"
                                                 path
                                                 rq-body
                                                 (str site-id)
                                                 api-secret)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code code
                                              :valid true
                                              :messages []})
                  (:status r) => 201))

              (future-facts "Validate Exceeded Usage Count"
                (let [code "P5"
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str (basic-request-data site-id code))
                      path (url-encode (str "/api/v1/promos/validation/" code))
                      sig-hash (compute-sig-hash "localhost"
                                                 "POST"
                                                 path
                                                 rq-body
                                                 (str site-id)
                                                 api-secret)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code code
                                              :valid false
                                              :messages []})
                  (:status r) => 201))

              (future-facts "Validate Non-Exceeded Usage Count"
                (let [code "P6"
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str (basic-request-data site-id code))
                      path (url-encode (str "/api/v1/promos/validation/" code))
                      sig-hash (compute-sig-hash "localhost"
                                                 "POST"
                                                 path
                                                 rq-body
                                                 (str site-id)
                                                 api-secret)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code code
                                              :valid true
                                              :messages []})
                  (:status r) => 201))

              (future-facts "Validate Non-Exceeded Total Discounts"
                (let [code "P7"
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str (basic-request-data site-id code))
                      path (url-encode (str "/api/v1/promos/validation/" code))
                      sig-hash (compute-sig-hash "localhost"
                                                 "POST"
                                                 path
                                                 rq-body
                                                 (str site-id)
                                                 api-secret)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code code
                                              :valid true
                                              :messages []})
                  (:status r) => 201))

              (future-facts "Validate Exceeded Total Discounts"
                (let [code "P8"
                      api-secret (str (:api-secret site))
                      rq-body (json/write-str (basic-request-data site-id code))
                      path (url-encode (str "/api/v1/promos/validation/" code))
                      sig-hash (compute-sig-hash "localhost"
                                                 "POST"
                                                 path
                                                 rq-body
                                                 (str site-id)
                                                 api-secret)
                      r (validate-promo code (str site-id) rq-body sig-hash)
                      response-body (json/read-str (:body r) :key-fn keyword)]
                  response-body => (contains {:code code
                                              :valid false
                                              :messages ["..."]})
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
