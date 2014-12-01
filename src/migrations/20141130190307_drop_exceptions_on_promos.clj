(ns migrations.20141130190307-drop-exceptions-on-promos
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20141130190307."
  []
  (println "migrations.20141130190307-drop-exceptions-on-promos up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos DROP COLUMN exceptions")))

(defn down
  "Migrates the database down from version 20141130190307."
  []
  (println "migrations.20141130190307-drop-exceptions-on-promos down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos ADD COLUMN exceptions text")))
