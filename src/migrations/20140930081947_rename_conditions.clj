(ns migrations.20140930081947-rename-conditions
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140930081947."
  []
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE conditions RENAME TO promo_conditions"))
  (println "migrations.20140930081947-rename-conditions up..."))

(defn down
  "Migrates the database down from version 20140930081947."
  []
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promo_conditions RENAME TO conditions"))
  (println "migrations.20140930081947-rename-conditions down..."))
