(ns migrations.20150428111034-insight-metrics
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150428111034."
  []
  (println "migrations.20150428111034-insight-metrics up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands db-con
      "create table METRICS_INSIGHTS (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        MEASUREMENT_HOUR timestamptz NOT NULL,
        DATA JSON NOT NULL,
        CREATED_AT timestamptz DEFAULT (now() at time zone 'utc') NOT NULL
      );"
      "CREATE INDEX metrics_insights_site_idx ON metrics_insights(site_id);")))


(defn down
  "Migrates the database down from version 20150428111034."
  []
  (println "migrations.20150428111034-insight-metrics down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands db-con
                         "DROP TABLE IF EXISTS metrics_insights")))
