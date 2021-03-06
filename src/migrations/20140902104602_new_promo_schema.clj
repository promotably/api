(ns migrations.20140902104602-new-promo-schema
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140902104602."
  []
  (println "migrations.20140902104602-new-promo-schema up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos RENAME COLUMN amount TO reward_amount "
     "ALTER TABLE promos ADD COLUMN reward_type TEXT,
                         ADD COLUMN exceptions TEXT,
                         ADD COLUMN reward_tax TEXT,
                         DROP COLUMN apply_before_tax,
                         ADD COLUMN reward_applied_to TEXT"
     (jdbc/create-table-ddl :conditions
                            [:id "serial8 primary key"]
                            [:promo_id "INTEGER REFERENCES promos (id)"]
                            [:uuid "uuid NOT NULL"]
                            [:type "TEXT NOT NULL"]
                            [:start_date "TIMESTAMP WITHOUT TIME ZONE"]
                            [:end_date "TIMESTAMP WITHOUT TIME ZONE"]
                            [:start_time "TEXT"]
                            [:end_time "TEXT"]
                            [:usage_count "INTEGER"]
                            [:total_discounts "NUMERIC(16,4)"]
                            [:product_ids "TEXT[]"]
                            [:category_ids "TEXT[]"]
                            [:not_product_ids "TEXT[]"]
                            [:not_category_ids "TEXT[]"]
                            [:combo_product_ids "TEXT[]"]
                            [:item_count "INTEGER"]
                            [:item_value "NUMERIC(16,4)"]
                            [:min_order_value "NUMERIC(16,4)"]))))

(defn down
  "Migrates the database down from version 20140902104602."
  []
  (println "migrations.20140902104602-new-promo-schema down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/drop-table-ddl :conditions)
     "ALTER TABLE promos RENAME COLUMN reward_amount TO amount"
     "ALTER TABLE promos DROP COLUMN reward_type"
     "ALTER TABLE promos DROP COLUMN exceptions"
     "ALTER TABLE promos DROP COLUMN reward_tax"
     "ALTER TABLE promos ADD COLUMN apply_before_tax BOOLEAN DEFAULT false"
     "ALTER TABLE promos DROP COLUMN reward_applied_to")))
