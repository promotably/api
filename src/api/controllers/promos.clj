(ns api.controllers.promos
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer :all]
            [api.models.promo :as promo]
            [api.models.site :as site]
            [api.views.promos :refer [shape-promo
                                      shape-validate
                                      shape-calculate]]
            [clojure.data.json :refer [read-str]]
            [clojure.tools.logging :as log]
            [schema.coerce :as c]
            [schema.core :as s]))

(defn create-new-promo!
  [{:keys [params body] :as request}]
  (let [input-edn (clojure.edn/read-string (slurp body))
        _ (println input-edn)
        coerced-params ((c/coercer NewPromo
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        input-edn)
        _ (println coerced-params)
        ;; TODO: Handle the site not being found
        the-site (site/find-by-site-uuid (:site-id coerced-params))
        the-promo (promo/new-promo! (assoc coerced-params :site-id (:id the-site)))]
    (println the-promo)
    (shape-promo the-promo)))

(defn show-promo
  [params]
  (log/debug params))

(let [inbound-schema {:site-id s/Uuid
                      (s/optional-key :promotably-auth) s/Str
                      :promo-code s/Str}]
  (defn query-promo
    [{:keys [params] :as request}]
    (let [{:keys [site-id promo-code] :as coerced-params}
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
                      (s/optional-key :promotably-auth) [s/Str]
                      (s/required-key :shopper-id) (s/maybe s/Str)
                      (s/required-key :shopper-email) s/Str
                      (s/optional-key :applied-coupons) [s/Str]
                      (s/optional-key :cart-contents) [{(s/required-key :product-id) s/Str
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
        (let [v (promo/valid? the-promo coerced-params)
              resp (merge v {:uuid (:uuid the-promo)
                             :code code})]
          {:status 201
           :headers {"Content-Type" "application/json; charset=UTF-8"}
           :body (shape-validate resp)}))))

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
        (let [v false]
          (println (:type the-promo))
          (println (class (:type the-promo)))
          {:status 201
           :headers {"Content-Type" "application/json; charset=UTF-8"}
           :body (shape-calculate (merge v
                                         {:discount 0}))})))))
