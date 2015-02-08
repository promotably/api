(ns migrations.20150208061435-alter-offer-conditions-many-more
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150208061435."
  []
  (println "migrations.20150208061435-alter-offer-conditions-many-more up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions ADD COLUMN last_order_value INTEGER,
                                                           ADD COLUMN max_redemptions_per_day INTEGER,
                                                           ADD COLUMN max_discount_per_day INTEGER")))

(defn down
  "Migrates the database down from version 20150208061435."
  []
  (println "migrations.20150208061435-alter-offer-conditions-many-more down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions DROP COLUMN last_order_value,
                                                           DROP COLUMN max_redemptions_per_day,
                                                           DROP COLUMN max_discount_per_day")))