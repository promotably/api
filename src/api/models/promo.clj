(ns api.models.promo
  (:require [clj-time.core :refer [before? after? now]]
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
  [the-promo]
  (if-not (nil? (:incept-date the-promo))
    (before? (now) (from-sql-date (:incept-date the-promo)))
    false))

(defn- after-expiry?
  [the-promo]
  (if-not (nil? (:expiry-date the-promo))
    (after? (now) (from-sql-date (:expiry-date the-promo)))
    false))

(defn- max-usage-exceeded?
  [the-promo]
  (if-not (nil? (:max-usage-count the-promo))
    (> (:current-usage-count the-promo) (:max-usage-count the-promo))
    false))

(defn individual-shopper-usage-exceeded?
  [the-promo context]
  (when-not (nil? (:usage-limit-per-user the-promo))
    (let [redemption-count
          (rd/count-by-promo-and-shopper-email (:id the-promo) (:shopper-email context))]
      (> redemption-count (:usage-limit-per-user the-promo)))))

(defn- cart-includes-excluded-products?
  [the-promo context]
  (when-not (and (nil? (:exclude-product-ids the-promo))
                 (nil? (:cart-items context)))
    (seq (intersection (set (:exclude-product-ids the-promo))
                       (set (map :product-id (:cart-items context)))))))

(defn- cart-includes-excluded-product-categories?
  [the-promo context]
  (when-not (and (nil? (:exclude-product-categories the-promo))
                 (nil? (:cart-items context)))
    (seq (intersection (set (:exclude-product-categories the-promo))
                       (set (mapcat :product-categories (:cart-items context)))))))

(defn- cart-missing-required-products?
  "If a promo has required product ids, there has to be at
   least one of those in the cart"
  [the-promo context]
  (when-not (nil? (:product-ids the-promo))
    (not= (count (intersection (set (:product-ids the-promo))
                                  (set (map :product-id (:cart-items context)))))
          (count (:product-ids the-promo)))))


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
        :else {:valid true}))
