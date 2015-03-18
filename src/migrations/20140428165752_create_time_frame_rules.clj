(ns migrations.20140428165752-create-time-frame-rules
  (:require [api.db :as db]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140428165752."
  []
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :time_frame_rules
                            [:id "serial8 primary key"]
                            [:promo_id "int8 NOT NULL"]
                            [:created_at "timestamptz DEFAULT (now() at time zone 'utc') NOT NULL"]
                            [:updated_at "timestamptz DEFAULT (now() at time zone 'utc') NOT NULL"])
     (jdbc/create-table-ddl :time_frames
                            [:id "serial8 primary key"]
                            [:time_frame_rule_id "int8 NOT NULL"]
                            [:start_date "timestamp NOT NULL"]
                            [:end_date "timestamp NOT NULL"]
                            [:created_at "timestamptz DEFAULT (now() at time zone 'utc') NOT NULL"]
                            [:updated_at "timestamptz DEFAULT (now() at time zone 'utc') NOT NULL"]))))

(defn down
  "Migrates the database down from version 20140428165752."
  []
  (jdbc/db-do-commands @db/$db-config
                       (jdbc/drop-table-ddl :time_frames)
                       (jdbc/drop-table-ddl :time_frame_rules)))
