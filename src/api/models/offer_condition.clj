(ns api.models.offer-condition
  (:require [api.entities :refer :all]
            [api.lib.coercion-helper
             :refer [custom-matcher dash-to-underscore-keys make-trans]]
            [api.lib.redis :refer [get-integer]]
            [api.lib.schema :refer :all]
            [api.models.event :as event]
            [api.models.redemption :as redemption]
            [api.models.promo :as promo]
            [api.models.site :as site]
            [api.util :refer [hyphenify-key]]
            [clj-time.format]
            [clj-time.core :refer [before? after? now time-zone-for-id to-time-zone
                                   from-time-zone date-time year month day]
             :as t]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [clojure.string :refer [trim split]]
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
        (dissoc :uuid :offer-id :id :created-at)
        base
        final)))

(def fix-keywords
  (make-trans (constantly true)
              #(if (keyword? %2) [%1 (name %2)] [%1 %2])))

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
                (let [undered (-> c
                                  (assoc :type (name (:type c)))
                                  coercer
                                  dash-to-underscore-keys
                                  fix-keywords)
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

(defmulti valid?
  (fn [context
      {:keys [type] :as condition}]
    (keyword type)))

(defmethod valid? :dates
  [context {:keys [start-date end-date] :as condition}]
  (and (after? (now) (from-sql-date start-date))
       (before? (now) (from-sql-date end-date))))

(defmethod valid? :times
  [{:keys [site-id] :as context}
   {:keys [start-time end-time]}]
  (let [the-site (site/find-by-site-uuid site-id)
        site-tz (time-zone-for-id (:timezone the-site))
        now-in-site-tz (to-time-zone (now) site-tz)
        start-pieces (map (fn [s]
                            (Integer/parseInt s))
                          (split start-time #":"))
        today-at-start (from-time-zone (date-time (year now-in-site-tz)
                                                  (month now-in-site-tz)
                                                  (day now-in-site-tz)
                                                  (first start-pieces)
                                                  (second start-pieces))
                                       site-tz)
        end-pieces (map (fn [s]
                          (Integer/parseInt s))
                        (split end-time #":"))
        today-at-end (from-time-zone (date-time (year now-in-site-tz)
                                                (month now-in-site-tz)
                                                (day now-in-site-tz)
                                                (first end-pieces)
                                                (second end-pieces))
                                     site-tz)]
    (and (after? now-in-site-tz today-at-start)
         (before? now-in-site-tz today-at-end))))

(defmethod valid? :product-views
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [product-views period-in-days] :as condition}]
  (let [pv-count (event/count-shopper-events-by-days site-shopper-id
                                                     "productview"
                                                     (or period-in-days 30))]
    (>= pv-count product-views)))

(defmethod valid? :repeat-product-views
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [repeat-product-views period-in-days] :as condition}]
  (let [pv-events (group-by #(get-in % [:data :sku])
                            (event/shopper-events site-id site-shopper-id
                                                  "productview"
                                                  (or period-in-days 30)))]
    (if-not (nil? (some #(>= (count %) repeat-product-views)
                        (vals pv-events)))
      true
      false)))

(defmethod valid? :num-lifetime-orders
  [{:keys [site-id site-shopper-id]}
   {:keys [num-lifetime-orders] :as condition}]
  ;; Okay, so by lifetime we mean anytime in the last 100 years
  (>= (event/orders-since site-id site-shopper-id (* 100 365))
      num-lifetime-orders))

(defmethod valid? :num-cart-adds-in-period
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [num-cart-adds period-in-days] :as condition}]
  (let [current (event/count-shopper-events-by-days site-shopper-id
                                                    "productadd"
                                                    period-in-days)]
    (>= current num-cart-adds)))

(defmethod valid? :min-orders-in-period
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [num-orders period-in-days] :as condition}]
  (let [current (event/orders-since site-id site-shopper-id period-in-days)]
    (>= current num-orders)))

(defmethod valid? :max-orders-in-period
  [{:keys [site-id site-shopper-id] :as context}
   {:keys [num-orders period-in-days] :as condition}]
  (let [current (event/orders-since site-id site-shopper-id period-in-days)]
    (< current num-orders)))

(defmethod valid? :minutes-on-site
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [minutes-on-site] :as condition}]
  (if (contains? session :started-at)
    (let [session-start (clj-time.format/parse (:started-at session))
          min-time (t/plus session-start (t/minutes minutes-on-site))]
      (t/after? (now) min-time))
    false))

(defmethod valid? :minutes-since-last-engagement
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [minutes-since-last-engagement] :as condition}]
  (if (contains? session :last-event-at)
    (let [last (clj-time.format/parse (:last-event-at session))
          min-time (t/plus last (t/minutes minutes-since-last-engagement))]
      (t/after? (now) min-time))
    false))

(defmethod valid? :days-since-last-offer
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [days-since-last-offer] :as condition}]
  (let [last-offer-event (event/last-event site-id site-shopper-id "offer-made")]
    (if-not (nil? last-offer-event)
      (t/before? (t/plus (from-sql-date (:created_at last-offer-event))
                         (t/days days-since-last-offer))
                 (t/now))
      false)))

(defmethod valid? :last-order-item-count
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [last-order-item-count] :as condition}]
  (>= (event/item-count-in-last-order site-id site-shopper-id) last-order-item-count))

(defmethod valid? :last-order-total
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [last-order-total] :as condition}]
  (let [value (event/value-of-last-order site-id site-shopper-id)]
    (>= value last-order-total)))

(defmethod valid? :last-order-max-discount
  [{:keys [session site-id site-shopper-id] :as context}
   {:keys [last-order-max-discount] :as condition}]
  (< (event/discount-last-order site-id site-shopper-id) last-order-max-discount))

(defmethod valid? :max-redemptions-per-day
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [max-redemptions-per-day] :as condition}]
  (let [the-promo (promo/find-by-uuid (-> offer :reward :promo-id))
        count (redemption/count-in-period (:uuid the-promo)
                                          :start (t/minus (t/now)
                                                          (t/days 1))
                                          :end (t/now))]
    (< count max-redemptions-per-day)))

(defmethod valid? :max-discount-per-day
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [max-discount-per-day] :as condition}]
  (let [the-promo (promo/find-by-uuid (-> offer :reward :promo-id))
        redeemed (redemption/total-discounts (:uuid the-promo))]
    (< redeemed max-discount-per-day)))

(defmethod valid? :shopper-device-type
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [shopper-device-type] :as condition}]
  (let [ua (:user-agent session)]
    (cond
     (= :all shopper-device-type) true
     (= :phone shopper-device-type) (#{:phone} (:device ua))
     (= :tablet shopper-device-type) (#{:tablet} (:device ua))
     (= :desktop shopper-device-type) (#{:desktop} (:device ua))
     :else false)))

(defmethod valid? :num-visits-in-period
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [num-visits period-in-days] :as condition}]
  (let [visits (event/count-shopper-events-by-days site-shopper-id
                                                   "session-start"
                                                   period-in-days)]
    (>= visits num-visits)))

(defmethod valid? :referer-domain
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [referer-domain] :as condition}]
  (let [d (get-in session [:initial-request-headers "referer"])]
    (.contains d referer-domain)))

(defmethod valid? :items-in-cart
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [items-in-cart] :as condition}]
  (let [c (:last-cart-event session)
        how-many (apply + (map :quantity (:cart-items c)))]
    (>= how-many items-in-cart)))

(defmethod valid? :cart-value
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [cart-value] :as condition}]
  (let [c (:last-cart-event session)
        total (apply + (map :subtotal (:cart-items c)))]
    (>= total cart-value)))

(defmethod valid? :shipping-zipcode
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [shipping-zipcode] :as condition}]
  (let [z (get-in session [:last-cart-event :shipping-postcode])]
    (if z (.contains z shipping-zipcode))))

(defmethod valid? :billing-zipcode
  [{:keys [session site-id site-shopper-id offer] :as context}
   {:keys [billing-zipcode] :as condition}]
  (let [z (get-in session [:last-cart-event :billing-postcode])]
    (if z (.contains z billing-zipcode))))

