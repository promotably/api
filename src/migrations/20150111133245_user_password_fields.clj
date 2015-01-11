(ns migrations.20150111133245-user-password-fields
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150111133245."
  []
  (println "migrations.20150111133245-user-password-fields up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE users ADD COLUMN password text"
     "ALTER TABLE users ADD COLUMN password_salt text")))

(defn down
  "Migrates the database down from version 20150111133245."
  []
  (println "migrations.20150111133245-user-password-fields down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE users DROP COLUMN password"
     "ALTER TABLE users DROP COLUMN password_salt")))
