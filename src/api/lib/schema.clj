(ns api.lib.schema
  (:require [schema.core :as s]))

;; Linked Products ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Linked
  {(s/optional-key :id) s/Int
   (s/optional-key :promo-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :url) s/Str
   (s/required-key :photo-url) s/Str
   (s/required-key :name) s/Str
   (s/required-key :original-price) s/Num
   (s/required-key :seo-copy) s/Str})

(def OutboundLinked (-> Linked
                        (dissoc (s/required-key :uuid))
                        (dissoc (s/optional-key :id))
                        (assoc (s/optional-key :created-at) s/Inst)
                        (assoc (s/optional-key :uuid) s/Uuid)
                        (assoc (s/optional-key :id) s/Int)))

;; Conditions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def PromoConditionType
  (s/enum :dates
          :times
          :usage-count
          :total-discounts
          :product-ids
          :category-ids
          :not-product-ids
          :not-category-ids
          :combo-product-ids
          :item-count
          :item-value
          :individual-use
          :min-order-value))

(def PromoCondition
  {(s/optional-key :id) s/Int
   (s/optional-key :promo-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :type) PromoConditionType
   (s/optional-key :start-date) (s/maybe org.joda.time.DateTime)
   (s/optional-key :end-date) (s/maybe org.joda.time.DateTime)
   (s/optional-key :start-time) (s/maybe org.joda.time.DateTime)
   (s/optional-key :end-time) (s/maybe org.joda.time.DateTime)
   (s/optional-key :usage-count) (s/maybe s/Int)
   (s/optional-key :total-discounts) (s/maybe s/Num)
   (s/optional-key :product-ids) [s/Str]
   (s/optional-key :product-categories) [s/Str]
   (s/optional-key :not-product-ids) [s/Str]
   (s/optional-key :not-product-categories) [s/Str]
   (s/optional-key :combo-product-ids) [s/Str]
   (s/optional-key :item-count) (s/maybe s/Int)
   (s/optional-key :item-value) (s/maybe s/Num)
   (s/optional-key :min-order-value) (s/maybe s/Num)})

(def DatabaseCondition
  {(s/optional-key :id) s/Int
   (s/optional-key :promo-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :type) s/Str
   (s/optional-key :start-date) (s/maybe java.sql.Date)
   (s/optional-key :end-date) (s/maybe java.sql.Date)
   (s/optional-key :start-time) (s/maybe java.sql.Timestamp)
   (s/optional-key :end-time) (s/maybe java.sql.Timestamp)
   (s/optional-key :usage-count) (s/maybe s/Int)
   (s/optional-key :total-discounts) (s/maybe s/Num)
   (s/optional-key :product-ids) [s/Str]
   (s/optional-key :product-categories) [s/Str]
   (s/optional-key :not-product-ids) [s/Str]
   (s/optional-key :not-product-categories) [s/Str]
   (s/optional-key :combo-product-ids) [s/Str]
   (s/optional-key :item-count) (s/maybe s/Int)
   (s/optional-key :item-value) (s/maybe s/Num)
   (s/optional-key :min-order-value) (s/maybe s/Num)})

(def InboundPromoCondition (-> PromoCondition
                          (dissoc (s/required-key :uuid))
                          (assoc (s/optional-key :start-date) org.joda.time.DateTime)
                          (assoc (s/optional-key :end-date) org.joda.time.DateTime)
                          (assoc (s/optional-key :start-time) org.joda.time.DateTime)
                          (assoc (s/optional-key :end-time) org.joda.time.DateTime)
                          (assoc (s/optional-key :uuid) s/Uuid)))

(def OutboundPromoCondition (-> PromoCondition
                           (dissoc (s/required-key :uuid))
                           (assoc (s/optional-key :start-date) (s/maybe java.util.Date))
                           (assoc (s/optional-key :end-date) (s/maybe java.util.Date))
                           (assoc (s/optional-key :start-time) (s/maybe java.util.Date))
                           (assoc (s/optional-key :end-time) (s/maybe java.util.Date))
                           (assoc (s/optional-key :uuid) s/Uuid)))

;; Products ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Product {(s/optional-key :seo-copy) s/Str
              (s/optional-key :original-price) s/Num
              (s/optional-key :name) s/Str
              (s/optional-key :photo-url) s/Str
              (s/optional-key :url) s/Str})

;; Promos ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def BasePromo {(s/required-key :name) s/Str
                (s/required-key :code) s/Str
                (s/required-key :description) (s/maybe s/Str)
                (s/optional-key :active) s/Bool
                (s/required-key :reward-amount) (s/maybe s/Num)
                (s/required-key :reward-type) (s/maybe (s/enum :dollar :percent))
                (s/required-key :reward-tax) (s/maybe (s/enum :after-tax :before-tax))
                (s/required-key :reward-applied-to) (s/maybe
                                                     (s/enum :cart
                                                             :all-items
                                                             :delivery
                                                             :one-item))
                (s/required-key :exceptions) (s/maybe (s/enum :sale-items))
                (s/required-key :conditions) [PromoCondition]
                (s/optional-key :created-at) s/Inst
                (s/optional-key :updated-at) s/Inst})

(def OutboundPromo (merge (dissoc BasePromo :conditions)
                          {(s/required-key :id) s/Int
                           (s/required-key :site-id) s/Int
                           (s/required-key :uuid) s/Uuid
                           (s/optional-key :linked-products) [OutboundLinked]
                           (s/optional-key :conditions) [OutboundPromoCondition]}))

(def NewPromo (merge BasePromo
                     {(s/required-key :site-id) s/Uuid
                      (s/optional-key :uuid) s/Uuid
                      (s/optional-key :linked-products) [Product]
                      (s/required-key :conditions) [InboundPromoCondition]}))

(def PromoLookup {(s/optional-key :site-id) s/Uuid})

;; Offers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def valid-types
  [:dates
   :times
   :minutes-since-last-offer
   :minutes-on-site
   :minutes-since-last-engagement
   :product-views
   :repeat-product-views
   :items-in-cart
   :shipping-zipcode
   :billing-zipcode
   :referer-domain
   :shopper-device-type
   :num-orders-in-period
   :num-lifetime-orders
   :last-order-total
   :last-order-item-count
   :last-order-includes-item-id])
(def valid-devices #{:mobile :desktop :all})
(def valid-reward-types #{:promo :dynamic-promo})
(def valid-presentation-types #{:lightbox :fly-in :fixed-div :inline :on-exit})
(def valid-presentation-page-types #{:product-detail :cart :checkout
                                     :search-results :any})
(def BasePresentation
  {(s/required-key :type) (apply s/enum (vec valid-presentation-types))
   (s/required-key :page) (apply s/enum (vec valid-presentation-page-types))
   (s/required-key :display-text) (s/maybe s/Str)})
(def Presentation
  (s/conditional #(= (:type %) :lightbox)
                 (merge BasePresentation {})
                 #(= (:type %) :fixed-div)
                 (merge BasePresentation {})
                 #(= (:type %) :inline)
                 (merge BasePresentation {})
                 #(= (:type %) :on-exit)
                 (merge BasePresentation {})
                 #(= (:type %) :fly-in)
                 (merge BasePresentation {})))
(def BaseReward
  {(s/required-key :type) (apply s/enum (vec valid-reward-types))})
(def Reward
  (s/conditional #(= (:type %) :dynamic-promo)
                 (merge BaseReward {:promo-id s/Uuid
                                    :expiry-in-minutes s/Int})
                 #(= (:type %) :promo)
                 (merge BaseReward {:promo-id s/Uuid})))
(def BaseOfferCondition
  {(s/required-key :type) (apply s/enum (vec valid-types))
   s/Keyword s/Any})
(def OfferCondition
  (s/conditional #(= (:type %) :dates)
                 (merge BaseOfferCondition {:start-date s/Inst
                                            :end-date s/Inst})

                 #(= (:type %) :times)
                 (merge BaseOfferCondition {:start-time s/Inst
                                            :end-time s/Inst})

                 #(= (:type %) :minutes-since-last-offer)
                 (merge BaseOfferCondition {:minutes-since-last-offer s/Int})

                 #(= (:type %) :minutes-on-site)
                 (merge BaseOfferCondition {:minutes-on-site s/Int})

                 #(= (:type %) :minutes-since-last-engagement)
                 (merge BaseOfferCondition {:minutes-since-last-engagement s/Int})

                 #(= (:type %) :product-views)
                 (merge BaseOfferCondition {:product-views s/Int})

                 #(= (:type %) :repeat-product-views)
                 (merge BaseOfferCondition {:repeat-product-views s/Int})

                 #(= (:type %) :items-in-cart)
                 (merge BaseOfferCondition {:items-in-cart s/Int})

                 #(= (:type %) :shipping-zipcode)
                 (merge BaseOfferCondition {:shipping-zipcode s/Str})

                 #(= (:type %) :billing-zipcode)
                 (merge BaseOfferCondition {:billing-zipcode s/Str})

                 #(= (:type %) :referer-domain)
                 (merge BaseOfferCondition {:referer-domain s/Str})

                 #(= (:type %) :shopper-device-type)
                 (merge BaseOfferCondition {:shopper-device-type
                                            (apply s/enum (vec valid-devices))})

                 #(= (:type %) :num-orders-in-period)
                 (merge BaseOfferCondition {:num-orders s/Int
                                                   :period-in-days s/Int})

                 #(= (:type %) :num-lifetime-orders)
                 (merge BaseOfferCondition {:num-lifetime-orders s/Int})

                 #(= (:type %) :last-order-total)
                 (merge BaseOfferCondition {:last-order-total s/Num})

                 #(= (:type %) :last-order-item-count)
                 (merge BaseOfferCondition {:last-order-item-count s/Int})

                 #(= (:type %) :last-order-includes-item-id)
                 (merge BaseOfferCondition {:last-order-includes-item-id s/Str})))

(def InboundOfferCondition
  (-> OfferCondition
      (dissoc (s/required-key :uuid))
      (assoc (s/optional-key :start-date) org.joda.time.DateTime)
      (assoc (s/optional-key :end-date) org.joda.time.DateTime)
      (assoc (s/optional-key :start-time) org.joda.time.DateTime)
      (assoc (s/optional-key :end-time) org.joda.time.DateTime)
      (assoc (s/optional-key :uuid) s/Uuid)))

(def OutboundOfferCondition
  (-> OfferCondition
      (dissoc (s/required-key :uuid))
      (assoc (s/optional-key :start-date) (s/maybe java.util.Date))
      (assoc (s/optional-key :end-date) (s/maybe java.util.Date))
      (assoc (s/optional-key :start-time) (s/maybe java.util.Date))
      (assoc (s/optional-key :end-time) (s/maybe java.util.Date))
      (assoc (s/optional-key :uuid) s/Uuid)))

(def DatabaseOfferCondition
  {(s/optional-key :id) s/Int
   (s/optional-key :offer-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :type) s/Str
   (s/optional-key :created-at) (s/maybe java.sql.Timestamp)
   (s/optional-key :start-date) (s/maybe java.sql.Date)
   (s/optional-key :end-date) (s/maybe java.sql.Date)
   (s/optional-key :start-time) (s/maybe java.sql.Timestamp)
   (s/optional-key :end-time) (s/maybe java.sql.Timestamp)
   (s/optional-key :minutes-since-last-offer) (s/maybe s/Int)
   (s/optional-key :minutes-on-site) (s/maybe s/Int)
   (s/optional-key :minutes-since-last-engagement) (s/maybe s/Int)
   (s/optional-key :product-views) (s/maybe s/Int)
   (s/optional-key :repeat-product-views) (s/maybe s/Int)
   (s/optional-key :items-in-cart) (s/maybe s/Int)
   (s/optional-key :shipping-zipcode) (s/maybe s/Str)
   (s/optional-key :billing-zipcode) (s/maybe s/Str)
   (s/optional-key :referer-domain) (s/maybe s/Str)
   (s/optional-key :shopper-device-type) (s/maybe (s/enum :mobile :desktop :all))
   (s/optional-key :num-orders) (s/maybe s/Int)
   (s/optional-key :period-in-days) (s/maybe s/Int)
   (s/optional-key :num-lifetime-orders) (s/maybe s/Int)
   (s/optional-key :last-order-total) (s/maybe s/Num)
   (s/optional-key :last-order-item-count) (s/maybe s/Int)
   (s/optional-key :last-order-includes-item-id) [s/Str]})

(def BaseOffer
  {(s/required-key :site-id) s/Uuid
   (s/required-key :code) s/Str
   (s/required-key :name) s/Str
   (s/required-key :active) s/Bool
   (s/required-key :display-text) (s/maybe s/Str)
   (s/required-key :reward) Reward
   (s/required-key :conditions) [OfferCondition]
   (s/required-key :presentation) Presentation})

(def OutboundOffer (merge (dissoc BaseOffer :conditions)
                          {(s/required-key :id) s/Int
                           (s/required-key :site-id) s/Int
                           (s/required-key :created-at) s/Inst
                           (s/required-key :updated-at) s/Inst
                           (s/required-key :uuid) s/Uuid
                           (s/optional-key :conditions) [OutboundOfferCondition]}))

(def NewOffer (merge (dissoc BaseOffer
                             (s/required-key :conditions)
                             (s/required-key :active))
                     {(s/required-key :site-id) s/Uuid
                      (s/optional-key :uuid) s/Uuid
                      (s/optional-key :active) s/Bool
                      (s/optional-key :created-at) s/Inst
                      (s/optional-key :updated-at) s/Inst
                      (s/required-key :conditions) [InboundOfferCondition]}))

(def OfferLookup {(s/optional-key :site-id) s/Uuid})

