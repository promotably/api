(ns migrations.20150604164644-add-unique-index-on-events
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150604164644."
  []
  (println "migrations.20150604164644-add-unique-index-on-events up...")
  (try
    (jdbc/with-db-connection [db-con @$db-config]
      (jdbc/db-do-commands
       db-con
       "CREATE UNIQUE INDEX events_event_id_idx ON events (event_id)"))
    (catch Exception ex
      (println (.getNextException ex)))))

(defn down
  "Migrates the database down from version 20150604164644."
  []
  (println "migrations.20150604164644-add-unique-index-on-events down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "DROP INDEX events_event_id_idx")))
