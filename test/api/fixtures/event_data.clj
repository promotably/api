(ns api.fixtures.event-data
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
   [clojure.data.json :as json]
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clj-time.coerce :as c]
   [api.q-fix :refer :all]))

(def shopper-id (java.util.UUID/randomUUID))
(def session-id (java.util.UUID/randomUUID))
(def site-uuid (java.util.UUID/randomUUID))

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
                   :uuid site-uuid
                   :site_code "site1"
                   :api_secret (java.util.UUID/randomUUID)
                   :site_url "http://sekrit.com"))
      (table :events
          (fixture :offer-1
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :session_id session-id
                   :type "cartview"
                   :data {:billing-state "",
                          :shipping-country "US",
                          :event-name :trackcartview,
                          :visitor-id "61b02c78-1cda-4636-9367-77c1c36da643",
                          :shipping-city "",
                          :billing-postcode "",
                          :shipping-state "",
                          :billing-email "colin@promotably.com",
                          :cart-items
                          [{:total 120,
                            :subtotal 120,
                            :quantity 6,
                            :variation "",
                            :variation-id "",
                            :categories [""],
                            :title "Widget",
                            :sku "W100"}],
                          :billing-city "",
                          :shipping-address-1 "",
                          :site-id "b7374bc9-274a-4164-b87f-d2a4f454665a",
                          :shipping-email "",
                          :billing-country "US",
                          :shipping-postcode "",
                          :billing-address-1 ""})
          (fixture :offer-2
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :session_id session-id
                   :type "productview"
                   :data {:sku "W100",
                          :title "Widget",
                          :auth
                          {:headers [],
                           :qs-fields
                           ["event-name"
                            "sku"
                            "product-name"
                            "short-description"
                            "site-id"
                            "user-id"],
                           :timestamp "20141130T094421Z",
                           :signature "Dq/LMz/XA8e+SKIba7Nez6Drii4=",
                           :scheme "hmac-sha1"},
                          :modified-at "2014-11-29T15:10:57.000-00:00"
                          :user-id "1",
                          :short-description "",
                          :event-name :trackproductview}))))


