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
                   :site_url "http://smnirven.com"))
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
                   :end_date (c/to-sql-time (t/minus (t/now) (t/days 1)))))))
