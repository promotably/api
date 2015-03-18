(ns migrations.20150108205331-drop-old-redemptions
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150108205331."
  []
  (println "migrations.20150108205331-drop-old-redemptions up...")
  (jdbc/db-do-commands @db/$db-config
                       (jdbc/drop-table-ddl :redemptions)))

(defn down
  "Migrates the database down from version 20150108205331."
  []
  (println "migrations.20150108205331-drop-old-redemptions down...")
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
     "CREATE INDEX idx_redemptions_promoId ON redemptions ( promo_id )")))
