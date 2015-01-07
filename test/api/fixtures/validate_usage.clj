(ns api.fixtures.validate-usage
  (:refer-clojure :exclude [set load])
  (:require
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clj-time.coerce :as c]
   [clojure.java.jdbc :as jdbc]
   [api.fixtures.common :refer [default-account default-site]]
   [api.q-fix :refer :all]))

(def site-id (java.util.UUID/randomUUID))
(def promo-5-id (java.util.UUID/randomUUID))
(def promo-6-id (java.util.UUID/randomUUID))


(def fixture-set
  (set
   default-account
   default-site
   (table :promos
          (fixture :promo-5
                   :uuid promo-5-id
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
                   :uuid promo-6-id
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
                   :total_discounts 0))))
