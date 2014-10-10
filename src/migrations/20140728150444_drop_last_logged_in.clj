(ns migrations.20140728150444-drop-last-logged-in
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140728150444."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140728150444-drop-last-logged-in up...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE users DROP COLUMN last_logged_in_at"))))

(defn down
  "Migrates the database down from version 20140728150444."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140728150444-drop-last-logged-in down...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE users ADD COLUMN last_logged_in_at timestamp"))))
