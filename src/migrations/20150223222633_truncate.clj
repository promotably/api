
(ns migrations.20150223222633-truncate
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150223222633."
  []
  (println "migrations.20150223222633-truncate up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "CREATE OR REPLACE FUNCTION truncate_tables(username IN VARCHAR) RETURNS void AS $$
      DECLARE statements CURSOR FOR
        SELECT tablename FROM pg_tables
        WHERE tableowner = username AND schemaname = 'public';
      BEGIN
      FOR stmt IN statements LOOP
        EXECUTE 'TRUNCATE TABLE ' || quote_ident(stmt.tablename) || ' CASCADE;';
      END LOOP;
      END;
      $$ LANGUAGE plpgsql;")))

(defn down
  "Migrates the database down from version 20150223222633."
  []
  (println "migrations.20150223222633-truncate down..."))
