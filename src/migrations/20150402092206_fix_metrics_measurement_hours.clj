(ns migrations.20150402092206-fix-metrics-measurement-hours
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150402092206."
  []
  (println "migrations.20150402092206-fix-metrics-measurement-hours up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE metrics_revenue ALTER COLUMN measurement_hour TYPE timestamptz"
     "ALTER TABLE metrics_additional_revenue ALTER COLUMN measurement_hour TYPE timestamptz"
     "ALTER TABLE metrics_promos ALTER COLUMN measurement_hour TYPE timestamptz"
     "ALTER TABLE metrics_lift ALTER COLUMN measurement_hour TYPE timestamptz"
     "ALTER TABLE metrics_rcos ALTER COLUMN measurement_hour TYPE timestamptz")))

(defn down
  "Migrates the database down from version 20150402092206."
  []
  (println "migrations.20150402092206-fix-metrics-measurement-hours down..."))
