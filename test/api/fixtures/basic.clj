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
                   :site_url "http://sekrit.com"))))
