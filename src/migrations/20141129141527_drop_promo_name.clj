(ns migrations.20141129141527-drop-promo-name
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20141129141527."
  []
  (println "migrations.20141129141527-drop-promo-name up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos DROP COLUMN name,
                         ADD COLUMN seo_text text")))

(defn down
  "Migrates the database down from version 20141129141527."
  []
  (println "migrations.20141129141527-drop-promo-name down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos ADD COLUMN name text,
                         DROP COLUMN seo_text")))
