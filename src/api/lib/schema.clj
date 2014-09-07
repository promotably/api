(ns api.lib.schema
  (:require [schema.core :as s]))

;; Conditions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ConditionType
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
          :order-min-value))

(def Condition
  {(s/optional-key :id) s/Int
   (s/optional-key :promo-id) s/Int
   (s/required-key :type) ConditionType
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
   (s/optional-key :order-min-value) (s/maybe s/Num)})


;; Promos ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def BasePromo {(s/required-key :name) s/Str
                (s/required-key :code) s/Str
                (s/required-key :description) (s/maybe s/Str)
                (s/optional-key :active) s/Bool
                (s/required-key :reward-amount) (s/maybe s/Num)
                (s/required-key :reward-type) (s/maybe (s/enum :dollar :percent))
                (s/required-key :reward-tax) (s/maybe (s/enum :after-tax :before-tax))
                (s/required-key :reward-applied-to) (s/maybe
                                                     (s/enum :cart :all-items :one-item))
                (s/optional-key :exceptions) [s/Str]
                (s/required-key :conditions) [Condition]
                (s/optional-key :created-at) s/Inst
                (s/optional-key :updated-at) s/Inst})

(def OutboundPromo (merge BasePromo
                          {(s/required-key :id) s/Int
                           (s/required-key :site-id) s/Int
                           (s/required-key :uuid) s/Uuid}))

(def NewPromo (merge BasePromo
                     {(s/required-key :site-id) s/Uuid}))


