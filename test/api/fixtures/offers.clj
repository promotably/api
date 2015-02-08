(ns api.fixtures.offers
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
   [clojure.data.json :as json]
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clj-time.coerce :as c]
   [api.fixtures.basic :as base]
   [api.q-fix :refer :all]))

(def site-2-id #uuid "2072f5d5-1d9a-49f3-8f06-8311088e8623")
(def site-3-id #uuid "2072f5d4-1d9a-49f3-8f06-8311088e8623")
(def site-4-id #uuid "22a37e2d-7f1c-4bce-9666-70b0c26de872")
(def minorder-site-id #uuid "34a37e2d-7f1c-4bce-9666-70b0c26de843")
(def maxorder-site-id #uuid "32a37e2d-7f1c-4bce-9666-70b0c26de873")

(def shopper-id #uuid "7f2fe574-974e-4f48-87fd-5ada3a4cb2bb")
(def site-shopper-id #uuid "001fd699-9d50-4b7c-af3b-3e022d379647")
(def session-id #uuid "95f1b8b2-a77b-4fec-a2fe-334f1afa2858")

(def shopper-2-id #uuid "3327a72b-d2e0-4fba-b872-d857c5453609")
(def site-shopper-2-id #uuid "5c171a4e-2528-4714-88d8-f7f3f9cab8df")
(def session-2-id #uuid "8fe0b04c-d4ee-4eda-a2fa-08ca8283f98d")

(def minorder-shopper-id #uuid "4327a72b-d2e0-4fba-b872-d857c5453609")
(def minorder-site-shopper-id #uuid "3c171a4e-2528-4714-88d8-f7f3f9cab8df")
(def minorder-session-id #uuid "1fe0b04c-d4ee-4eda-a2fa-08ca8283f98d")

(def fixture-set
  (set
   base/fixture-set
   (table :sites
          (fixture :site-2
                   :account_id :account-1
                   :name "Site 2"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))
                   :uuid site-2-id
                   :site_code "site2"
                   :api_secret (java.util.UUID/randomUUID)
                   :site_url "http://smnirven.com")
          (fixture :site-3
                   :account_id :account-1
                   :name "Site 3"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))
                   :uuid site-3-id
                   :site_code "site3"
                   :api_secret (java.util.UUID/randomUUID)
                   :site_url "http://bpromo.com")
          (fixture :site-4
                   :account_id :account-1
                   :name "Site 4"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))
                   :uuid site-4-id
                   :site_code "site4"
                   :api_secret (java.util.UUID/randomUUID)
                   :site_url "http://zombo.com")
          (fixture :site-min-order
                   :account_id :account-1
                   :name "Site Min Order"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))
                   :uuid minorder-site-id
                   :site_code "site-min-order"
                   :api_secret (java.util.UUID/randomUUID)
                   :site_url "http://minorder.com")
          (fixture :site-max-order
                   :account_id :account-1
                   :name "Site Max Order"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))
                   :uuid maxorder-site-id
                   :site_code "site-max-order"
                   :api_secret (java.util.UUID/randomUUID)
                   :site_url "http://maxorder.com"))
   (table :promos
          (fixture :site-2-promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-2
                   :code "EASTER PROMO FOR SITE 2"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Easter Coupon"
                   :seo_text "Best effing coupon evar"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now)))
          (fixture :site-2-expired-promo
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-2
                   :code "SITE 2 EXPIRED PROMO"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Expired promo"
                   :seo_text "Best effing coupon evar"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now)))
          (fixture :site-3-promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-3
                   :code "SITE 3 PROMO"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Easter Coupon"
                   :seo_text "Best effing coupon evar"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now)))
          (fixture :site-4-promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-4
                   :code "EASTER PROMO FOR SITE 4"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Easter Coupon"
                   :seo_text "Best effing coupon evar"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now)))
          (fixture :site-min-order-promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-min-order
                   :code "EASTER PROMO FOR SITE 4"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Easter Coupon"
                   :seo_text "Best effing coupon evar"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now)))
          (fixture :site-max-order-promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-max-order
                   :code "EASTER PROMO FOR SITE 4"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Easter Coupon"
                   :seo_text "Best effing coupon evar"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :offers
          (fixture :offer-1-with-date-condition
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-2
                   :promo_id :site-2-promo-1
                   :code "OFFER-VALID-DATES"
                   :name "NAME HERE"
                   :active true
                   :display_text "Book it, dano"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :html "<html></html>"
                   :css "body {}"
                   :theme "theme"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now)))
          (fixture :offer-with-expired-dates
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-2
                   :promo_id :site-2-promo-1
                   :code "OFFER-INVALID-DATES"
                   :name "THIS OFFER IS EXPIRED"
                   :active true
                   :display_text "YOU CANT HAVE THIS"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :html "<html></html>"
                   :css "body {}"
                   :theme "theme"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now)))
          (fixture :offer-1-with-cart-add-condition
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-3
                   :promo_id :site-3-promo-1
                   :code "OFFER-CART-ADD"
                   :name "SITE 3 OFFER 1"
                   :active true
                   :display_text "Book it, dano"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :html "<html></html>"
                   :css "body {}"
                   :theme "theme"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now)))
          (fixture :offer-1-with-min-order-condition
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-min-order
                   :promo_id :site-min-order-promo-1
                   :code "OFFER-MIN-ORDER"
                   :name "SITE 3 OFFER 1 W MIN ORDER"
                   :active true
                   :display_text "MIN ORDER OFFER"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :html "<html></html>"
                   :css "body {}"
                   :theme "theme"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now)))
          (fixture :offer-1-with-max-order-condition
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-max-order
                   :promo_id :site-max-order-promo-1
                   :code "OFFER-MAX-ORDER"
                   :name "SITE 3 OFFER 1 W MAX ORDER"
                   :active true
                   :display_text "MAX ORDER OFFER"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :html "<html></html>"
                   :css "body {}"
                   :theme "theme"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now)))
          (fixture :offer-1-with-product-views-condition
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-4
                   :promo_id :site-4-promo-1
                   :code "OFFER-PRODUCT-VIEWS-VALID"
                   :name "NAME HERE"
                   :active true
                   :display_text "Book it, dano"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :html "<html></html>"
                   :css "body {}"
                   :theme "theme"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now)))
          (fixture :offer-with-expired-promo
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-2
                   :promo_id :site-2-expired-promo
                   :code "OFFER-EXPIRED-PROMO"
                   :name "NAME HERE"
                   :active true
                   :display_text "Book it, dano"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :html "<html></html>"
                   :css "body {}"
                   :theme "theme"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now)))
          (fixture :offer-product-views-not-valid
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-4
                   :promo_id :site-4-promo-1
                   :code "OFFER-PRODUCT-VIEWS-INVALID"
                   :name "NAME HERE"
                   :active true
                   :display_text "Book it, dano"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :html "<html></html>"
                   :css "body {}"
                   :theme "theme"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-expired
                   :promo_id :site-2-expired-promo
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (t/minus (t/now) (t/days 10)))
                   :end_date (c/to-sql-time (t/minus (t/now) (t/days 3)))))
   (table :offer_conditions
          (fixture :offer-1-date-condition
                   :uuid (java.util.UUID/randomUUID)
                   :offer_id :offer-1-with-date-condition
                   :type "dates"
                   :start_date (c/to-sql-time (t/minus (t/now) (t/days 1)))
                   :end_date (c/to-sql-time (t/plus (t/now) (t/days 1))))
          (fixture :offer-with-expired-dates-condition
                   :uuid (java.util.UUID/randomUUID)
                   :offer_id :offer-with-expired-dates
                   :type "dates"
                   :start_date (c/to-sql-time (t/minus (t/now) (t/days 14)))
                   :end_date (c/to-sql-time (t/minus (t/now) (t/days 1))))
          (fixture :offer-with-cart-adds-condition
                   :uuid (java.util.UUID/randomUUID)
                   :offer_id :offer-1-with-cart-add-condition
                   :type "num-cart-adds-in-period"
                   :num_cart_adds 2
                   :period_in_days 5
                   :start_date (c/to-sql-time (t/minus (t/now) (t/days 14)))
                   :end_date (c/to-sql-time (t/minus (t/now) (t/days 1))))
          (fixture :offer-with-min-orders-condition
                   :uuid (java.util.UUID/randomUUID)
                   :offer_id :offer-1-with-min-order-condition
                   :type "min-orders-in-period"
                   :num_orders 0
                   :period_in_days (* 2 365))
          (fixture :offer-with-max-of-one-in-one-orders-condition
                   :uuid (java.util.UUID/randomUUID)
                   :offer_id :offer-1-with-max-order-condition
                   :type "max-orders-in-period"
                   :num_orders 2
                   :period_in_days (* 2 365))
          (fixture :offer-condition-product-views
                   :uuid (java.util.UUID/randomUUID)
                   :offer_id :offer-1-with-product-views-condition
                   :type "product-views"
                   :product_views 2
                   :period_in_days 2)
          (fixture :offer-condition-product-views-invalid
                   :uuid (java.util.UUID/randomUUID)
                   :offer_id :offer-product-views-not-valid
                   :type "product-views"
                   :product_views 20
                   :period_in_days 2))
   (table :events
          (fixture :event-pa-1
                   :site_id site-3-id
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "productadd"
                   :created_at (c/to-sql-date (t/minus (t/now) (t/days 2)))
                   :data {:quantity 1,
                          :site-id (str site-3-id),
                          :shopper-id (str shopper-id),
                          :sku "T100",
                          :variation "",
                          :event-name "productadd",
                          :session-id (str session-id)})
          (fixture :event-pa-2
                   :site_id site-3-id
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "productadd"
                   :created_at (c/to-sql-date (t/minus (t/now) (t/days 2)))
                   :data {:quantity 1,
                          :site-id (str site-3-id),
                          :shopper-id (str shopper-id),
                          :sku "T100",
                          :variation "",
                          :event-name "productadd",
                          :session-id (str session-id)})
          (fixture :event-minorder-1
                   :site_id minorder-site-id
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id minorder-shopper-id
                   :site_shopper_id minorder-site-shopper-id
                   :session_id minorder-session-id
                   :type "thankyou"
                   :created_at (c/to-sql-date (t/minus (t/now) (t/days 2)))
                   :data {:site-id (str minorder-site-id),
                          :shopper-id (str minorder-shopper-id),
                          :event-name "thankyou",
                          :session-id (str minorder-session-id)
                          :order-date "2014-12-14 09:04:27",
                          :applied-coupons [{:discount "28", :code "p4"}],
                          :shipping-methods [{:cost "0", :method "free_shipping"}],
                          :tax "0",
                          :shipping-address "Colin,Steele,Suite 1450,,Dallas,VA,75219,US",
                          :cart-items
                          [{:quantity 3,
                            :variation "",
                            :variation-id "0",
                            :categories [""],
                            :title "WIDGET",
                            :sku "W100",
                            :subtotal 60,
                            :total 60}
                           {:quantity 1,
                            :variation "",
                            :variation-id "0",
                            :categories [""],
                            :title "THNEED",
                            :sku "T100",
                            :subtotal 10,
                            :total 10}],
                          :total "112",
                          :order-id "16",
                          :billing-address "Colin,Steele,Suite 1450,,Dallas,VA,75219,US,",
                          :shipping "0",
                          :discount "28"})
          (fixture :event-minorder-2
                   :site_id maxorder-site-id
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id minorder-shopper-id
                   :site_shopper_id minorder-site-shopper-id
                   :session_id minorder-session-id
                   :type "thankyou"
                   :created_at (c/to-sql-date (t/minus (t/now) (t/days 2)))
                   :data {:site-id (str maxorder-site-id),
                          :shopper-id (str minorder-shopper-id ),
                          :session-id (str minorder-session-id)
                          :event-name :thankyou,
                          :order-date "2014-12-14 09:04:27",
                          :applied-coupons [{:discount "28", :code "p4"}],
                          :shipping-methods [{:cost "0", :method "free_shipping"}],
                          :tax "0",
                          :shipping-address "Colin,Steele,Suite 1450,,Dallas,VA,75219,US",
                          :cart-items
                          [{:quantity 3,
                            :variation "",
                            :variation-id "0",
                            :categories [""],
                            :title "WIDGET",
                            :sku "W100",
                            :subtotal 60,
                            :total 60}
                           {:quantity 1,
                            :variation "",
                            :variation-id "0",
                            :categories [""],
                            :title "THNEED",
                            :sku "T100",
                            :subtotal 10,
                            :total 10}],
                          :total "112",
                          :order-id "16",
                          :billing-address "Colin,Steele,Suite 1450,,Dallas,VA,75219,US,",
                          :shipping "0",
                          :discount "28"})
          (fixture :event-pv-valid-1
                   :site_id site-4-id
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-2-id
                   :site_shopper_id site-shopper-2-id
                   :session_id session-2-id
                   :type "productview"
                   :created_at (c/to-sql-time (t/now))
                   :data {:quantity 1,
                          :site-id (str site-4-id),
                          :shopper-id (str shopper-2-id),
                          :sku "T100",
                          :event-name "productview",
                          :session-id (str session-2-id)})
          (fixture :event-pv-valid-2
                   :site_id site-4-id
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-2-id
                   :site_shopper_id site-shopper-2-id
                   :session_id session-2-id
                   :type "productview"
                   :created_at (c/to-sql-time (t/now))
                   :data {:quantity 1,
                          :site-id (str site-4-id),
                          :shopper-id (str shopper-2-id),
                          :sku "T101",
                          :event-name "productview",
                          :session-id (str session-2-id)}))))
