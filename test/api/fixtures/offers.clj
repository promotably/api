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
(def shopper-id #uuid "7f2fe574-974e-4f48-87fd-5ada3a4cb2bb")
(def site-shopper-id #uuid "001fd699-9d50-4b7c-af3b-3e022d379647")
(def session-id #uuid "95f1b8b2-a77b-4fec-a2fe-334f1afa2858")


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
                   :site_url "http://bpromo.com"))
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
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now))))
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
                   :end_date (c/to-sql-time (t/minus (t/now) (t/days 1)))))
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
                          :session-id (str session-id)}))))
