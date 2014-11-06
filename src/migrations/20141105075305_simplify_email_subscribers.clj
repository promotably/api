(ns migrations.20141105075305-simplify-email-subscribers
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20141105075305."
  []
  (println "migrations.20141105075305-simplify-email-subscribers up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE email_subscribers DROP COLUMN browser_id")))

(defn down
  "Migrates the database down from version 20141105075305."
  []
  (println "migrations.20141105075305-simplify-email-subscribers down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE email_subscribers ADD COLUMN browser_id uuid NOT NULL")))
