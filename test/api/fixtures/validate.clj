(ns api.fixtures.validate
  (:refer-clojure :exclude [set load])
  (:require
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clj-time.coerce :as c]
   [api.q-fix :refer :all]))

(def fixture-set
  (set
   (table :accounts
          (fixture :account-1
                   :company_name "company name"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))
                   :account_id (java.util.UUID/randomUUID)))
   (table :sites
          (fixture :site-1
                   :account_id :account-1
                   :name "site-1"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))
                   :uuid (java.util.UUID/randomUUID)
                   :site_code "site1"
                   :api_secret (java.util.UUID/randomUUID)
                   :site_url "http://sekrit.com"))
   (table :promos
          (fixture :promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P1"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Description"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-1
                   :promo_id :promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2014-11-28 23:59:59"))))
   (table :promos
          (fixture :promo-2
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P2"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Description"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-2
                   :promo_id :promo-2
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2020-11-28 23:59:59"))))
   (table :promos
          (fixture :promo-3
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P3"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Description"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-3
                   :promo_id :promo-3
                   :uuid (java.util.UUID/randomUUID)
                   :type "times"
                   :start_time "00:00"
                   :end_time "00:00"))
   (table :promos
          (fixture :promo-4
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P4"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Description"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-4
                   :promo_id :promo-4
                   :uuid (java.util.UUID/randomUUID)
                   :type "times"
                   :start_time "00:00"
                   :end_time "23:59"))
   (table :promos
          (fixture :promo-5
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P5"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Description"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-5
                   :promo_id :promo-5
                   :uuid (java.util.UUID/randomUUID)
                   :type "usage-count"
                   :usage_count 0))
   (table :promos
          (fixture :promo-6
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P6"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Description"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-6
                   :promo_id :promo-6
                   :uuid (java.util.UUID/randomUUID)
                   :type "usage-count"
                   :usage_count java.lang.Integer/MAX_VALUE))
   (table :promos
          (fixture :promo-7
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P7"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Description"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-7
                   :promo_id :promo-7
                   :uuid (java.util.UUID/randomUUID)
                   :type "total-discounts"
                   :total_discounts java.lang.Integer/MAX_VALUE))
   (table :promos
          (fixture :promo-8
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P8"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Description"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-8
                   :promo_id :promo-8
                   :uuid (java.util.UUID/randomUUID)
                   :type "total-discounts"
                   :total_discounts 0))
   (table :offers
          (fixture :offer-1
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :promo_id :promo-1
                   :name "Easter Offer"
                   :code "E1"
                   :display_text "display text"
                   :active true
                   :dynamic true
                   :expiry_in_minutes 20
                   :presentation_type "lightbox"
                   :presentation_page "any"
                   :presentation_display_text "presentation text"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))))
