(ns migrations.20150207063546-add-offers-html-and-css
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150207063546."
  []
  (println "migrations.20150207063546-add-offers-html-and-css up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offers ADD COLUMN html TEXT,
                                                 ADD COLUMN css TEXT")))

(defn down
  "Migrates the database down from version 20150207063546."
  []
  (println "migrations.20150207063546-add-offers-html-and-css down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offers DROP COLUMN html,
                                                 DROP COLUMN css")))