(ns migrations.20140622165018-change-promo-defaults
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140622165018."
  []
  (println "migrations.20140622165018-change-promo-defaults up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos ALTER COLUMN minimum_cart_amount SET DEFAULT null"
     "ALTER TABLE promos ALTER COLUMN minimum_product_amount SET DEFAULT null"
     "ALTER TABLE promos ALTER COLUMN usage_limit_per_user SET DEFAULT null")))

(defn down
  "Migrates the database down from version 20140622165018."
  []
  (println "migrations.20140622165018-change-promo-defaults down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos ALTER COLUMN minimum_cart_amount SET DEFAULT 0"
     "ALTER TABLE promos ALTER COLUMN minimum_product_amount SET DEFAULT 0"
     "ALTER TABLE promos ALTER COLUMN usage_limit_per_user SET DEFAULT 0")))
