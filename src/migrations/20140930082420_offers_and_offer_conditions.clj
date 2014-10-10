(ns migrations.20140930082420-offers-and-offer-conditions
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140930082420."
  []
  (let [db-config (get-in system [:app :database])]
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       (jdbc/create-table-ddl :offers
                              [:id "serial8 primary key"]
                              [:uuid "uuid NOT NULL"]
                              [:site_id "INTEGER REFERENCES sites (id)"]
                              [:promo_id "INTEGER REFERENCES promos (id)"]
                              [:code "TEXT NOT NULL"]
                              [:name "TEXT NOT NULL"]
                              [:active "BOOLEAN DEFAULT TRUE"]
                              [:display_text "TEXT NOT NULL"]
                              [:dynamic "BOOLEAN NOT NULL DEFAULT FALSE"]
                              [:expiry_in_minutes "INTEGER"]
                              [:presentation_type "TEXT NOT NULL"]
                              [:presentation_page "TEXT NOT NULL"]
                              [:presentation_display_text "TEXT"]
                              [:updated_at "timestamp NOT NULL"]
                              [:created_at "timestamp NOT NULL"])
       "CREATE UNIQUE INDEX offers_uuid_idx ON offers ( uuid )"
       "CREATE INDEX offers_site_idx ON offers ( site_id )"
       (jdbc/create-table-ddl :offer_conditions
                              [:id "serial8 primary key"]
                              [:uuid "uuid NOT NULL"]
                              [:offer_id "INTEGER REFERENCES offers (id)"]
                              [:type "TEXT NOT NULL"]
                              [:start_date "DATE"]
                              [:end_date "DATE"]
                              [:start_time "TIMESTAMP"]
                              [:end_time "TIMESTAMP"]
                              [:minutes_since_last_offer "INTEGER"]
                              [:minutes_on_site "INTEGER"]
                              [:minutes_since_last_engagement "INTEGER"]
                              [:product_views "INTEGER"]
                              [:repeat_product_views "INTEGER"]
                              [:items_in_cart "INTEGER"]
                              [:shipping_zipcode "TEXT"]
                              [:billing_zipcode "TEXT"]
                              [:referer_domain "TEXT"]
                              [:shopper_device_type "TEXT"]
                              [:num_orders "INTEGER"]
                              [:period_in_days "INTEGER"]
                              [:num_lifetime_orders "INTEGER"]
                              [:last_order_total "NUMERIC(16,4)"]
                              [:last_order_item_count "INTEGER"]
                              [:last_order_includes_item_id "TEXT[]"]
                              [:created_at "timestamp"])
       "CREATE UNIQUE INDEX offer_conditions_uuid_idx ON offer_conditions ( uuid )"
       "CREATE INDEX offer_conditions_offer_idx ON offer_conditions ( offer_id )"
       )))
  (println "migrations.20140930082420-offers-and-offer-conditions up..."))

(defn down
  "Migrates the database down from version 20140930082420."
  []
  (let [db-config (get-in system [:app :database])]
    (jdbc/db-do-commands
     db-config
     (jdbc/drop-table-ddl :offer_conditions)
     (jdbc/drop-table-ddl :offers)))
  (println "migrations.20140930082420-offers-and-offer-conditions down..."))
