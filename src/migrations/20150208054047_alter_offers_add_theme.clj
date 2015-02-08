(ns migrations.20150208054047-alter-offers-add-theme
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150208054047."
  []
  (println "migrations.20150208054047-alter-offers-add-theme up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offers ADD COLUMN theme varchar(255)")))

(defn down
  "Migrates the database down from version 20150208054047."
  []
  (println "migrations.20150208054047-alter-offers-add-theme down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offers DROP COLUMN theme")))
