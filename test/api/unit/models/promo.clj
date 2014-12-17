(ns api.unit.models.promo
  (:require [api.models.promo :refer :all]
            [api.models.redemption :as rd]
            [clj-time.coerce :refer [to-sql-date to-date]]
            [clj-time.core :refer [now plus minus months]]
            [midje.sweet :refer :all]))

(fact "Inactive promos are declared invalid"
  (let [the-promo {:active false}
        output (valid? the-promo anything)]
    output => (just [anything ["That promo is currently inactive"]])))

(fact "Promos that haven't started yet are declared invalid"
  (let [the-promo {:active true
                   :conditions [{:type :dates
                                 :start-date (to-date (plus (now) (months 1)))
                                 :end-date (to-date (plus (now) (months 3)))}]}
        output (valid? the-promo {})]
    output => (contains [(contains {:errors (just ["The coupon has expired."])}) anything])))

(fact "Promos that have expired are declared invalid"
  (let [the-promo {:active true
                   :conditions [{:type :dates
                                 :start-date (to-date (minus (now) (months 3)))
                                 :end-date (to-date (minus (now) (months 1)))}]}
        output (valid? the-promo {})]
    output => (contains [(contains {:errors (just ["The coupon has expired."])}) anything])))

;; DB OUTPUT
;;{:description asdkfjkdfd, :promo_conditions ({:usage_count nil, :start_time nil, :category_ids nil, :not_product_ids nil, :item_count nil, :type dates, :total_discounts nil, :end_time nil, :min_order_value nil, :end_date #inst "2015-01-31T05:00:00.000-00:00", :item_value nil, :start_date #inst "2014-11-11T05:00:00.000-00:00", :id 1, :product_ids nil, :combo_product_ids nil, :uuid #uuid "9816d8bc-47f0-4d8c-a1e1-6e9e9d7ff89c", :not_categories_ids nil, :promo_id 1}), :name 10% off, :reward_amount 10.0000M, :linked_products (), :updated_at #inst "2014-11-11T12:47:16.259495000-00:00", :active true, :id 1, :code TENOFF, :site_id 1, :exceptions nil, :reward_type percent, :uuid #uuid "e2d6e3ed-1406-4d5d-86a8-24c64ce118d4", :reward_applied_to cart, :reward_tax after-tax, :created_at #inst "2014-11-11T12:47:16.259495000-00:00"}

;;DB-TO-PROMO OUTPUT
;;{:description "asdkfjkdfd", :updated-at #inst "2014-11-11T12:47:16.259495000-00:00", :reward-applied-to :cart, :name "10% off", :reward-tax :after-tax, :reward-amount 10.0000M, :linked-products [], :conditions [{:start-date #inst "2014-11-11T05:00:00.000-00:00", :type :dates, :end-date #inst "2015-01-31T05:00:00.000-00:00"}], :active true, :code "TENOFF", :reward-type :percent, :exceptions nil, :uuid #uuid "e2d6e3ed-1406-4d5d-86a8-24c64ce118d4", :created-at #inst "2014-11-11T12:47:16.259495000-00:00"}

(fact "db-to-promo has expected output format"
  (let [promo-uuid (java.util.UUID/randomUUID)
        db-result {:description "adsfdf"
                   :updated_at (to-sql-date (now))
                   :created_at (to-sql-date (now))
                   :reward_amount 10.0000M
                   :active true
                   :id 1
                   :code "TENOFF"
                   :site_id 1
                   :reward_type "percent"
                   :reward_applied_to "cart"
                   :reward_tax "after-tax"
                   :exceptions nil
                   :uuid promo-uuid
                   :promo_conditions (seq [{:type "dates"
                                            :start_date (to-sql-date (now))
                                            :end_date (to-sql-date (now))}])}]
    (db-to-promo db-result) => (contains {:description "adsfdf"
                                          :uuid promo-uuid
                                          :reward-amount 10.0000M
                                          :conditions (just [(contains {:type :dates})])})))

(def the-context {:billing-state "VA",
                  :shipping-country "US",
                  :shipping-city "Dallas",
                  :site {},
                  :billing-postcode "75219",
                  :shopper-email "colin@promotably.com",
                  :applied-coupons ["p4"],
                  :product-ids-on-sale [],
                  :shipping-state "VA",
                  :billing-email "colin@promotably.com",
                  :billing-city "Dallas",
                  :shipping-address-1 "Suite 1450",
                  :code "p4",
                  :cart-contents [{:quantity 3,
                                   :line-tax 0,
                                   :product-categories [],
                                   :line-subtotal-tax 0,
                                   :sku "W100",
                                   :variation-id "",
                                   :variation "",
                                   :line-subtotal 60,
                                   :line-total 60}
                                  {:quantity 1,
                                   :line-tax 0,
                                   :product-categories [],
                                   :line-subtotal-tax 0,
                                   :sku "T100",
                                   :variation-id "",
                                   :variation "",
                                   :line-subtotal 10,
                                   :line-total 10}],
                  :shipping-email "",
                  :billing-country "US",
                  :shipping-postcode "75219",
                  :billing-address-1 "Suite 1450"})

(fact "Calculate percent off cart"
  (let [promo {:description "Description",
               :reward-applied-to :cart,
               :seo-text "SEO",
               :reward-tax :after-tax,
               :reward-amount 20,
               :linked-products [],
               :conditions [{:end-time "23:59", :type :times, :start-time "00:00"}],
               :active true,
               :code "P4",
               :reward-type :percent}
        context the-context
        [amount return-context errors] (discount-amount promo context nil)]
    amount => "14.0000"))

(fact "Calculate percent off matching items"
  (let [promo {:description "Description",
               :reward-applied-to :all-items,
               :seo-text "SEO",
               :reward-tax :after-tax,
               :reward-amount 20,
               :linked-products [],
               :conditions [{:type :product-ids :product-ids ["W100"]}],
               :active true,
               :code "P4",
               :reward-type :percent}
        [context errors] (valid? promo the-context)
        [amount return-context errors] (discount-amount promo context errors)]
    amount => "12.0000"))

(fact "Calculate percent off matching items"
  (let [promo {:description "Description",
               :reward-applied-to :all-items,
               :seo-text "SEO",
               :reward-tax :after-tax,
               :reward-amount 20,
               :linked-products [],
               :conditions [{:type :product-ids :product-ids ["T100"]}],
               :active true,
               :code "P4",
               :reward-type :percent}
        [context errors] (valid? promo the-context)
        [amount return-context errors] (discount-amount promo context errors)]
    amount => "2.0000"))

(fact "Calculate percent off NO matching items"
  (let [promo {:description "Description",
               :reward-applied-to :all-items,
               :seo-text "SEO",
               :reward-tax :after-tax,
               :reward-amount 20,
               :linked-products [],
               :conditions [{:type :product-ids :product-ids ["FOO"]}],
               :active true,
               :code "P4",
               :reward-type :percent}
        [context errors] (valid? promo the-context)
        [amount return-context errors] (discount-amount promo context errors)]
    amount => "0"))

(fact "Calculate dollar off one matching item"
  (let [promo {:description "Description",
               :reward-applied-to :all-items,
               :seo-text "SEO",
               :reward-tax :after-tax,
               :reward-amount 10,
               :linked-products [],
               :conditions [{:type :product-ids :product-ids ["T100"]}],
               :active true,
               :code "P4",
               :reward-type :dollar}
        [context errors] (valid? promo the-context)
        [amount return-context errors] (discount-amount promo context errors)]
    amount => "10.0000"))

(fact "Calculate dollar off multiple matching items"
  (let [promo {:description "Description",
               :reward-applied-to :all-items,
               :seo-text "SEO",
               :reward-tax :after-tax,
               :reward-amount 10,
               :linked-products [],
               :conditions [{:type :product-ids :product-ids ["W100"]}],
               :active true,
               :code "P4",
               :reward-type :dollar}
        [context errors] (valid? promo the-context)
        [amount return-context errors] (discount-amount promo context errors)]
    amount => "30.0000"))

(fact "Calculate dollar off one matching item, discount exceeds price"
  (let [promo {:description "Description",
               :reward-applied-to :all-items,
               :seo-text "SEO",
               :reward-tax :after-tax,
               :reward-amount 100,
               :linked-products [],
               :conditions [{:type :product-ids :product-ids ["T100"]}],
               :active true,
               :code "P4",
               :reward-type :dollar}
        [context errors] (valid? promo the-context)
        [amount return-context errors] (discount-amount promo context errors)]
    amount => "10.0000"))

