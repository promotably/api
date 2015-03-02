(ns migrations.20150301063116-add-code-to-metrics-rcos
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150301063116."
  []
  (println "migrations.20150301063116-add-code-to-metrics-rcos up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands 
                             db-con 
                             "ALTER TABLE metrics_rcos ADD COLUMN code varchar(255)")))

(defn down
  "Migrates the database down from version 20150301063116."
  []
  (println "migrations.20150301063116-add-code-to-metrics-rcos down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands 
                             db-con 
                             "ALTER TABLE metrics_rcos DROP COLUMN code")))