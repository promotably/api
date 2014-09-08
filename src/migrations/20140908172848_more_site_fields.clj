(ns migrations.20140908172848-more-site-fields
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140908172848."
  []
  (println "migrations.20140908172848-more-site-fields up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites ADD COLUMN site_url TEXT")))

(defn down
  "Migrates the database down from version 20140908172848."
  []
  (println "migrations.20140908172848-more-site-fields down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites DROP COLUMN site_url")))
