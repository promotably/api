(ns api.fixtures.common
  (:refer-clojure :exclude [set load])
  (:require
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clj-time.coerce :as c]
   [api.q-fix :refer :all]))

(def site-uuid #uuid "5669de1d-cc61-4590-9ef6-5cab58369df2")
(def site-secret #uuid "bbc9da89-0960-457e-9e58-20ad00150c4d")
(def account-uuid #uuid "5c97fb43-0c4f-4f2f-9bcb-829fd6b1b720")

(def default-account (table :accounts
                            (fixture :account-1
                                     :company_name "company name"
                                     :updated_at (c/to-sql-date (t/now))
                                     :created_at (c/to-sql-date (t/now))
                                     :account_id account-uuid)))

(def default-site (table :sites
                         (fixture :site-1
                                  :account_id :account-1
                                  :name "site-1"
                                  :updated_at (c/to-sql-date (t/now))
                                  :created_at (c/to-sql-date (t/now))
                                  :uuid site-uuid
                                  :site_code "site1"
                                  :api_secret site-secret
                                  :site_url "http://sekrit.com")))
