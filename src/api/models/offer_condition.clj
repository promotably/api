(ns api.models.offer-condition
  (:require [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher dash-to-underscore-keys]]
            [api.lib.redis :refer [get-integer]]
            [api.lib.schema :refer :all]
            [api.models.event :as event]
            [api.models.redemption :as redemption]
            [api.models.promo :as promo]
            [api.util :refer [hyphenify-key]]
            [clj-time.format]
            [clj-time.core :refer [before? after? now] :as t]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [clojure.string :refer [trim]]
            [korma.core :refer :all]
            [korma.db :refer [transaction]]
            [schema.core :as s]
            [schema.coerce :as sc]))

(defn db-to-condition
  [r]
  (let [matcher sc/string-coercion-matcher
        base (sc/coercer BaseOfferCondition matcher)
        final (sc/coercer OutboundOfferCondition matcher)
        ks (keys r)
        remove-nils (fn [x] (apply dissoc x (for [[k v] x :when (nil? v)] k)))]
    (-> (rename-keys r (zipmap ks (map hyphenify-key ks)))
        remove-nils
        (dissoc :uuid :offer-id :id)
        base
        final)))

;; TODO: DRY up with promo-condition/create-conditions!
(defn create-conditions!
  [conditions]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer DatabaseOfferCondition matcher)
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
                  (insert offer-conditions (values fixed))))
              conditions)))))

;; TODO: DRY up with promo-condition/create-conditions!
(defn delete-conditions!
  [offer-id]
  (delete offer-conditions (where {:offer_id offer-id})))

;; TODO: DRY up with promo-condition/create-conditions!
(defn update-conditions!
  [promo-id c]
  (transaction
   (delete-conditions! promo-id)
   (create-conditions! c)))

(defmulti validate
  (fn [context
      {:keys [type] :as condition}]
    (keyword type)))

(defmethod validate :dates
  [context {:keys [start-date end-date] :as condition}]
  (and (after? (now) (from-sql-date start-date))
       (before? (now) (from-sql-date end-date))))

(defmethod validate :product-views
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [product-views period-in-days] :as condition}]
  (let [pv-count (event/count-shopper-events-by-days site-shopper-id
                                                     "productview"
                                                     period-in-days)]
    (>= pv-count product-views)))

(defmethod validate :repeat-product-views
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [repeat-product-views period-in-days] :as condition}]
  (let [pv-events (group-by #(get-in % [:data :sku])
                            (event/shopper-events site-id site-shopper-id "productview" period-in-days))]
    (if-not (nil? (some #(>= (count %) repeat-product-views)
                        (vals pv-events)))
      true
      false)))

(defmethod validate :num-lifetime-orders
  [{:keys [site-id site-shopper-id]}
   {:keys [num-lifetime-orders] :as condition}]
  ;; Okay, so by lifetime we mean anytime in the last 100 years
  (>= (event/orders-since site-id site-shopper-id (* 100 365))
      num-lifetime-orders))

(defmethod validate :num-cart-adds-in-period
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [num-cart-adds period-in-days] :as condition}]
  (>= (event/count-shopper-events-by-days site-shopper-id "productadd" period-in-days) num-cart-adds))

(defmethod validate :min-orders-in-period
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [num-orders period-in-days] :as condition}]
  (> (event/orders-since site-id site-shopper-id period-in-days) num-orders))

(defmethod validate :max-orders-in-period
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [num-orders period-in-days] :as condition}]
  (< (event/orders-since site-id site-shopper-id period-in-days) num-orders))

(defmethod validate :minutes-on-site
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [minutes-on-site] :as condition}]
  (if (contains? session :started-at)
    (let [session-start (clj-time.format/parse (:started-at session))
          min-time (clj-time.core/plus session-start (clj-time.core/minutes minutes-on-site))]
      (clj-time.core/after? (now) min-time))
    false))

(defmethod validate :minutes-since-last-engagement
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [minutes-since-last-engagement] :as condition}]
  (if (contains? session :last-event-at)
    (let [last (clj-time.format/parse (:last-event-at session))
          min-time (t/plus last (t/minutes minutes-since-last-engagement))]
      (t/after? (now) min-time))
    false))

(defmethod validate :minutes-since-last-offer
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [minutes-since-last-offer] :as condition}]
  (if (contains? session :last-offer-at)
    (let [last (clj-time.format/parse (:last-offer-at session))
          min-time (t/plus last (t/minutes minutes-since-last-offer))]
      (t/after? (now) min-time))
    true))

(defmethod validate :last-order-item-count
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [last-order-item-count] :as condition}]
  (>= (event/item-count-in-last-order site-id site-shopper-id) last-order-item-count))

(defmethod validate :last-order-value
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [last-order-value] :as condition}]
  (>= (event/value-of-last-order site-id site-shopper-id) last-order-value))

(defmethod validate :last-order-max-discount
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [last-order-max-discount] :as condition}]
  (< (event/discount-last-order site-id site-shopper-id) last-order-max-discount))

(defmethod validate :max-redemptions-per-day
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [max-redemptions-per-day] :as condition}]
  (let [the-promo (promo/find-by-uuid (-> offer :reward :promo-id))]
    (>= (redemption/count-in-period (:uuid the-promo) max-redemptions-per-day))))

(defmethod validate :max-discount-per-day
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [max-discount-per-day] :as condition}]
  (let [the-promo (promo/find-by-uuid (-> offer :reward :promo-id))]
    (>= (redemption/total-discounts (:uuid the-promo) max-discount-per-day))))

(defmethod validate :shopper-device-type
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [shopper-device-type] :as condition}]
  (let [ua (:user-agent session)]
    (cond
     (= :all shopper-device-type) true
     (= :phone shopper-device-type) (#{:phone} (:device ua))
     (= :tablet shopper-device-type) (#{:tablet} (:device ua))
     (= :desktop shopper-device-type) (#{:desktop} (:device ua))
     :else false)))

(defmethod validate :num-visits-in-period
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [num-visits period-in-days] :as condition}]
  (let [visits (event/count-shopper-events-by-days site-shopper-id "session-start" period-in-days)]
    (>= visits num-visits)))

