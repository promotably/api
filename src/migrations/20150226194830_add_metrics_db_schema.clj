(ns migrations.20150226194830-add-metrics-db-schema
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150226194830."
  []
  (println "migrations.20150226194830-add-metrics-db-schema up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands db-con
      "create table METRICS_ADDITIONAL_REVENUE (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        MEASUREMENT_HOUR timestamp NOT NULL,
        NUMBER_OF_ORDERS int8 NOT NULL,
        DISCOUNT numeric(17,2) NOT NULL,
        PROMOTABLY_COMMISSION numeric(17,2) NOT NULL,
        REVENUE numeric(17,2) NOT NULL,
        LESS_COMMISSION_AND_DISCOUNT numeric(17,2) NOT NULL,
        CREATED_AT timestamptz DEFAULT (now() at time zone 'utc') NOT NULL
      );"
      "CREATE INDEX metrics_additional_revenue_site_idx ON metrics_additional_revenue(site_id);"

      "create table METRICS_REVENUE (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        MEASUREMENT_HOUR timestamp NOT NULL,
        TOTAL_REVENUE numeric(17,2) NOT NULL,
        DISCOUNT numeric(17,2) NOT NULL,
        NUMBER_OF_ORDERS int8 NOT NULL,
        AVG_ORDER_REVENUE numeric(17,2) NOT NULL,
        REVENUE_PER_VISIT numeric(17,2) NOT NULL,
        CREATED_AT timestamptz DEFAULT (now() at time zone 'utc') NOT NULL
      );"
      "CREATE INDEX metrics_revenue_site_idx ON metrics_revenue(site_id);"

      "create table METRICS_PROMOS (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        PROMO_ID uuid NOT NULL,
        CODE varchar(255) NOT NULL,
        MEASUREMENT_HOUR timestamp NOT NULL,
        REDEMPTIONS int8 NOT NULL,
        DISCOUNT numeric(17,2) NOT NULL,
        REVENUE numeric(17,2) NOT NULL,
        REVENUE_PER_ORDER numeric(17,2) NOT NULL,
        CREATED_AT timestamptz DEFAULT (now() at time zone 'utc') NOT NULL
      );"
      "CREATE INDEX metrics_promos_site_idx ON metrics_promos(site_id);"
      "CREATE INDEX metrics_promos_site_promo_idx ON metrics_promos(site_id,promo_id);"

      "create table METRICS_RCOS (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        OFFER_ID uuid NOT NULL,
        CODE varchar(255) NOT NULL,
        MEASUREMENT_HOUR timestamp NOT NULL,
        VISITS int8 NOT NULL,
        QUALIFIED int8 NOT NULL,
        OFFERED int8 NOT NULL,
        ORDERS int8 NOT NULL,
        REDEEMED int8 NOT NULL,
        REDEMPTION_RATE numeric(17,2) NOT NULL,
        CONVERSION_RATE numeric(17,2) NOT NULL,
        TOTAL_ITEMS_IN_CARTS int8 NOT NULL,
        AVG_ITEMS_IN_CART numeric(17,2) NOT NULL,
        AVG_REVENUE numeric(17,2) NOT NULL,
        REVENUE numeric(17,2) NOT NULL,
        AVG_DISCOUNT numeric(17,2) NOT NULL,
        DISCOUNT numeric(17,2) NOT NULL,
        CREATED_AT timestamptz DEFAULT (now() at time zone 'utc') NOT NULL
      );"
      "CREATE INDEX metrics_rcos_site_idx ON metrics_rcos(site_id);"
      "CREATE INDEX metrics_rcos_site_offer_idx ON metrics_rcos(site_id,offer_id);"

      "create table METRICS_LIFT (
        ID serial8 primary key,
        SITE_ID uuid NOT NULL,
        MEASUREMENT_HOUR timestamp NOT NULL,
        TOTAL_REVENUE_INC numeric(17,2) NOT NULL,
        TOTAL_REVENUE_EXC numeric(17,2) NOT NULL,
        AVG_ORDER_REVENUE_INC numeric(17,2) NOT NULL,
        AVG_ORDER_REVENUE_EXC numeric(17,2) NOT NULL,
        REVENUE_PER_VISIT_INC numeric(17,2) NOT NULL,
        REVENUE_PER_VISIT_EXC numeric(17,2) NOT NULL,
        ORDER_COUNT_INC int8 NOT NULL,
        ORDER_COUNT_EXC int8 NOT NULL,
        CREATED_AT timestamptz DEFAULT (now() at time zone 'utc') NOT NULL
      );"
      "CREATE INDEX metrics_lift_site_idx ON metrics_lift(site_id);")))

(defn down
  "Migrates the database down from version 20150226194830."
  []
  (println "migrations.20150226194830-add-metrics-db-schema down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands db-con
                         "DROP TABLE IF EXISTS metrics_additional_revenue"
                         "DROP TABLE IF EXISTS metrics_revenue"
                         "DROP TABLE IF EXISTS metrics_promos"
                         "DROP TABLE IF EXISTS metrics_rcos"
                         "DROP TABLE IF EXISTS metrics_lift")))
