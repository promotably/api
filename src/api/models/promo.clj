(ns api.models.promo
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [api.db :refer :all]
            [api.entities :refer :all]
            [api.models.redemption :as rd]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]))

(def PromoSchema {(s/optional-key :id) s/Int
                  (s/required-key :site-id) s/Int
                  (s/required-key :name) s/Str
                  (s/required-key :code) s/Str
                  (s/optional-key :created-at) s/Inst
                  (s/optional-key :updated-at) s/Inst})

(defn- db-to-promo
  "Convert a database result to a promo that obeys the PromoSchema"
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(sm/defn new-promo! :- PromoSchema
  "Creates a new promo in the database"
  [params :- PromoSchema]
  (let [{:keys [site-id name code]} params]
    (db-to-promo
     (insert promos
             (values {:site_id site-id
                      :name name
                      :code code
                      :created_at (sqlfn now)
                      :updated_at (sqlfn now)
                      :uuid (java.util.UUID/randomUUID)})))))

(sm/defn find-by-site-uuid :- [PromoSchema]
  "Finds all promos for a given site id. Returns a collection (empty
  array if no results found)"
  [site-uuid :- s/Uuid]
  (let [results (select promos
                        (join sites (= :sites.id :site_id))
                        (where {:sites.uuid site-uuid}))]
    (map db-to-promo results)))


(sm/defn ^:always-validate find-by-site-uuid-and-code
  "Finds a promo with the given site id and code combination.
  Returns nil if no results found"
  [site-uuid :- s/Uuid
   promo-code :- s/Str]
  (db-to-promo (first (select promos
                              (join sites (= :sites.id :site_id))
                              (where {:sites.uuid site-uuid
                                      :promos.code (clojure.string/upper-case
                                                    promo-code)})))))

(defn- before-incept?
  [{:keys [incept-date] :as the-promo}]
  (if-not (nil? incept-date)
    (before? (now) (from-sql-date incept-date))
    false))

(defn- after-expiry?
  [{:keys [expiry-date] :as the-promo}]
  (if-not (nil? expiry-date)
    (after? (now) (from-sql-date expiry-date))
    false))

(defn- max-usage-exceeded?
  [{:keys [max-usage-count current-usage-count] :as the-promo}]
  (if-not (nil? max-usage-count)
    (> current-usage-count max-usage-count)
    false))

(defn individual-shopper-usage-exceeded?
  [{:keys [usage-limit-per-user id] :as the-promo}
   {:keys [shopper-email] :as context}]
  (when-not (nil? usage-limit-per-user)
    (let [redemption-count
          (rd/count-by-promo-and-shopper-email id shopper-email)]
      (> redemption-count usage-limit-per-user))))

(defn- cart-includes-excluded-products?
  [{:keys [exclude-product-ids] :as the-promo}
   {:keys [cart-contents] :as context}]
  (when-not (and (nil? exclude-product-ids)
                 (nil? cart-contents))
    (seq (intersection (set exclude-product-ids)
                       (set (map :product-id cart-contents))))))

(defn- cart-includes-excluded-product-categories?
  [{:keys [exclude-product-categories] :as the-promo}
   {:keys [cart-contents] :as context}]
  (when-not (and (nil? exclude-product-categories)
                 (nil? cart-contents))
    (seq (intersection (set exclude-product-categories)
                       (set (mapcat :product-categories cart-contents))))))

(defn- cart-missing-required-products?
  "If a promo has required product ids, there has to be at
   least one of those in the cart"
  [{:keys [product-ids] :as the-promo}
   {:keys [cart-contents] :as context}]
  (when-not (nil? product-ids)
    (not= (count (intersection (set product-ids)
                               (set (map :product-id cart-contents))))
          (count product-ids))))

(defn- cart-violates-minimum-amount?
  [{:keys [minimum-cart-amount] :as the-promo}
   {:keys [cart-contents] :as context}]
  (when-not (nil? minimum-cart-amount)
    (let [cart-total (reduce + (map :line-total cart-contents))]
      (< cart-total minimum-cart-amount))))

(defn valid?
  "Validates whether a promo can be used, based on the rules
   of the promo, and the context passed in"
  [the-promo context]
  (cond (not (:active the-promo))
        {:valid false :message "That promo is currently inactive"}
        (before-incept? the-promo)
        {:valid false :message "That promo hasn't started yet"}
        (after-expiry? the-promo)
        {:valid false :message "That promo has expired"}
        (max-usage-exceeded? the-promo)
        {:valid false :message "That promo is no longer available"}
        (individual-shopper-usage-exceeded? the-promo context)
        {:valid false :message "Shopper has exceeded maximum usage"}
        (cart-includes-excluded-products? the-promo context)
        {:valid false :message "There is an excluded product in the cart"}
        (cart-includes-excluded-product-categories? the-promo context)
        {:valid false :message "There is an excluded product category in the cart"}
        (cart-missing-required-products? the-promo context)
        {:valid false :message "Required products are missing"}
        (cart-violates-minimum-amount? the-promo context)
        {:valid false :message "The total cart amount is less than the minimum"}
        :else {:valid true}))
