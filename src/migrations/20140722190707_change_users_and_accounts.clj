(ns migrations.20140722190707-change-users-and-accounts
  (:require [api.db :as db]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140722190707."
  []
  (println "migrations.20140722190707-change-users-and-accounts up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE users DROP COLUMN crypted_password"
     "ALTER TABLE users DROP COLUMN company_name"
     "ALTER TABLE users ALTER COLUMN username DROP NOT NULL")))

(defn down
  "Migrates the database down from version 20140722190707."
  []
  (println "migrations.20140722190707-change-users-and-accounts down..."))
