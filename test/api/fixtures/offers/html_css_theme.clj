(ns api.fixtures.offers.html-css-theme
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
    [clojure.data.json :as json]
    [clj-time.format :as f]
    [clj-time.core :as t]
    [clj-time.coerce :as c]
    [api.fixtures.basic :as base]
    [api.q-fix :refer :all]))


(def site-id #uuid "9595f5d5-1d9a-49f3-8f06-8311088e8623")
(def shopper-id #uuid "9595e574-974e-4f48-87fd-5ada3a4cb2bb")
(def site-shopper-id #uuid "9595d699-9d50-4b7c-af3b-3e022d379647")
(def promo-id #uuid "ef875d3a-3f7e-4244-879c-4381428b97b4")

(defn no-html-offer []
  {:site-id (str site-id)
   :name "No HTML Offer"
   :code "NO-HTML-OFFER"
   :display-text "display text"
   :reward {:promo-id (str promo-id)
            :type :dynamic-promo
            :expiry-in-minutes 10}
   :presentation {:type :lightbox
                  :page :any
                  :display-text "presentation text"}
   :conditions [{:type "dates"
                 :start-date "2014-11-27T05:00:00Z"
                 :end-date "2014-11-29T04:59:59Z"}
                {:type :minutes-since-last-offer
                 :minutes-since-last-offer 10}]
   :css "body {}"
   :theme "theme"})

(def fixture-set
  (set
    (table :sites
           (fixture :html-css-theme-site
                    :account_id :account-1
                    :name "html-css-theme Site"
                    :updated_at (c/to-sql-date (t/now))
                    :created_at (c/to-sql-date (t/now))
                    :site_id site-id
                    :site_code "html-css-theme-site"
                    :api_secret (java.util.UUID/randomUUID)
                    :site_url "http://html-css-theme-site.com"))
    (table :promos
           (fixture :html-css-theme-promo
                    :uuid promo-id
                    :site_id :html-css-theme-site
                    :code "HTML CSS THEME CODE"
                    :active true
                    :reward_amount 20
                    :reward_type "percent"
                    :reward_tax "after-tax"
                    :reward_applied_to "cart"
                    :description "HTML CSS COUPON"
                    :seo_text "Best effing coupon evar"
                    :updated_at (c/to-sql-date (t/now))
                    :created_at (c/to-sql-date (t/now))))
    (table :offers
           (fixture :html-css-theme-offer
                    :uuid (java.util.UUID/randomUUID)
                    :site_id :html-css-theme-site
                    :promo_id :html-css-theme-promo
                    :code "HTML-CSS-THEME-OFFER"
                    :name "NAME HERE"
                    :active true
                    :display_text "Book it, dano"
                    :presentation_type "lightbox"
                    :presentation_page "product-detail"
                    :css "body {}"
                    :theme "theme"
                    :created_at (c/to-sql-date (t/now))
                    :updated_at (c/to-sql-date (t/now))))
    (table :promo_conditions
           (fixture :html-css-theme-promo-condition
                    :promo_id :html-css-theme-promo
                    :uuid (java.util.UUID/randomUUID)
                    :type "dates"
                    :start_date (c/to-sql-time (t/minus (t/now) (t/days 10)))
                    :end_date (c/to-sql-time (t/minus (t/now) (t/days 3)))))
    (table :offer_conditions
           (fixture :html-css-theme-offer-condition
                    :uuid (java.util.UUID/randomUUID)
                    :offer_id :html-css-theme-offer
                    :type "dates"
                    :start_date (c/to-sql-time (t/minus (t/now) (t/days 1)))
                    :end_date (c/to-sql-time (t/plus (t/now) (t/days 1)))))))
