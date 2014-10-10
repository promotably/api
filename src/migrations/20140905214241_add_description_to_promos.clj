(ns migrations.20140905214241-add-description-to-promos
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140905214241."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140905214241-add-description-to-promos up...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE promos ADD COLUMN description TEXT"))))

(defn down
  "Migrates the database down from version 20140905214241."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140905214241-add-description-to-promos down...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE promos DROP COLUMN description"))))
