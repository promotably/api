(ns migrations.20150217062623-alter-events-w-control-group
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150217062623."
  []
  (println "migrations.20150217062623-alter-events-w-control-group up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE events ADD COLUMN control_group BOOLEAN DEFAULT false")))

(defn down
  "Migrates the database down from version 20150217062623."
  []
  (println "migrations.20150217062623-alter-events-w-control-group down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE events DROP COLUMN control_group")))