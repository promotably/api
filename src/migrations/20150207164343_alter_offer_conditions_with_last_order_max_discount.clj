(ns migrations.20150207164343-alter-offer-conditions-with-last-order-max-discount
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150207164343."
  []
  (println "migrations.20150207164343-alter-offer-conditions-with-last-order-max-discount up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions ADD COLUMN last_order_max_discount INTEGER")))

(defn down
  "Migrates the database down from version 20150207164343."
  []
  (println "migrations.20150207164343-alter-offer-conditions-with-last-order-max-discount down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions DROP COLUMN last_order_max_discount")))