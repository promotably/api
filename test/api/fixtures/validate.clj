(ns api.fixtures.validate
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
          (fixture :promo-9
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P9"
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
          (fixture :pc-9
                   :promo_id :promo-9
                   :uuid (java.util.UUID/randomUUID)
                   :type "product-ids"
                   :product_ids #(.createArrayOf (jdbc/get-connection %)
                                                 "varchar"
                                                 (into-array String ["W100"]))))
   (table :promos
          (fixture :promo-10
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P10"
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
          (fixture :pc-10
                   :promo_id :promo-10
                   :uuid (java.util.UUID/randomUUID)
                   :type "product-ids"
                   :product_ids #(.createArrayOf (jdbc/get-connection %)
                                                 "varchar"
                                                 (into-array String ["X99"]))))
   (table :promos
          (fixture :promo-11
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P11"
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
          (fixture :pc-11
                   :promo_id :promo-11
                   :uuid (java.util.UUID/randomUUID)
                   :type "category-ids"
                   :category_ids #(.createArrayOf (jdbc/get-connection %)
                                                  "varchar"
                                                  (into-array String ["1"]))))
   (table :promos
          (fixture :promo-12
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P12"
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
          (fixture :pc-12
                   :promo_id :promo-12
                   :uuid (java.util.UUID/randomUUID)
                   :type "category-ids"
                   :category_ids #(.createArrayOf (jdbc/get-connection %)
                                                  "varchar"
                                                  (into-array String ["X"]))))
   (table :promos
          (fixture :promo-13
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P13"
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
          (fixture :pc-13
                   :promo_id :promo-13
                   :uuid (java.util.UUID/randomUUID)
                   :type "not-category-ids"
                   :not_category_ids #(.createArrayOf (jdbc/get-connection %)
                                                      "varchar"
                                                      (into-array String ["1"]))))
   (table :promos
          (fixture :promo-14
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P14"
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
          (fixture :pc-14
                   :promo_id :promo-14
                   :uuid (java.util.UUID/randomUUID)
                   :type "not-category-ids"
                   :not_category_ids #(.createArrayOf (jdbc/get-connection %)
                                                      "varchar"
                                                      (into-array String ["X"]))))
   (table :promos
          (fixture :promo-15
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P15"
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
          (fixture :pc-15
                   :promo_id :promo-15
                   :uuid (java.util.UUID/randomUUID)
                   :type "not-product-ids"
                   :not_product_ids #(.createArrayOf (jdbc/get-connection %)
                                                     "varchar"
                                                     (into-array String ["X"]))))
   (table :promos
          (fixture :promo-16
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P16"
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
          (fixture :pc-16
                   :promo_id :promo-16
                   :uuid (java.util.UUID/randomUUID)
                   :type "not-product-ids"
                   :not_product_ids #(.createArrayOf (jdbc/get-connection %)
                                                     "varchar"
                                                     (into-array String ["W100"]))))
   (table :promos
          (fixture :promo-17
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P17"
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
          (fixture :pc-17
                   :promo_id :promo-17
                   :uuid (java.util.UUID/randomUUID)
                   :type "combo-product-ids"
                   :combo_product_ids #(.createArrayOf (jdbc/get-connection %)
                                                       "varchar"
                                                       (into-array String ["X99" "W100"]))))
   (table :promos
          (fixture :promo-18
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P18"
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
          (fixture :pc-18
                   :promo_id :promo-18
                   :uuid (java.util.UUID/randomUUID)
                   :type "item-count"
                   :item_count 10))
   (table :promos
          (fixture :promo-19
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P19"
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
          (fixture :pc-19
                   :promo_id :promo-19
                   :uuid (java.util.UUID/randomUUID)
                   :type "item-value"
                   :item_value 150))
   (table :promos
          (fixture :promo-20
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P20"
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
          (fixture :pc-20
                   :promo_id :promo-20
                   :uuid (java.util.UUID/randomUUID)
                   :type "min-order-value"
                   :min_order_value 150))
   (table :promos
          (fixture :promo-21
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "P21"
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
          (fixture :pc-21
                   :promo_id :promo-21
                   :uuid (java.util.UUID/randomUUID)
                   :type "individual-use"))

   (table :promos
          (fixture :promo-22
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "PM22"
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
          (fixture :pc-22-1
                   :promo_id :promo-22
                   :uuid (java.util.UUID/randomUUID)
                   :type "individual-use")
          (fixture :pc-22-2
                   :promo_id :promo-22
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2020-11-28 23:59:59")))
          (fixture :pc-22-3
                   :promo_id :promo-22
                   :uuid (java.util.UUID/randomUUID)
                   :type "min-order-value"
                   :min_order_value 150))
   (table :promos
          (fixture :promo-23
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "PM23"
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
          (fixture :pc-23-1
                   :promo_id :promo-23
                   :uuid (java.util.UUID/randomUUID)
                   :type "times"
                   :start_time "00:00"
                   :end_time "00:00")
          (fixture :pc-23-2
                   :promo_id :promo-23
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2020-11-28 23:59:59"))))
   (table :promos
          (fixture :promo-24
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "PM24"
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
          (fixture :pc-24-1
                   :promo_id :promo-24
                   :uuid (java.util.UUID/randomUUID)
                   :type "times"
                   :start_time "00:00"
                   :end_time "23:59")
          (fixture :pc-24-2
                   :promo_id :promo-24
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2020-11-28 23:59:59"))))
(table :promos
          (fixture :promo-25
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "PM25"
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
          (fixture :pc-25-1
                   :promo_id :promo-25
                   :uuid (java.util.UUID/randomUUID)
                   :type "product-ids"
                   :product_ids #(.createArrayOf (jdbc/get-connection %)
                                                 "varchar"
                                                 (into-array String ["X99"])))
          (fixture :pc-25-2
                   :promo_id :promo-25
                   :uuid (java.util.UUID/randomUUID)
                   :type "times"
                   :start_time "00:00"
                   :end_time "23:59")
          (fixture :pc-25-3
                   :promo_id :promo-25
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2020-11-28 23:59:59"))))
   (table :promos
          (fixture :promo-26
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "PM26"
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
          (fixture :pc-26-1
                   :promo_id :promo-26
                   :uuid (java.util.UUID/randomUUID)
                   :type "product-ids"
                   :product_ids #(.createArrayOf (jdbc/get-connection %)
                                                 "varchar"
                                                 (into-array String ["W100"])))
          (fixture :pc-26-2
                   :promo_id :promo-26
                   :uuid (java.util.UUID/randomUUID)
                   :type "times"
                   :start_time "00:00"
                   :end_time "23:59")
          (fixture :pc-26-3
                   :promo_id :promo-26
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2020-11-28 23:59:59"))))
   (table :promos
          (fixture :promo-27
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-1
                   :code "PM27"
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
          (fixture :pc-27-1
                   :promo_id :promo-27
                   :uuid (java.util.UUID/randomUUID)
                   :type "combo-product-ids"
                   :combo_product_ids #(.createArrayOf (jdbc/get-connection %)
                                                       "varchar"
                                                       (into-array String ["X99" "W100"])))
          (fixture :pc-27-2
                   :promo_id :promo-27
                   :uuid (java.util.UUID/randomUUID)
                   :type "times"
                   :start_time "00:00"
                   :end_time "23:59")
          (fixture :pc-27-3
                   :promo_id :promo-27
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2020-11-28 23:59:59"))))
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
