(ns migrations.20150128161616-site-code-unique
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150128161616."
  []
  (println "migrations.20150128161616-site-code-unique up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites ADD CONSTRAINT uniquesitecode UNIQUE (site_code)")))

(defn down
  "Migrates the database down from version 20150128161616."
  []
  (println "migrations.20150128161616-site-code-unique down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites DROP CONSTRAINT uniquesitecode")))
