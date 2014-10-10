(ns migrations.20140722193210-change-users-and-accounts-part-deux
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140722193210."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140722193210-change-users-and-accounts-part-deux up...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE accounts ADD COLUMN account_id uuid NOT NULL DEFAULT uuid_generate_v4()"
       "ALTER TABLE users ADD COLUMN user_id uuid NOT NULL DEFAULT uuid_generate_v4()"
       "ALTER TABLE users ADD COLUMN first_name TEXT"
       "ALTER TABLE users ADD COLUMN last_name TEXT"))))

(defn down
  "Migrates the database down from version 20140722193210."
  []
  (println "migrations.20140722193210-change-users-and-accounts-part-deux down..."))
