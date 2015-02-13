(ns migrations.20150212223342-add-days-since-last-offer-to-conditions
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150212223342."
  []
  (println "migrations.20150212223342-add-days-since-last-offer-to-conditions up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE offer_conditions ADD COLUMN days_since_last_offer integer"
     "ALTER TABLE offer_conditions DROP COLUMN minutes_since_last_offer")))

(defn down
  "Migrates the database down from version 20150212223342."
  []
  (println "migrations.20150212223342-add-days-since-last-offer-to-conditions down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE offer_conditions DROP COLUMN days_since_last_offer"
     "ALTER TABLE offer_conditions ADD COLUMN minutes_since_last_offer integer")))
