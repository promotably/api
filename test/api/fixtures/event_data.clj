(ns api.fixtures.event-data
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
   [clojure.data.json :as json]
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
      (table :events
          (fixture :offer-1
                   :site_id :site-1
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id (java.util.UUID/randomUUID)
                   :session_id (java.util.UUID/randomUUID)
                   :type "some-event"
                   :data {:foo 12}))))
