(ns migrations.20140722135625-tighten-email-subscribers
  (:require [api.db :as db]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140722135625."
  []
  (println "migrations.20140722135625-tighten-email-subscribers up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE email_subscribers DROP CONSTRAINT email_subscribers_pkey"
     "ALTER TABLE email_subscribers ADD COLUMN id serial8 PRIMARY KEY"
     "CREATE UNIQUE INDEX email_subscribers_email_idx ON email_subscribers ( email )")))

(defn down
  "Migrates the database down from version 20140722135625."
  []
  (println "migrations.20140722135625-tighten-email-subscribers down...")
  ;; FUCK THAT
  )
