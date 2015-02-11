(ns migrations.20150210221112-add-num-visits-offer-condition-column
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150210221112."
  []
  (println "migrations.20150210221112-add-num-visits-offer-condition-column up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions ADD COLUMN num_visits INTEGER")))

(defn down
  "Migrates the database down from version 20150210221112."
  []
  (println "migrations.20150210221112-add-num-visits-offer-condition-column down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions DROP COLUMN num_visits")))
