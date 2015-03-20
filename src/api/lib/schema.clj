(ns api.lib.schema
  (:require [schema.core :as s])
  (:import [java.util UUID]))

(defn shape-to-spec
  "Given a model (a map) and a spec map, returns a map that consists of
  the keys from the spec map and the values produced by calling the
  associated functions of the spec with the model as their only
  argument."
  [model spec]
  (reduce-kv (fn [a k v]
               (assoc a k (v model))) {} spec))

;;;;;;;;;
;;
;; Here be "specs." Specs are maps of {:response-key model-value-fn}
;; They are passed to the shape-to-spec function along with the model (a map).
;; THe model-value-fn takes the model as it's argument and produces
;; the value for the corresponding response key.
;;
;;;;;;;;

(def site-spec
  {:site-id (fn [site] ; uuid coerced to string
              (str (:site-id site)))
   :site-code :site-code
   :site-name :name
   :site-url :site-url
   :api-secret (fn [site] ; uuid coerced to string
                 (str (:api-secret site)))
   :country :country
   :timezone :timezone
   :currency :currency
   :language :language})

(def account-spec
  {:account-id (fn [account] ; uuid coerced to string
                 (str (:account-id account)))
   :company-name :company-name
   :sites (fn [account] ; hard-coding a vector response here
            (let [sites (:sites account)]
              (cond
               (empty? sites) []
               (empty? (first sites)) []
               (nil? (:site-id (first sites))) []
               :else (mapv #(shape-to-spec % site-spec) sites))))})

(def user-spec
  {:user-id (fn [user] ; uuid coerced to string
              (str (:user-id user)))
   :first-name :first-name
   :last-name :last-name
   :email :email
   :has-password (fn [user] ; boolean
                   (not (nil? (:password user))))
   :phone :phone
   :job-title :job-title
   :accounts (fn [user] ; hard-cording a vector response here
               (let [accounts (:accounts user)]
                 (cond
                  (empty? accounts) []
                  (empty? (first accounts)) []
                  (nil? (:account-id (first accounts))) []
                  :else (mapv #(shape-to-spec % account-spec) accounts))))})

(def inbound-site-spec
  {:site-id (fn [site]
              (when-let [site-id (:site-id site)]
                (if (string? site-id)
                  (UUID/fromString site-id)
                  site-id)))
   :account-id (fn [site]
                 (when-let [account-id (:account-id site)]
                   (if (string? account-id)
                     (UUID/fromString account-id)
                     account-id)))
   :user-id (fn [site]
              (when-let [user-id (:user-id site)]
                (if (string? user-id)
                  (UUID/fromString user-id)
                  user-id)))
   :site-code :site-code
   :name :site-name
   :site-url :site-url
   :api-secret :api-secret
   :country :country
   :timezone :timezone
   :currency :currency
   :language :language})

(def inbound-account-spec
  {:account-id (fn [account]
                 (when-let [account-id (:account-id account)]
                   (if (string? account-id)
                     (UUID/fromString account-id)
                     account-id)))
   :company-name :company-name
   :user-id (fn [account]
              (when-let [user-id (:user-id account)]
                (if (string? user-id)
                  (UUID/fromString user-id)
                  user-id)))})

(def inbound-user-spec
  {:username :username
   :user-id (fn [user]
              (when-let [user-id (:user-id user)]
                (if (string? user-id)
                  (UUID/fromString user-id)
                  user-id)))
   :email :email
   :password :password
   :user-social-id :user-social-id
   :phone :phone
   :first-name :first-name
   :last-name :last-name
   :job-title :job-title
   :account-id (fn [user]
                 (when-let [account-id (:account-id user)]
                   (if (string? account-id)
                     (UUID/fromString account-id)
                     account-id)))})

;; Events ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def EventType (s/enum :productview
                       :productadd
                       :cartupdate
                       :cartview
                       :checkout
                       :thankyou
                       :offershown
                       :shopper-qualified-offers
                       :offer-made))

;; TODO: WIP
(def Auth
  {(s/required-key :scheme) s/Str
   (s/required-key :signature) s/Str
   (s/required-key :timestamp) s/Str
   (s/required-key :qs-fields) (s/maybe [s/Str])
   (s/required-key :headers) (s/maybe [s/Str])})

(def BaseEvent
  {(s/required-key :event-name) EventType
   (s/required-key :auth) Auth
   (s/optional-key :user-id) s/Str
   (s/required-key :site) s/Any
   (s/optional-key :shopper-id) (s/maybe s/Uuid)
   (s/optional-key :site-shopper-id) (s/maybe s/Uuid)
   (s/required-key :control-group) s/Bool})

(def CartItem
  {:sku s/Str
   :title s/Str
   :categories [s/Str]
   :variation-id s/Str
   :variation s/Str
   :quantity s/Int
   (s/optional-key :subtotal) (s/maybe s/Num)
   (s/optional-key :total) (s/maybe s/Num)})

(def LineItem
  (-> CartItem
      (dissoc (s/optional-key :subtotal))
      (dissoc (s/optional-key :total))
      (assoc :subtotal s/Num)
      (assoc :total s/Num)))

(def AppliedCoupon
  {(s/required-key :code) s/Str
   (s/required-key :discount) s/Str})

(def ShippingMethod
  {(s/required-key :method) s/Str
   (s/required-key :cost) s/Str})

(defmacro def-event
  [the-event base-event & body]
  `(def ~the-event
     (s/conditional #(= (:event-name %) :offershown)
                    (-> ~base-event
                        (dissoc (s/required-key :auth))
                        (merge
                         {(s/optional-key :seconds-remaining) (s/maybe s/Num)
                          (s/required-key :offer-id) (s/maybe s/Str)}))
                    #(= (:event-name %) :productview)
                    (merge ~base-event
                           {(s/required-key :sku) s/Str
                            (s/optional-key :title) (s/maybe s/Str)
                            (s/optional-key :offer-id) (s/maybe s/Str)
                            (s/optional-key :item-count) (s/maybe s/Str)
                            (s/optional-key :description) (s/maybe s/Str)
                            (s/optional-key :short-description) (s/maybe s/Str)
                            (s/optional-key :modified-at) (s/maybe s/Inst)
                            (s/optional-key :variation) (s/maybe s/Str)
                            (s/optional-key :applied-coupons) (s/maybe [AppliedCoupon])
                            (s/optional-key :shipping-methods) (s/maybe [ShippingMethod])
                            (s/optional-key :billing-address-1) (s/maybe s/Str)
                            (s/optional-key :billing-city) (s/maybe s/Str)
                            (s/optional-key :billing-state) (s/maybe s/Str)
                            (s/optional-key :billing-country) (s/maybe s/Str)
                            (s/optional-key :billing-postcode) (s/maybe s/Str)
                            (s/optional-key :billing-email) (s/maybe s/Str)
                            (s/optional-key :shipping-address-1) (s/maybe s/Str)
                            (s/optional-key :shipping-city) (s/maybe s/Str)
                            (s/optional-key :shipping-state) (s/maybe s/Str)
                            (s/optional-key :shipping-country) (s/maybe s/Str)
                            (s/optional-key :shipping-postcode) (s/maybe s/Str)
                            (s/optional-key :shipping-email) (s/maybe s/Str)
                            (s/optional-key :cart-items) [CartItem]})
                    #(= (:event-name %) :productadd)
                    (merge ~base-event
                           {(s/required-key :sku) s/Str
                            (s/optional-key :item-count) (s/maybe s/Str)
                            (s/optional-key :offer-id) (s/maybe s/Str)
                            (s/optional-key :category-id) (s/maybe s/Str)
                            (s/optional-key :quantity) s/Int
                            (s/optional-key :variation) (s/maybe s/Str)
                            (s/optional-key :applied-coupons) (s/maybe [AppliedCoupon])
                            (s/optional-key :shipping-methods) (s/maybe [ShippingMethod])
                            (s/optional-key :billing-address-1) (s/maybe s/Str)
                            (s/optional-key :billing-city) (s/maybe s/Str)
                            (s/optional-key :billing-state) (s/maybe s/Str)
                            (s/optional-key :billing-country) (s/maybe s/Str)
                            (s/optional-key :billing-postcode) (s/maybe s/Str)
                            (s/optional-key :billing-email) (s/maybe s/Str)
                            (s/optional-key :shipping-address-1) (s/maybe s/Str)
                            (s/optional-key :shipping-city) (s/maybe s/Str)
                            (s/optional-key :shipping-state) (s/maybe s/Str)
                            (s/optional-key :shipping-country) (s/maybe s/Str)
                            (s/optional-key :shipping-postcode) (s/maybe s/Str)
                            (s/optional-key :shipping-email) (s/maybe s/Str)
                            (s/optional-key :cart-items) [CartItem]})
                    #(= (:event-name %) :cartupdate)
                    (merge ~base-event
                           {(s/optional-key :applied-coupons) (s/maybe [AppliedCoupon])
                            (s/optional-key :item-count) (s/maybe s/Str)
                            (s/optional-key :offer-id) (s/maybe s/Str)
                            (s/optional-key :shipping-methods) (s/maybe [ShippingMethod])
                            (s/optional-key :billing-address-1) (s/maybe s/Str)
                            (s/optional-key :billing-city) (s/maybe s/Str)
                            (s/optional-key :billing-state) (s/maybe s/Str)
                            (s/optional-key :billing-country) (s/maybe s/Str)
                            (s/optional-key :billing-postcode) (s/maybe s/Str)
                            (s/optional-key :billing-email) (s/maybe s/Str)
                            (s/optional-key :shipping-address-1) (s/maybe s/Str)
                            (s/optional-key :shipping-city) (s/maybe s/Str)
                            (s/optional-key :shipping-state) (s/maybe s/Str)
                            (s/optional-key :shipping-country) (s/maybe s/Str)
                            (s/optional-key :shipping-postcode) (s/maybe s/Str)
                            (s/optional-key :shipping-email) (s/maybe s/Str)
                            (s/optional-key :cart-items) [CartItem]})
                    #(= (:event-name %) :cartview)
                    (merge ~base-event
                           {(s/optional-key :applied-coupons) (s/maybe [AppliedCoupon])
                            (s/optional-key :item-count) (s/maybe s/Str)
                            (s/optional-key :offer-id) (s/maybe s/Str)
                            (s/optional-key :shipping-methods) (s/maybe [ShippingMethod])
                            (s/optional-key :billing-address-1) (s/maybe s/Str)
                            (s/optional-key :billing-city) (s/maybe s/Str)
                            (s/optional-key :billing-state) (s/maybe s/Str)
                            (s/optional-key :billing-country) (s/maybe s/Str)
                            (s/optional-key :billing-postcode) (s/maybe s/Str)
                            (s/optional-key :billing-email) (s/maybe s/Str)
                            (s/optional-key :shipping-address-1) (s/maybe s/Str)
                            (s/optional-key :shipping-city) (s/maybe s/Str)
                            (s/optional-key :shipping-state) (s/maybe s/Str)
                            (s/optional-key :shipping-country) (s/maybe s/Str)
                            (s/optional-key :shipping-postcode) (s/maybe s/Str)
                            (s/optional-key :shipping-email) (s/maybe s/Str)
                            (s/required-key :cart-items) [CartItem]})
                    #(= (:event-name %) :checkout)
                    (merge ~base-event
                           {(s/optional-key :billing-address-1) (s/maybe s/Str)
                            (s/optional-key :item-count) (s/maybe s/Str)
                            (s/optional-key :offer-id) (s/maybe s/Str)
                            (s/optional-key :billing-city) (s/maybe s/Str)
                            (s/optional-key :billing-state) (s/maybe s/Str)
                            (s/optional-key :billing-country) (s/maybe s/Str)
                            (s/optional-key :billing-postcode) (s/maybe s/Str)
                            (s/optional-key :billing-email) (s/maybe s/Str)
                            (s/optional-key :shipping-address-1) (s/maybe s/Str)
                            (s/optional-key :shipping-city) (s/maybe s/Str)
                            (s/optional-key :shipping-state) (s/maybe s/Str)
                            (s/optional-key :shipping-country) (s/maybe s/Str)
                            (s/optional-key :shipping-postcode) (s/maybe s/Str)
                            (s/optional-key :shipping-email) (s/maybe s/Str)
                            (s/optional-key :applied-coupons) (s/maybe [AppliedCoupon])
                            (s/optional-key :shipping-methods) (s/maybe [ShippingMethod])
                            (s/required-key :cart-items) [CartItem]})
                    #(= (:event-name %) :thankyou)
                    (merge ~base-event
                           {(s/required-key :order-id) (s/maybe s/Str)
                            (s/optional-key :offer-id) (s/maybe s/Str)
                            (s/required-key :order-date) (s/maybe s/Str)
                            (s/optional-key :item-count) (s/maybe s/Str)
                            (s/optional-key :billing-address) (s/maybe s/Str)
                            (s/optional-key :billing-address-1) (s/maybe s/Str)
                            (s/optional-key :billing-city) (s/maybe s/Str)
                            (s/optional-key :billing-state) (s/maybe s/Str)
                            (s/optional-key :billing-country) (s/maybe s/Str)
                            (s/optional-key :billing-postcode) (s/maybe s/Str)
                            (s/optional-key :billing-email) (s/maybe s/Str)
                            (s/optional-key :shipping-address) (s/maybe s/Str)
                            (s/optional-key :shipping-address-1) (s/maybe s/Str)
                            (s/optional-key :shipping-city) (s/maybe s/Str)
                            (s/optional-key :shipping-state) (s/maybe s/Str)
                            (s/optional-key :shipping-country) (s/maybe s/Str)
                            (s/optional-key :shipping-postcode) (s/maybe s/Str)
                            (s/optional-key :shipping-email) (s/maybe s/Str)
                            (s/optional-key :shopper-email) (s/maybe s/Str)
                            (s/optional-key :total) (s/maybe s/Str)
                            (s/optional-key :tax) (s/maybe s/Str)
                            (s/optional-key :shipping) (s/maybe s/Str)
                            (s/optional-key :discount) (s/maybe s/Str)
                            (s/optional-key :applied-coupons) (s/maybe [AppliedCoupon])
                            (s/optional-key :shipping-methods) (s/maybe [ShippingMethod])
                            (s/required-key :cart-items) [LineItem]}))))

(def-event InboundEvent BaseEvent)
(def-event OutboundEvent (-> BaseEvent
                             (assoc (s/required-key :site-id) s/Uuid)
                             (assoc (s/required-key :session-id) s/Uuid)
                             (dissoc (s/required-key :site)
                                     (s/required-key :auth))))

;; Linked Products ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;; Conditions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO:  Do we need to support a condition that states "The maximum number of individual items this coupon can apply to when using product discounts. Absence of this condition = apply to all qualifying items in cart."?

(def PromoConditionType
  (s/enum :dates
          :times
          :usage-count
          :daily-usage-count
          :total-discounts
          :daily-total-discounts
          :product-ids
          :category-ids
          :not-product-ids
          :not-category-ids
          :combo-product-ids
          :item-count
          :item-value
          :individual-use
          :no-sale-items
          :min-order-value))

(def PromoCondition
  {(s/optional-key :id) s/Int
   (s/optional-key :promo-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :type) PromoConditionType
   (s/optional-key :start-date) (s/maybe org.joda.time.DateTime)
   (s/optional-key :end-date) (s/maybe org.joda.time.DateTime)
   (s/optional-key :start-time) (s/maybe s/Str)
   (s/optional-key :end-time) (s/maybe s/Str)
   (s/optional-key :usage-count) (s/maybe s/Int)
   (s/optional-key :total-discounts) (s/maybe s/Num)
   (s/optional-key :product-ids) [s/Str]
   (s/optional-key :category-ids) [s/Str]
   (s/optional-key :not-product-ids) [s/Str]
   (s/optional-key :not-category-ids) [s/Str]
   (s/optional-key :combo-product-ids) [s/Str]
   (s/optional-key :item-count) (s/maybe s/Int)
   (s/optional-key :item-value) (s/maybe s/Num)
   (s/optional-key :min-order-value) (s/maybe s/Num)})

(def DatabaseCondition
  {(s/optional-key :id) s/Int
   (s/optional-key :promo-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :type) s/Str
   (s/optional-key :start-date) (s/maybe java.sql.Timestamp)
   (s/optional-key :end-date) (s/maybe java.sql.Timestamp)
   (s/optional-key :start-time) (s/maybe s/Str)
   (s/optional-key :end-time) (s/maybe s/Str)
   (s/optional-key :usage-count) (s/maybe s/Int)
   (s/optional-key :total-discounts) (s/maybe s/Num)
   (s/optional-key :product-ids) [s/Str]
   (s/optional-key :category-ids) [s/Str]
   (s/optional-key :not-product-ids) [s/Str]
   (s/optional-key :not-category-ids) [s/Str]
   (s/optional-key :combo-product-ids) [s/Str]
   (s/optional-key :item-count) (s/maybe s/Int)
   (s/optional-key :item-value) (s/maybe s/Num)
   (s/optional-key :min-order-value) (s/maybe s/Num)})

(def InboundPromoCondition (-> PromoCondition
                          (dissoc (s/required-key :uuid))
                          (assoc (s/optional-key :start-date) org.joda.time.DateTime)
                          (assoc (s/optional-key :end-date) org.joda.time.DateTime)
                          (assoc (s/optional-key :start-time) s/Str)
                          (assoc (s/optional-key :end-time) s/Str)
                          (assoc (s/optional-key :uuid) s/Uuid)))

(def OutboundPromoCondition (-> PromoCondition
                                (dissoc (s/required-key :uuid))
                                (assoc (s/optional-key :start-date) (s/maybe java.util.Date))
                                (assoc (s/optional-key :end-date) (s/maybe java.util.Date))
                                (assoc (s/optional-key :start-time) (s/maybe s/Str))
                                (assoc (s/optional-key :end-time) (s/maybe s/Str))
                                (assoc (s/optional-key :uuid) s/Uuid)))

;; Products ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Product {(s/optional-key :seo-copy) s/Str
              (s/optional-key :original-price) s/Num
              (s/optional-key :name) s/Str
              (s/optional-key :photo-url) s/Str
              (s/optional-key :url) s/Str})

;; Promos ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def BasePromo {(s/required-key :code) s/Str
                (s/required-key :description) (s/maybe s/Str)
                (s/optional-key :seo-text) (s/maybe s/Str)
                (s/optional-key :active) s/Bool
                (s/required-key :reward-amount) (s/maybe s/Num)
                (s/required-key :reward-type) (s/maybe (s/enum :fixed :percent))
                (s/required-key :reward-tax) (s/maybe (s/enum :after-tax :before-tax))
                (s/required-key :reward-applied-to) (s/maybe
                                                     (s/enum :cart
                                                             :matching-items
                                                             :delivery))
                (s/required-key :conditions) [PromoCondition]
                (s/optional-key :created-at) s/Inst
                (s/optional-key :updated-at) s/Inst})

(def OutboundPromo (merge (dissoc BasePromo :conditions)
                          {(s/required-key :uuid) s/Uuid
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
   :days-since-last-offer
   :minutes-on-site
   :minutes-since-last-engagement
   :product-views
   :repeat-product-views
   :items-in-cart
   :cart-value
   :shipping-zipcode
   :billing-zipcode
   :referer-domain
   :shopper-device-type
   :min-orders-in-period
   :max-orders-in-period
   :num-visits-in-period
   :num-cart-adds-in-period
   :num-lifetime-orders
   :last-order-total
   :last-order-item-count
   :last-order-max-discount
   :max-redemptions-per-day
   :max-discount-per-day
   :last-order-includes-item-id
   :last-order-value])
(def valid-devices #{:desktop :phone :tablet :all})
(def valid-reward-types #{:promo :dynamic-promo})
(def valid-presentation-types #{:lightbox :fly-in :fixed-div :inline :on-exit})
(def valid-presentation-page-types #{:product-detail :cart :checkout
                                     :search-results :any})
(def BasePresentation
  {(s/required-key :type) (apply s/enum (vec valid-presentation-types))
   (s/required-key :page) (apply s/enum (vec valid-presentation-page-types))
   (s/required-key :html) (s/maybe s/Str)
   (s/required-key :css) (s/maybe s/Str)
   (s/required-key :theme) (s/maybe s/Str)
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


;; TODO: We need $/unit time and redemptions/unit time conditions.

(def OfferCondition
  (s/conditional #(= (:type %) :dates)
                 (merge BaseOfferCondition {:start-date s/Inst
                                            :end-date s/Inst})

                 #(= (:type %) :times)
                 (merge BaseOfferCondition {:start-time s/Str
                                            :end-time s/Str})

                 #(= (:type %) :max-redemptions-per-day)
                 (merge BaseOfferCondition {:max-redemptions-per-day s/Int})

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

                 #(= (:type %) :cart-value)
                 (merge BaseOfferCondition {:cart-value s/Num})

                 #(= (:type %) :shipping-zipcode)
                 (merge BaseOfferCondition {:shipping-zipcode s/Str})

                 #(= (:type %) :billing-zipcode)
                 (merge BaseOfferCondition {:billing-zipcode s/Str})

                 #(= (:type %) :referer-domain)
                 (merge BaseOfferCondition {:referer-domain s/Str})

                 #(= (:type %) :shopper-device-type)
                 (merge BaseOfferCondition {:shopper-device-type
                                            (apply s/enum (vec valid-devices))})

                 #(= (:type %) :min-orders-in-period)
                 (merge BaseOfferCondition {:num-orders s/Int
                                            :period-in-days s/Int})

                 #(= (:type %) :max-orders-in-period)
                 (merge BaseOfferCondition {:num-orders s/Int
                                            :period-in-days s/Int})

                 #(= (:type %) :num-visits-in-period)
                 (merge BaseOfferCondition {:num-visits s/Int
                                            :period-in-days s/Int})

                 #(= (:type %) :num-cart-adds-in-period)
                 (merge BaseOfferCondition {:num-cart-adds s/Int
                                            :period-in-days s/Int})

                 #(= (:type %) :num-lifetime-orders)
                 (merge BaseOfferCondition {:num-lifetime-orders s/Int})

                 #(= (:type %) :last-order-total)
                 (merge BaseOfferCondition {:last-order-total s/Num})

                 #(= (:type %) :last-order-item-count)
                 (merge BaseOfferCondition {:last-order-item-count s/Int})

                 #(= (:type %) :last-order-max-discount)
                 (merge BaseOfferCondition {:last-order-max-discount s/Num})

                 #(= (:type %) :max-discount-per-day)
                 (merge BaseOfferCondition {:max-discount-per-day s/Num})

                 #(= (:type %) :last-order-includes-item-id)
                 (merge BaseOfferCondition {:last-order-includes-item-id s/Str})

                 #(= (:type %) :last-order-value)
                 (merge BaseOfferCondition {:last-order-value s/Int})

                 #(= (:type %) :days-since-last-offer)
                 (merge BaseOfferCondition {:days-since-last-offer s/Int})))

(def InboundOfferCondition
  (-> OfferCondition
      (dissoc (s/required-key :uuid))
      (assoc (s/optional-key :start-date) org.joda.time.DateTime)
      (assoc (s/optional-key :end-date) org.joda.time.DateTime)
      (assoc (s/optional-key :start-time) s/Str)
      (assoc (s/optional-key :end-time) s/Str)
      (assoc (s/optional-key :uuid) s/Uuid)))

(def OutboundOfferCondition
  (-> OfferCondition
      (dissoc (s/required-key :uuid))
      (assoc (s/optional-key :start-date) (s/maybe java.util.Date))
      (assoc (s/optional-key :end-date) (s/maybe java.util.Date))
      (assoc (s/optional-key :start-time) (s/maybe s/Str))
      (assoc (s/optional-key :end-time) (s/maybe s/Str))
      (assoc (s/optional-key :uuid) s/Uuid)))

(def DatabaseOfferCondition
  {(s/optional-key :id) s/Int
   (s/optional-key :offer-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :type) s/Str
   (s/optional-key :created-at) (s/maybe java.sql.Timestamp)
   (s/optional-key :start-date) (s/maybe java.sql.Timestamp)
   (s/optional-key :end-date) (s/maybe java.sql.Timestamp)
   (s/optional-key :start-time) (s/maybe s/Str)
   (s/optional-key :end-time) (s/maybe s/Str)
   (s/optional-key :minutes-since-last-offer) (s/maybe s/Int)
   (s/optional-key :minutes-on-site) (s/maybe s/Int)
   (s/optional-key :minutes-since-last-engagement) (s/maybe s/Int)
   (s/optional-key :product-views) (s/maybe s/Int)
   (s/optional-key :repeat-product-views) (s/maybe s/Int)
   (s/optional-key :items-in-cart) (s/maybe s/Int)
   (s/optional-key :cart-value) (s/maybe s/Num)
   (s/optional-key :shipping-zipcode) (s/maybe s/Str)
   (s/optional-key :last-order-max-discount) (s/maybe s/Num)
   (s/optional-key :billing-zipcode) (s/maybe s/Str)
   (s/optional-key :referer-domain) (s/maybe s/Str)
   (s/optional-key :shopper-device-type) (s/maybe (s/enum :phone :tablet :desktop :all))
   (s/optional-key :num-orders) (s/maybe s/Int)
   (s/optional-key :period-in-days) (s/maybe s/Int)
   (s/optional-key :num-lifetime-orders) (s/maybe s/Int)
   (s/optional-key :last-order-total) (s/maybe s/Num)
   (s/optional-key :last-order-item-count) (s/maybe s/Int)
   (s/optional-key :last-order-includes-item-id) [s/Str]
   (s/optional-key :last-order-value) (s/maybe s/Int)
   (s/optional-key :num-visits) (s/maybe s/Int)
   (s/optional-key :max-discount-per-day) (s/maybe s/Num)
   (s/optional-key :max-redemptions-per-day) (s/maybe s/Num)
   (s/optional-key :num-cart-adds) (s/maybe s/Int)
   (s/optional-key :days-since-last-offer) (s/maybe s/Int)})

(def BaseOffer
  {(s/required-key :site-id) s/Uuid
   (s/required-key :code) s/Str
   (s/required-key :name) s/Str
   (s/required-key :active) s/Bool
   (s/required-key :reward) Reward
   (s/required-key :conditions) [OfferCondition]
   (s/required-key :presentation) Presentation
   (s/optional-key :html) s/Str
   (s/optional-key :css) s/Str
   (s/optional-key :theme) s/Str})

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
                      ;; :display-text needs to go away from here
                      (s/optional-key :display-text) (s/maybe s/Str)
                      (s/optional-key :uuid) s/Uuid
                      (s/optional-key :active) s/Bool
                      (s/optional-key :created-at) s/Inst
                      (s/optional-key :updated-at) s/Inst
                      (s/required-key :conditions) [InboundOfferCondition]}))

(def OfferLookup {(s/optional-key :site-id) s/Uuid})

;; Requests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def PromoValidationRequest
  {(s/required-key :site) s/Any
   (s/required-key :code) s/Str
   (s/optional-key :auth) Auth
   (s/optional-key :shopper-id) (s/maybe s/Str)
   (s/optional-key :site-shopper-id) (s/maybe s/Str)
   (s/required-key :shopper-email) s/Str
   (s/optional-key :applied-coupons) [s/Str]
   (s/optional-key :shipping-address-1) s/Str
   (s/optional-key :shipping-address-2) s/Str
   (s/optional-key :shipping-city) s/Str
   (s/optional-key :shipping-state) s/Str
   (s/optional-key :shipping-country) s/Str
   (s/optional-key :shipping-postcode) s/Str
   (s/optional-key :shipping-email) s/Str
   (s/optional-key :billing-address-1) s/Str
   (s/optional-key :billing-address-2) s/Str
   (s/optional-key :billing-city) s/Str
   (s/optional-key :billing-state) s/Str
   (s/optional-key :billing-country) s/Str
   (s/optional-key :billing-postcode) s/Str
   (s/optional-key :billing-email) s/Str
   (s/optional-key :cart-contents) [{(s/required-key :sku) s/Str
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
   (s/optional-key :product-ids-on-sale) [s/Int]
   (s/optional-key :selected-product-sku) s/Str})

(def RevenueMetric
  {(s/required-key :id) s/Num
   (s/required-key :site-id) s/Uuid
   (s/required-key :measurement-hour) (s/maybe org.joda.time.DateTime)
   (s/required-key :number-of-hours) s/Num
   (s/required-key :discount) s/Num
   (s/required-key :promotably-commission) s/Num
   (s/required-key :revenue) s/Num
   (s/required-key :less-commission-and-discount) s/Num
   (s/required-key :created-at) (s/maybe org.joda.time.DateTime)})

