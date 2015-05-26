(ns migrations.20150524184432-visit-sources
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150524184432."
  []
  (println "migrations.20150524184432-visit-sources up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands db-con
                         "create table VISIT_SOURCES (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        SITE_SHOPPER_ID UUID NOT NULL,
        SESSION_ID UUID NOT NULL,
        SOURCE_CATEGORY varchar(255) NOT NULL,
        DATA JSON NOT NULL,
        CREATED_AT timestamptz DEFAULT (now() at time zone 'utc') NOT NULL
      );"
      "CREATE INDEX visit_sources_site_idx ON visit_sources(site_id);")))

(defn down
  "Migrates the database down from version 20150524184432."
  []
  (println "migrations.20150524184432-visit-sources down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands db-con
                         "DROP TABLE IF EXISTS visit_sources")))

