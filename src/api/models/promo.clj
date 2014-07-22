(ns api.models.promo
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [api.db :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher]]
            [api.models.redemption :as rd]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]
            [schema.coerce :as sc]))

(def PromoSchema {(s/optional-key :id) s/Int
                  (s/required-key :site-id) s/Int
                  (s/required-key :name) s/Str
                  (s/required-key :code) s/Str
                  (s/optional-key :created-at) s/Inst
                  (s/optional-key :updated-at) s/Inst
                  (s/required-key :uuid) s/Uuid
                  (s/optional-key :incept-date) s/Inst
                  (s/optional-key :expiry-date) s/Inst
                  (s/optional-key :individual-use) s/Bool
                  (s/optional-key :exclude-sale-items) s/Bool
                  (s/optional-key :max-usage-count) (s/maybe s/Int)
                  (s/optional-key :current-usage-count) s/Int
                  (s/required-key :type) (s/enum :percent-product
                                                 :amount-product
                                                 :percent-cart
                                                 :amount-cart)
                  (s/required-key :active) s/Bool
                  (s/optional-key :amount) s/Num
                  (s/optional-key :apply-before-tax) s/Bool
                  (s/optional-key :free-shipping) s/Bool
                  (s/optional-key :minimum-cart-amount) (s/maybe s/Num)
                  (s/optional-key :minimum-product-amount) (s/maybe s/Num)
                  (s/optional-key :usage-limit-per-user) (s/maybe s/Int)
                  (s/optional-key :product-ids) [s/Str]
                  (s/optional-key :exclude-product-ids) [s/Str]
                  (s/optional-key :product-categories) [s/Str]
                  (s/optional-key :exclude-product-categories) [s/Str]
                  (s/optional-key :limit-usage-to-x-items) (s/maybe s/Int)})

(defn- jdbc-array->seq
  [^org.postgresql.jdbc4.Jdbc4Array jdbc-array]
  (when-not (nil? jdbc-array)
    (seq (.getArray jdbc-array))))

(defn db-to-promo
  "Convert a database result to a promo that obeys the PromoSchema"
  [r]
  (let [ks (keys r)
        {:keys [product-ids product-categories
                exclude-product-ids exclude-product-categories]
         :as hyphenified-params} (rename-keys r (zipmap ks (map hyphenify-key ks)))]
    ((sc/coercer PromoSchema
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     (merge hyphenified-params
            {:product-ids (jdbc-array->seq product-ids)
             :product-categories (jdbc-array->seq product-categories)
             :exclude-product-ids (jdbc-array->seq exclude-product-ids)
             :exclude-product-categories (jdbc-array->seq exclude-product-categories)}))))

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
  (let [row (first (select promos
                           (join sites (= :sites.id :site_id))
                           (where {:sites.uuid site-uuid
                                   :promos.code (clojure.string/upper-case
                                                 promo-code)})))]
    (when row (db-to-promo row))))

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

(defn- product-id-line-intersect
  [pid-int cart-contents]
  (filter #(some #{(:product-id %)} pid-int)
          cart-contents))

(defn- product-categories-line-intersect
  [pc-int cart-contents]
  (filter (fn [item]
            (seq? (seq
                   (intersection (set (:product-categories item))
                                 pc-int))))
          cart-contents))

(defn- line-to-discount
  [pid-int pc-int cart-contents]
  (let [pid-line-int (product-id-line-intersect pid-int
                                                cart-contents)
        pc-line-int (product-categories-line-intersect pc-int
                                                       cart-contents)
        eligible-lines (cond (seq pid-line-int) pid-line-int
                             (seq pc-line-int) pc-line-int
                             :else nil)]
    (when eligible-lines
      (first (sort-by :line-subtotal <
                      eligible-lines)))))

;; TODO: This function only applies to percentage discounts, not
;; amount discounts
(defn- per-item-discount
  [ltd amount]
  {:pre [(map? ltd)
         (contains? ltd :line-subtotal)
         (contains? ltd :quantity)
         (float? (:line-subtotal ltd))
         (integer? (:quantity ltd))
         (number? amount)]}
  (* (/ (:line-subtotal ltd)
        (:quantity ltd))
     amount))

(defn- product-id-intersect
  [product-ids cart-contents]
  (intersection (set product-ids)
                (set (map :product-id cart-contents))))

(defn- product-categories-intersect
  [product-categories cart-contents]
  (intersection (set product-categories)
                (set (mapcat :product-categories cart-contents))))

(defn- discount-quantity
  [limit-usage-to-x-items quantity]
  {:pre [(integer? quantity)]}
  (if-not (nil? limit-usage-to-x-items)
    (min quantity limit-usage-to-x-items)
    quantity))

(defmulti calculate-discount
  (fn [{:keys [type]}
      context] type))

(defmethod calculate-discount :percent-product
  [{:keys [type product-ids product-categories
           amount limit-usage-to-x-items] :as the-promo}
   {:keys [cart-contents] :as context}]
  (let [pid-int (product-id-intersect product-ids cart-contents)
        pc-int (product-categories-intersect product-categories cart-contents)
        ltd (line-to-discount pid-int pc-int cart-contents)]
    (if ltd
      (let [pid (per-item-discount ltd amount)
            dq (discount-quantity limit-usage-to-x-items (:quantity ltd))]
        {:discount-amount (* pid dq)
         :discounted-product-id (:product-id ltd)
         :discounted-cart false
         :number-discounted-items dq})
      {:discount-amount 0.00})))


(defmethod calculate-discount :amount-product
  [{:keys [type product-ids product-categories
           limit-usage-to-x-items amount] :as the-promo}
   {:keys [cart-contents] :as context}]
  (let [pid-int (product-id-intersect product-ids cart-contents)
        pc-int (product-categories-intersect product-categories cart-contents)
        ltd (line-to-discount pid-int pc-int cart-contents)]
    (if ltd
      (let [dq (discount-quantity limit-usage-to-x-items (:quantity ltd))]
        {:discount-amount (* amount dq)
         :discounted-product-id (:product-id ltd)
         :discounted-cart false
         :number-discounted-items dq})
      {:discount-amount 0.00})))

(defmethod calculate-discount :percent-cart
  [{:keys [type] :as the-promo}
   {:keys [cart-contents] :as context}]
  :jolly-good)

(defmethod calculate-discount :amount-cart
  [{:keys [type] :as the-promo}
   {:keys [cart-contents] :as context}]
  :good-show)
