(ns migrations.20140603232017-even-moar-fields-for-promos
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140603232017."
  []
  (println "migrations.20140603232017-even-moar-fields-for-promos up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos ADD COLUMN amount NUMERIC(16,4) DEFAULT 0,
                         ADD COLUMN apply_before_tax BOOLEAN DEFAULT false,
                         ADD COLUMN free_shipping BOOLEAN DEFAULT false,
                         ADD COLUMN minimum_cart_amount NUMERIC(16,4) DEFAULT 0,
                         ADD COLUMN minimum_product_amount NUMERIC(16,4) DEFAULT 0,
                         ADD COLUMN usage_limit_per_user INTEGER DEFAULT 0")))

(defn down
  "Migrates the database down from version 20140603232017."
  []
  (println "migrations.20140603232017-even-moar-fields-for-promos down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos DROP COLUMN amount,
                         DROP COLUMN apply_before_tax,
                         DROP COLUMN free_shipping,
                         DROP COLUMN minimum_cart_amount,
                         DROP COLUMN minimum_product_amount,
                         DROP COLUMN usage_limit_per_user")))
