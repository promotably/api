(ns ^{:author "tsteffes@promotably.com"
      :doc "Validation tests for usage-count and total-discounts on promos.
           The validate integration test file was getting way too big, so I split out
           these tests to this file."}
  api.integration.validate-usage
  (:require
   [api.fixtures.validate-usage]
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

(against-background  [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set api.fixtures.validate-usage/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

   (def site (api.models.site/find-by-name "site-1"))
   (def site-id (:uuid site))

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
      :cart-contents [{:sku "W100",
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
               (facts "Validate Exceeded Usage Count"
                 (let [code "P5"
                       api-secret (str (:api-secret site))
                       rq-body (json/write-str (basic-request-data (str site-id) code))
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
                                               :messages (just ["This promotion has ended"])})
                   (:status r) => 201))

               (facts "Validate Non-Exceeded Usage Count"
                   (let [code "P6"
                         api-secret (str (:api-secret site))
                         rq-body (json/write-str (basic-request-data (str site-id) code))
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

               (facts "Validate Non-Exceeded Total Discounts"
                   (let [code "P7"
                         api-secret (str (:api-secret site))
                         rq-body (json/write-str (basic-request-data (str site-id) code))
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

               (facts "Validate Exceeded Total Discounts"
                 (let [code "P8"
                       api-secret (str (:api-secret site))
                       rq-body (json/write-str (basic-request-data (str site-id) code))
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
                                               :messages ["This promotion has ended"]})
                   (:status r) => 201))

               (facts "Validate Exceeded Daily Usage Count"
                   (let [code "P9"
                         api-secret (str (:api-secret site))
                         rq-body (json/write-str (basic-request-data (str site-id) code))
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
                                                 :messages ["No more for today, check back tomorrow!"]})
                     (:status r) => 201))

                (facts "Validate Exceeded Daily Total Discounts"
                   (let [code "P10"
                         api-secret (str (:api-secret site))
                         rq-body (json/write-str (basic-request-data (str site-id) code))
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
                                                 :messages ["No more for today, check back tomorrow!"]})
                     (:status r) => 201))

               (facts "Validate Non-Exceeded Daily Discounts"
                 (let [code "P11"
                       api-secret (str (:api-secret site))
                       rq-body (json/write-str (basic-request-data (str site-id) code))
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
                   (:status r) => 201))))
