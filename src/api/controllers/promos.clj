(ns api.controllers.promos
  (:require [clojure.data.json :refer [read-str]]
            [clojure.tools.logging :as log]
            [api.lib.coercion-helper :refer [custom-matcher]]
            [api.models.promo :as promo]
            [api.views.promos :refer [shape-promo
                                         shape-validate
                                         shape-calculate]]
            [cemerick.friend :as friend]
            [schema.core :as s]
            [schema.coerce :as c]))

(defn create-new-promo!
  [{:keys [params] :as request}]
  (log/debug params)
  (comment
    (let [user (friend/current-authentication)]
      (promo/new-promo! (:account_id user) params))))

(defn show-promo
  [params]
  (log/debug params))

(let [inbound-schema {:site-id s/Uuid :promo-code s/Str}]
  (defn query-promo
    [{:keys [params] :as request}]
    (let [{:keys [site-id promo-code] :as cp}
          ((c/coercer inbound-schema
                      (c/first-matcher [custom-matcher
                                        c/string-coercion-matcher]))
           params)
          the-promo (promo/find-by-site-uuid-and-code site-id
                                                      promo-code)]
      (if-not the-promo
        {:status 404 :body "Can't find that promo"}
        (do
          {:body (shape-promo the-promo)})))))

(let [inbound-schema {(s/required-key :site-id) s/Uuid
                      (s/required-key :code) s/Str
                      (s/required-key :shopper-id) s/Str
                      (s/required-key :shopper-email) s/Str
                      (s/optional-key :applied-coupons) [s/Str]
                      (s/optional-key :cart-items) [{(s/required-key :product-id) s/Str
                                                     (s/optional-key :product-title) s/Str
                                                     (s/optional-key :product-type) s/Str
                                                     (s/optional-key :product-categories) [s/Str]
                                                     (s/optional-key :variation-id) s/Str
                                                     (s/optional-key :variation) s/Str
                                                     (s/optional-key :quantity) s/Int
                                                     (s/optional-key :line-total) s/Num
                                                     (s/optional-key :line-subtotal) s/Num
                                                     (s/optional-key :line-tax) s/Num
                                                     (s/optional-key :line-subtotal-tax) s/Num}]
                      (s/optional-key :product-ids-on-sale) [s/Str]}]
  (defn validate-promo
    [{:keys [params body] :as request}]
    (let [input-json (read-str (slurp body) :key-fn keyword)
          {:keys [site-id code] :as coerced-params}
            ((c/coercer inbound-schema
                        (c/first-matcher [custom-matcher
                                          c/string-coercion-matcher]))
             input-json)
          the-promo (promo/find-by-site-uuid-and-code site-id code)]
      (if-not the-promo
        {:status 404 :body "Can't find that promo"}
        (let [v (promo/valid? the-promo coerced-params)]
          {:status 201 :body (shape-validate (merge v {:uuid (:uuid the-promo)
                                                       :code code}))}))))

  (defn calculate-promo
    [{:keys [params body] :as request}]
    (let [input-json (read-str (slurp body) :key-fn keyword)
          {:keys [site-id code] :as coerced-params}
          ((c/coercer inbound-schema
                      (c/first-matcher [custom-matcher
                                        c/string-coercion-matcher]))
           input-json)
          the-promo (promo/find-by-site-uuid-and-code site-id code)]
      (if-not the-promo
        {:status 404 :body "Can't find that promo"}
        (let [v (promo/valid? the-promo coerced-params)]
          {:status 201 :body (shape-calculate (merge v {:discount-amount "5.99"}))})))))
