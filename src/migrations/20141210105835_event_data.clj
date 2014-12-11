(ns migrations.20141210105835-event-data
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20141210105835."
  []
  (println "migrations.20141210105835-event-data up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :site_visits
                            [:message_id "uuid primary key"]
                            [:site_id "uuid NOT NULL"]
                            [:shopper_id "uuid NOT NULL"]
                            [:session_id "uuid NOT NULL"]
                            [:created_at "timestamp DEFAULT current_timestamp"])
     "CREATE INDEX site_id_shopper_id_idx ON site_visits(site_id, shopper_id)"
     (jdbc/create-table-ddl :product_views
                            [:message_id "uuid primary key"]
                            [:site_id "uuid NOT NULL"]
                            [:shopper_id "uuid NOT NULL"]
                            [:session_id "uuid NOT NULL"]
                            [:product_id "text NOT NULL"]
                            [:category_id "text"]
                            [:created_at "timestamp DEFAULT current_timestamp"])
     "CREATE INDEX ProductViews_site_id_shopper_id_idx ON product_views (site_id, shopper_id)"
     (jdbc/create-table-ddl :orders
                            [:message_id "uuid primary key"]
                            [:site_id "uuid NOT NULL"]
                            [:shopper_id "uuid NOT NULL"]
                            [:session_id "uuid NOT NULL"]
                            [:order_number "text"]
                            [:currency "text DEFAULT 'USD'"]
                            [:total_amount_pre_tax "decimal(12,4) NOT NULL"]
                            [:total_amount_with_tax "decimal(12,4) NOT NULL"]
                            [:created_at "timestamp DEFAULT current_timestamp"])
     "CREATE INDEX Orders_site_id_shopper_id_idx ON orders (site_id, shopper_id)"
     (jdbc/create-table-ddl :order_products
                            [:id "serial8 primary key"]
                            [:message_id "uuid NOT NULL"]
                            [:product_id "text NOT NULL"]
                            [:quantity "integer NOT NULL"]
                            [:total_line_amount "decimal(12,4) NOT NULL"])
     "CREATE INDEX OrderProducts_message_id_idx ON order_products (message_id)"
     (jdbc/create-table-ddl :promo_uses
                            [:message_id "uuid primary key"]
                            [:site_id "uuid NOT NULL"]
                            [:shopper_id "uuid NOT NULL"]
                            [:session_id "uuid NOT NULL"]
                            [:promo_id "uuid NOT NULL"]
                            [:currency "text DEFAULT 'USD'"]
                            [:discount_amount "decimal(12,4) NOT NULL"]
                            [:created_at "timestamp DEFAULT current_timestamp"])
     "CREATE INDEX PromoUses_site_id_promo_id_idx ON promo_uses (site_id, promo_id)"
     "CREATE INDEX PromoUses_site_id_promo_id_shopper_id_idx ON promo_uses (site_id, promo_id, shopper_id)")))

(defn down
  "Migrates the database down from version 20141210105835."
  []
  (println "migrations.20141210105835-event-data down...")
  (jdbc/db-do-commands @db/$db-config
                       (jdbc/drop-table-ddl :site_visits)
                       (jdbc/drop-table-ddl :product_views)
                       (jdbc/drop-table-ddl :orders)
                       (jdbc/drop-table-ddl :order_products)
                       (jdbc/drop-table-ddl :promo_uses)))
