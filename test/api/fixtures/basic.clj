(ns api.fixtures.basic
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
                   :code "EASTER"
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
