(ns migrations.20150609223834-prompt-styling-for-offers
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150609223834."
  []
  (println "migrations.20150609223834-prompt-styling-for-offers up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE offers ADD COLUMN inline_selector text,
                         ADD COLUMN prompt_position text,
                         ADD COLUMN prompt_html text,
                         ADD COLUMN prompt_css text")))

(defn down
  "Migrates the database down from version 20150609223834."
  []
  (println "migrations.20150609223834-prompt-styling-for-offers down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE offers DROP COLUMN inline_selector,
                         DROP COLUMN prompt_position,
                         DROP COLUMN prompt_html,
                         DROP COLUMN prompt_css")))
