(ns migrations.20150208125311-uuid-to-site-id
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150208125311."
  []
  (println "migrations.20150208125311-uuid-to-site-id up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites RENAME COLUMN uuid TO site_id")))

(defn down
  "Migrates the database down from version 20150208125311."
  []
  (println "migrations.20150208125311-uuid-to-site-id down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites RENAME COLUMN site_id TO uuid")))
