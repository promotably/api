(ns migrations.20150226194830-add-metrics-db-schema
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150226194830."
  []
  (println "migrations.20150226194830-add-metrics-db-schema up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands db-con
      "create table METRICS_REVENUE (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        MEASUREMENT_HOUR timestamp NOT NULL,
        NUMBER_OF_ORDERS int8 NOT NULL,
        DISCOUNT numeric(17,2) NOT NULL,
        PROMOTABLY_COMMISSION numeric(17,2) NOT NULL,
        REVENUE numeric(17,2) NOT NULL,
        LESS_COMMISSION_AND_DISCOUNT numeric(17,2) NOT NULL,
        CREATED_AT timestamp NOT NULL DEFAULT now()
      );"
      "CREATE INDEX metrics_revenue_site_idx ON metrics_revenue(site_id);"

      "create table METRICS_PROMOS (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        PROMO_ID uuid NOT NULL,
        MEASUREMENT_HOUR timestamp NOT NULL,
        REDEMPTIONS int8 NOT NULL,
        DISCOUNT numeric(17,2) NOT NULL,
        REVENUE numeric(17,2) NOT NULL,
        CREATED_AT timestamp NOT NULL DEFAULT now()
      );"
      "CREATE INDEX metrics_promos_site_idx ON metrics_promos(site_id);"
      "CREATE INDEX metrics_promos_site_promo_idx ON metrics_promos(site_id,promo_id);"

      "create table METRICS_RCOS (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        OFFER_ID uuid NOT NULL,
        MEASUREMENT_HOUR timestamp NOT NULL,
        VISITS int8 NOT NULL,
        QUALIFIED int8 NOT NULL,
        OFFERED int8 NOT NULL,
        ORDERS int8 NOT NULL,
        REDEMPTIONS int8 NOT NULL,
        TOTAL_ITEMS_IN_CARTS int8 NOT NULL,
        REVENUE numeric(17,2) NOT NULL,
        DISCOUNT numeric(17,2) NOT NULL,
        CREATED_AT timestamp NOT NULL DEFAULT now()
      );"
      "CREATE INDEX metrics_rcos_site_idx ON metrics_rcos(site_id);"
      "CREATE INDEX metrics_rcos_site_offer_idx ON metrics_rcos(site_id,offer_id);"

      "create table METRICS_LIFT (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        OFFER_ID uuid, /* IF NULL = CONTROL GROUP */
        MEASUREMENT_HOUR timestamp NOT NULL,
        CONVERSION numeric(17,2) NOT NULL,
        AVG_ORDER_VALUE numeric(17,2) NOT NULL,
        CART_ABANDON_RATE numeric(17,2) NOT NULL,
        REVENUE_PER_VISIT numeric(17,2) NOT NULL,
        CREATED_AT timestamp NOT NULL DEFAULT now()
      );"
      "CREATE INDEX metrics_lift_site_idx ON metrics_lift(site_id);"
      "CREATE INDEX metrics_lift_site_offer_idx ON metrics_lift(site_id,offer_id);")))

(defn down
  "Migrates the database down from version 20150226194830."
  []
  (println "migrations.20150226194830-add-metrics-db-schema down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands db-con
                       (jdbc/drop-table-ddl :metrics_revenue)
                       (jdbc/drop-table-ddl :metrics_promos)
                       (jdbc/drop-table-ddl :metrics_rcos)
                       (jdbc/drop-table-ddl :metrics_lift))))
