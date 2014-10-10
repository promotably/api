(ns migrations.20140629153157-add-item-limit-to-promos
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140629153157."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140629153157-add-item-limit-to-promos up...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE promos ADD COLUMN limit_usage_to_x_items INT4 DEFAULT NULL"))))

(defn down
  "Migrates the database down from version 20140629153157."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140629153157-add-item-limit-to-promos down...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE promos DROP COLUMN limit_usage_to_x_items"))))
