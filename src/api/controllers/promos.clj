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


;;  {:site-id "26b28c70-2144-4427-aee3-b51031b08426", :applied-coupons ["twentyoff"], :code "twentyoff", :shopper-email "cvillecsteele@gmail.com", :shopper-id nil, :cart-contents [{:line-total 39.98, :line-subtotal 39.98, :variation "", :variation-id "", :product-categories [13], :line-subtotal_tax 0, :line-tax 0, :quantity 2, :product-id 11}], :product-ids-on-sale []}
;;
;; TODO: this schema needs to match above real-world input...
;;

(let [inbound-schema {(s/required-key :site-id) s/Str
                      (s/required-key :code) s/Str
                      (s/required-key :shopper-id) s/Str
                      (s/required-key :shopper-email) s/Str}]
  (defn validate-promo
    [{:keys [params body] :as request}]
    (let [{:keys [site-id code] :as input} (read-str (slurp body) :key-fn keyword)
          ;;
          ;; commented out cuz this schema ain't right and I dunno how to fix it yet
          ;; ... and wtf does cp stand for?
          ;;
          ;; {:keys [site-id code] :as cp}
          ;; ((c/coercer inbound-schema
          ;;            (c/first-matcher [custom-matcher
          ;;                              c/string-coercion-matcher]))
          ;; input)
          site-uuid (java.util.UUID/fromString site-id)
          the-promo (promo/find-by-site-uuid-and-code site-uuid code)]
      (if-not the-promo
        {:status 404 :body "Can't find that promo"}
        (let [v (promo/valid? the-promo input)]
          {:status 201 :body (shape-validate (merge v {:uuid (:uuid the-promo)
                                                       :code code}))})))))

;; TODO: use schema
;; input looks like:
;;
;; {"site-id":"26b28c70-2144-4427-aee3-b51031b08426","applied-coupons":["twentyoff"],"code":"twentyoff","shopper-email":"cvillecsteele@gmail.com","shopper-id":null,"cart-contents":[{"product-id":11,"variation":"","variation-id":"","quantity":2,"line-total":39.98,"line-tax":0,"line-subtotal":39.98,"line-subtotal-tax":0,"product-categories":[13]}],"product-ids-on-sale":[]}
;;
(defn calculate-promo
  [{:keys [params body] :as request}]
  (let [{:keys [site-id code] :as input} (read-str (slurp body) :key-fn keyword)
        ;; TODO: use schema
        site-uuid (java.util.UUID/fromString site-id)
        the-promo (promo/find-by-site-uuid-and-code site-uuid code)]
    (if-not the-promo
      {:status 404 :body "Can't find that promo"}
      (let [v (promo/valid? the-promo)]
        {:status 201 :body (shape-calculate (merge v {:discount-amount "5.99"}))}))))
