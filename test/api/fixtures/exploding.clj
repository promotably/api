(ns api.fixtures.exploding
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
   [clojure.data.json :as json]
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clj-time.coerce :as c]
   [api.fixtures.basic :as base]
   [api.fixtures.offers.html-css-theme :as offers-f-hct]
   [api.q-fix :refer :all]))

(def dynamic-site-id #uuid "9be8a905-498d-4a8e-ba50-397e2d5f5275")

(def shopper-id #uuid "7f2fe574-974e-4f48-87fd-5ada3a4cb2bb")
(def site-shopper-id #uuid "001fd699-9d50-4b7c-af3b-3e022d379647")
(def session-id #uuid "95f1b8b2-a77b-4fec-a2fe-334f1afa2858")

(def dynamic-site-shopper-id #uuid "ecdbdb74-84a1-42cf-adf3-561ff1cd2ba6")

(def fixture-set
  (set
   base/fixture-set
   (table :sites
          (fixture :site-dynamic
                   :account_id :account-1
                   :name "site-dynamic"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))
                   :site_id dynamic-site-id
                   :site_code "site-dynamic"
                   :api_secret (java.util.UUID/randomUUID)
                   :site_url "http://dynamic.com"))
   (table :promos
          (fixture :site-dynamic-promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-dynamic
                   :code "DYNAMIC PROMO"
                   :active true
                   :reward_amount 20
                   :reward_type "percent"
                   :reward_tax "after-tax"
                   :reward_applied_to "cart"
                   :description "Dynamic offer Coupon"
                   :seo_text "Best effing coupon evar"
                   :updated_at (c/to-sql-date (t/now))
                   :created_at (c/to-sql-date (t/now))))
   (table :offers
          (fixture :offer-dynamic
                   :uuid (java.util.UUID/randomUUID)
                   :site_id :site-dynamic
                   :dynamic true
                   :expiry_in_minutes 5
                   :promo_id :site-dynamic-promo-1
                   :code "DYNAMIC"
                   :name "NAME HERE"
                   :active true
                   :display_text "Book it, dano"
                   :presentation_type "lightbox"
                   :presentation_page "product-detail"
                   :created_at (c/to-sql-date (t/now))
                   :updated_at (c/to-sql-date (t/now))))))
