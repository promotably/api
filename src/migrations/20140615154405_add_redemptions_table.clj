(ns migrations.20140615154405-add-redemptions-table
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140615154405."
  []
  (println "migrations.20140615154405-add-redemptions-table up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :redemptions
                            [:id "serial8 primary key not null"]
                            [:promo_id "int8 not null"]
                            [:shopper_id "varchar(255) not null"]
                            [:shopper_email "varchar(255)"]
                            [:created_at "timestamptz DEFAULT (now() at time zone 'utc') NOT NULL"])
     "CREATE INDEX idx_redemptions_promoId_shopperId ON redemptions ( promo_id, shopper_id )"
     "CREATE INDEX idx_redemptions_promoId_shopperEmail ON redemptions ( promo_id, shopper_email )"
     "CREATE INDEX idx_redemptions_promoId ON redemptions ( promo_id )"
     "ALTER TABLE ONLY promos ALTER COLUMN usage_limit_per_user SET DEFAULT NULL")))

(defn down
  "Migrates the database down from version 20140615154405."
  []
  (println "migrations.20140615154405-add-redemptions-table down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/drop-table-ddl :redemptions)
     "ALTER TABLE ONLY promos ALTER COLUMN usage_limit_per_user SET DEFAULT 0")))
