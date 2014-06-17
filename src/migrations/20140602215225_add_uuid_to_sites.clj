(ns migrations.20140602215225-add-uuid-to-sites
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140602215225."
  []
  (println "migrations.20140602215225-add-uuid-to-sites up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites ADD COLUMN uuid uuid NOT NULL DEFAULT uuid_generate_v4()")))

(defn down
  "Migrates the database down from version 20140602215225."
  []
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites DROP COLUMN uuid")))
