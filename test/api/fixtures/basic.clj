(ns api.fixtures.basic
  (:refer-clojure :exclude [set load])
  (:require
   [clj-time.format :as f]
   [clj-time.core :as t]
   [api.fixtures.common :refer [default-account default-site]]
   [clj-time.coerce :as c]
   [api.q-fix :refer :all]))

(def fixture-set
  (set
   default-account
   default-site
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
   (table :promo_conditions
          (fixture :pc-1
                   :promo_id :promo-1
                   :uuid (java.util.UUID/randomUUID)
                   :type "dates"
                   :start_date (c/to-sql-time (c/from-string "2014-11-27 00:00:00"))
                   :end_date (c/to-sql-time (c/from-string "2014-11-28 23:59:59"))))
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
                   :created_at (c/to-sql-date (t/now))
                   :html "<html></html>"
                   :css "body {}"))))
