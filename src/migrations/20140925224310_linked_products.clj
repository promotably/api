(ns migrations.20140925224310-linked-products
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140925224310."
  []
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :linked_products
                            [:id "serial8 primary key"]
                            [:uuid "uuid NOT NULL"]
                            [:promo_id "INTEGER REFERENCES promos (id)"]
                            [:url "varchar(512) NOT NULL"]
                            [:photo_url "varchar(512) NOT NULL"]
                            [:name "varchar(255) NOT NULL"]
                            [:original_price "NUMERIC(16,4) DEFAULT 0"]
                            [:seo_copy "varchar(1024) NOT NULL"]
                            [:created_at "timestamp"])
     "CREATE UNIQUE INDEX lp_uuid_idx ON linked_products ( uuid )"
     "CREATE INDEX lp_promo_idx ON linked_products ( promo_id )"
     ))
  (println "migrations.20140925224310-linked-products up..."))

(defn down
  "Migrates the database down from version 20140925224310."
  []
  (jdbc/db-do-commands @db/$db-config
                       (jdbc/drop-table-ddl :linked_products))
  (println "migrations.20140925224310-linked-products down..."))
