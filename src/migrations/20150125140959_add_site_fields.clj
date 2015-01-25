(ns migrations.20150125140959-add-site-fields
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150125140959."
  []
  (println "migrations.20150125140959-add-site-fields up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites ADD COLUMN country text,
                        ADD COLUMN timezone text,
                        ADD COLUMN language text,
                        ADD COLUMN currency text")))

(defn down
  "Migrates the database down from version 20150125140959."
  []
  (println "migrations.20150125140959-add-site-fields down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE sites DROP COLUMN country,
                        DROP COLUMN timezone,
                        DROP COLUMN language,
                        DROP COLUMN currency")))
