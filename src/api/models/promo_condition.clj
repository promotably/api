(ns api.models.promo-condition
  (:require
   [clojure.set]
   [clojure.string :refer [trim]]
   [api.entities :refer :all]
   [api.lib.coercion-helper :refer [custom-matcher dash-to-underscore-keys]]
   [api.lib.schema :refer :all]
   [api.models.redemption :as redemption]
   [api.util :refer [hyphenify-key]]
   [korma.core :refer :all]
   [korma.db :refer [transaction]]
   [clojure.set :refer [rename-keys intersection]]
   [clj-time.coerce :refer [from-sql-date from-sql-time
                            from-date to-sql-date to-sql-time
                            to-date]]
   [clj-time.core :refer [before? after? now today-at hour minute]]
   [schema.core :as s]
   [schema.coerce :as sc]))

(defn- db-to-condition
  [r]
  (let [ks (keys r)
        hyphenified-params (rename-keys r (zipmap ks (map hyphenify-key ks)))]
    ((sc/coercer PromoCondition
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     hyphenified-params)))

(defn create-conditions!
  [conditions]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer DatabaseCondition matcher)
        arrify (fn [m k]
                 (assoc m k (sqlfn "string_to_array"
                                   (apply str (interpose "," (map trim (k m))))
                                   ",")))]
    (if (seq conditions)
      (doall (map
              (fn [c]
                (let [coerced (-> c
                                  (assoc :type (name (:type c)))
                                  coercer)
                      undered (dash-to-underscore-keys coerced)
                      fixers (for [[k v] undered :when (vector? v)] k)
                      fixed (reduce
                             arrify
                             undered
                             fixers)]
                  (insert promo-conditions (values fixed))))
              conditions)))))

(defn delete-conditions!
  [promo-id]
  (delete promo-conditions (where {:promo_id promo-id})))

(defn update-conditions!
  [promo-id c]
  (transaction
   (delete-conditions! promo-id)
   (create-conditions! c)))

(defmulti validate
  (fn [context {t :type :as condition}]
    t))

(def explanitory-stuff
  {:dates "BETWEEN dates"
   :times "BETWEEN times of day"
   :usage-count "WHILE the count of coupon uses is below this value"
   :daily-usage-count "WHILE the count of coupon uses today is below this value"
   :total-discounts "WHILE total discounts generated by this coupon are below this value"
   :product-ids "MATCHING these product IDs"
   :category-ids "MATCHING these product categories"
   :not-product-ids "NOT for these product IDs"
   :not-category-ids "NOT for these product categories"
   :combo-product-ids "if a COMBINATION of matching product IDs is in the order"
   :item-count "if the COUNT of matching items in the order exceeds a value"
   :item-value "if the PRICE of matching items exceeds a value"
   :individual-use "if no other COUPONS/PROMOS are being used"
   :min-order-value "if the order TOTAL exceeds a threshold"})

;; NOTE:
;;
;; Woocommerce asks for validation in stages.  In the first stage, elements of
;; cart-contents will look like:
;;
;;   {:sku "T100", :quantity 2, product-categories ["6"], variation-id "", :variation ""}
;;
;; In the second stage, the element will also have:
;;
;;   line-tax 0, :line-subtotal-tax 0, :line-subtotal 20, :line-total 20
;;
;; Validators therefore may be called multiple times during a single
;; logical "cart validation".  Those that depend on presence/absence
;; of product or category ids are fine in both passes.  Those that
;; depend on line-oriented data should always validate on the first
;; pass, and do their real work on the second pass.
;;

(defmethod validate :dates
  [context
   {:keys [start-date end-date] :as condition}]
  (let [ok? (and (after? (now) (from-date start-date))
                 (before? (now) (from-date end-date)))]
    (cond
     (not ok?)
     (update-in context [:errors] conj "The coupon has expired.")
     :else context)))

(defmethod validate :times
  [context
   {:keys [start-time end-time] :as condition}]
  (let [start (Integer/parseInt (clojure.string/replace start-time #":" ""))
        end (Integer/parseInt (clojure.string/replace end-time #":" ""))
        right-now (Integer/parseInt (format "%02d%02d" (hour (now)) (minute (now))))
        ok? (and (<= start right-now) (<= right-now end))]
    (cond
     (not ok?)
     (let [msg (format "The coupon is only valid between %s and %s."
                       start-time
                       end-time)]
       (update-in context [:errors] conj msg))
     :else context)))

(defmethod validate :product-ids
  [{:keys [cart-contents matching-products] :as context}
   {:keys [product-ids] :as condition}]
  (let [keepers (filter #(get (set product-ids) (:sku %))
                        cart-contents)
        updated-context (assoc context :matching-products keepers)]
    (cond
     (not (seq keepers))
     (update-in updated-context [:errors] conj "No products match this coupon.")
     :else updated-context)))

(defmethod validate :combo-product-ids
  [{:keys [matching-products cart-contents] :as context}
   {:keys [combo-product-ids] :as condition}]
  (let [keepers (filter #(get (set combo-product-ids) (:sku %))
                        (or matching-products cart-contents))
        updated-context (assoc context :matching-products keepers)]
    (cond
     (not= (count combo-product-ids) (count keepers))
     (update-in updated-context [:errors] conj
                "This coupon is not valid for the combination of products selected.")
     :else updated-context)))

(defmethod validate :not-product-ids
  [{:keys [cart-contents matching-products] :as context}
   {:keys [not-product-ids] :as condition}]
  (let [keepers (keep #(if (not (get (set not-product-ids) (:sku %)))
                         %)
                      (or matching-products cart-contents))
        updated-context (assoc context :matching-products keepers)]
    (cond
     (not (seq keepers))
     (update-in context [:errors] conj
                "None of your items are eligible for this coupon.")
     :else context)))

(defmethod validate :category-ids
  [{:keys [matching-products cart-contents] :as context}
   {:keys [category-ids] :as condition}]
  (let [keepers (filter #(let [i (clojure.set/intersection
                                  (set category-ids)
                                  (set (:product-categories %)))]
                           (seq i))
                        (or matching-products cart-contents))
        updated-context (assoc context :matching-products keepers)]
    (cond
     (not (seq keepers))
     (update-in updated-context [:errors] conj
                "No products match this coupon's categories.")
     :else updated-context)))

(defmethod validate :not-category-ids
  [{:keys [matching-products cart-contents] :as context}
   {:keys [not-category-ids] :as condition}]
  (let [keepers (remove #(seq (clojure.set/intersection
                               (set not-category-ids)
                               (set (:product-categories %))))
                        (or matching-products cart-contents))
        updated-context (assoc context :matching-products keepers)]
    (cond
     (not (seq keepers))
     (update-in updated-context [:errors] conj
                "No products match this coupon's categories.")
     :else updated-context)))

(defmethod validate :item-count
  [{:keys [matching-products cart-contents] :as context}
   {:keys [item-count] :as condition}]
  (let [quantities (map :quantity (or matching-products cart-contents))
        total (apply + quantities)]
    (cond
     (< total item-count)
     (update-in context [:errors] conj
                (format "This coupon requires at least %d matching items in your cart."
                        item-count))
     :else context)))

(defmethod validate :item-value
  [{:keys [matching-products cart-contents] :as context}
   {:keys [item-value] :as condition}]
  (let [amounts (map :line-subtotal (or matching-products cart-contents))
        total (or (apply + amounts) 0)]
    (cond
     (< total item-value)
     (update-in context [:errors] conj
                (format "To qualify for this coupon, the total value of eligible items in your cart must exceed %.2f."
                        item-value))
     :else context)))

(defmethod validate :min-order-value
  [{:keys [matching-products cart-contents] :as context}
   {:keys [min-order-value] :as condition}]
  (let [amounts (map :line-total cart-contents)
        total (apply + amounts)]
    (cond
     (< total min-order-value)
     (update-in context [:errors] conj
                (format "To qualify for this coupon your cart value must exceed %.2f."
                        min-order-value))
     :else context)))

(defmethod validate :individual-use
  [{:keys [applied-coupons matching-products cart-contents] :as context}
   condition]
  (cond
   (> (count applied-coupons) 0)
   (update-in context [:errors] conj "This coupon can not be used with any others.")
   :else context))

(defmethod validate :total-discounts
  [{:keys [current-total-discounts] :as context}
   {:keys [total-discounts] :as condition}]
  (cond
   (>= current-total-discounts total-discounts)
   (update-in context [:errors] conj "This promotion has ended")
   :else context))

(defmethod validate :usage-count
  [{:keys [current-usage-count] :as context}
   {:keys [usage-count] :as condition}]
  (cond
   (>= current-usage-count usage-count)
   (update-in context [:errors] conj "This promotion has ended")
   :else context))
