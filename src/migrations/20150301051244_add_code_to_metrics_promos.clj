(ns migrations.20150301051244-add-code-to-metrics-promos
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150301051244."
  []
  (println "migrations.20150301051244-add-code-to-metrics-promos up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands  
                             db-con 
                             "ALTER TABLE metrics_promos ADD COLUMN code varchar(255)")))
  

(defn down
  "Migrates the database down from version 20150301051244."
  []
  (println "migrations.20150301051244-add-code-to-metrics-promos down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands 
                             db-con 
                             "ALTER TABLE metrics_promos DROP COLUMN code")))