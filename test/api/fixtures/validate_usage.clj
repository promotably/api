(ns api.fixtures.validate-usage
  (:refer-clojure :exclude [set load])
  (:require
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clj-time.coerce :as c]
   [clojure.java.jdbc :as jdbc]
   [api.fixtures.common :refer [default-account default-site]]
   [api.q-fix :refer :all]))

(def fixture-set
  (set
   default-account
   default-site
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
                   :description "This promo has a usage-count condition"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-5
                   :promo_id :promo-5
                   :uuid (java.util.UUID/randomUUID)
                   :type "usage-count"
                   :usage_count 2))
   (table :promo_redemptions
          (fixture :pr-p5
                   :event_id (java.util.UUID/randomUUID)
                   :site_id api.fixtures.common/site-uuid
                   :order_id "1234"
                   :promo_code "P5"
                   :promo_id :promo-5
                   :discount 12.56
                   :shopper_id (java.util.UUID/randomUUID)
                   :session_id (java.util.UUID/randomUUID)))

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
                   :description "This promo has a usage-count condition"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-6
                   :promo_id :promo-6
                   :uuid (java.util.UUID/randomUUID)
                   :type "usage-count"
                   :usage_count java.lang.Integer/MAX_VALUE))
   (table :promo_redemptions
          (fixture :pr-p6
                   :event_id (java.util.UUID/randomUUID)
                   :site_id api.fixtures.common/site-uuid
                   :order_id "1234"
                   :promo_code "P6"
                   :promo_id :promo-6
                   :discount 12.56
                   :shopper_id (java.util.UUID/randomUUID)
                   :session_id (java.util.UUID/randomUUID)))

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
                   :description "This promo has a total-discounts condition"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-7
                   :promo_id :promo-7
                   :uuid (java.util.UUID/randomUUID)
                   :type "total-discounts"
                   :total_discounts java.lang.Integer/MAX_VALUE))
   (table :promo_redemptions
          (fixture :pr-p7
                   :event_id (java.util.UUID/randomUUID)
                   :site_id api.fixtures.common/site-uuid
                   :order_id "1234"
                   :promo_code "P7"
                   :promo_id :promo-7
                   :discount 121.56
                   :shopper_id (java.util.UUID/randomUUID)
                   :session_id (java.util.UUID/randomUUID)))

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
                   :description "This promo has a total-discounts condition"
                   :seo_text "SEO"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :promo_conditions
          (fixture :pc-8
                   :promo_id :promo-8
                   :uuid (java.util.UUID/randomUUID)
                   :type "total-discounts"
                   :total_discounts 10.50))
   (table :promo_redemptions
          (fixture :pr-p8
                   :event_id (java.util.UUID/randomUUID)
                   :site_id api.fixtures.common/site-uuid
                   :order_id "1234"
                   :promo_code "P8"
                   :promo_id :promo-8
                   :discount 3.25
                   :shopper_id (java.util.UUID/randomUUID)
                   :session_id (java.util.UUID/randomUUID))
          (fixture :pr-p8-2
                   :event_id (java.util.UUID/randomUUID)
                   :site_id api.fixtures.common/site-uuid
                   :order_id "12345"
                   :promo_code "P8"
                   :promo_id :promo-8
                   :discount 8.25
                   :shopper_id (java.util.UUID/randomUUID)
                   :session_id (java.util.UUID/randomUUID)))))
