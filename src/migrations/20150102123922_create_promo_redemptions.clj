(ns migrations.20150102123922-create-promo-redemptions
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150102123922."
  []
  (println "migrations.20150102123922-create-promo-redemptions up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :promo_redemptions
                            [:id "bigserial primary key"]
                            [:event_id "uuid not null"]
                            [:site_id "uuid not null"]
                            [:order_id "text not null"]
                            [:promo_code "text not null"]
                            [:promo_id "uuid"]
                            [:discount "numeric(16,8)"]
                            [:shopper_id "uuid not null"]
                            [:session_id "uuid not null"]
                            [:created_at "timestamptz DEFAULT (now() at time zone 'utc') NOT NULL"])
     "CREATE INDEX promo_redemptions_upsert_idx ON promo_redemptions(site_id, order_id, promo_code);"
     "CREATE INDEX promo_redemptions_event_id_idx ON promo_redemptions(event_id);"
     "CREATE OR REPLACE FUNCTION upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, sessionId uuid)
      RETURNS void AS $$
      BEGIN
      IF NOT EXISTS (SELECT 1 FROM promo_redemptions
                         WHERE site_id=siteId
                         AND order_id=orderId
                         AND promo_code=promoCode) THEN
          INSERT INTO promo_redemptions (event_id, site_id, order_id, promo_code,
                                         discount, shopper_id, session_id)
              VALUES (eventId, siteId, orderId, promoCode, _discount, shopperId,
                      sessionId);
      END IF;
      NULL;
      END;
      $$ LANGUAGE plpgsql;")))

(defn down
  "Migrates the database down from version 20150102123922."
  []
  (println "migrations.20150102123922-create-promo-redemptions down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/drop-table-ddl :promo_redemptions)
     "DROP FUNCTION upsertPromoRedemption (eventId uuid, siteId uuid, orderId text,
          promoCode text, _discount numeric, shopperId uuid, sessionId uuid);")))
