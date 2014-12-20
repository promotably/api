(ns migrations.20141216215403-usage-discounts-stored-procedures
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20141216215403."
  []
  (println "migrations.20141216215403-usage-discounts-stored-procedures up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "CREATE OR REPLACE FUNCTION promoUsageCount (siteId uuid, promoId uuid)
      RETURNS integer AS $total$
      declare
          total integer;
      BEGIN
          SELECT count(*) into total FROM EVENTS WHERE type='trackthankyou' AND site_id=siteId AND promo_id=promoId;
          RETURN total;
      END
      $total$ LANGUAGE plpgsql;"

     "CREATE OR REPLACE FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                             sessionId uuid, promoId uuid, _data json)
      RETURNS void AS $$
      BEGIN
      IF _type = 'trackthankyou' THEN
          IF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)
                                              OR (events.type=_type
                                                  AND events.site_id=siteId
                                                  AND events.shopper_id=shopperId
                                                  AND events.session_id=sessionId
                                                  AND json_extract_path_text(events.data, 'order-id')=json_extract_path_text(_data, 'order-id'))) THEN
              INSERT INTO events (event_id, type, site_id, shopper_id, session_id, data)
                                 VALUES (eventId, _type, siteId, shopperId, sessionId, _data);
              NULL
          END IF;
      ELSIF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)) THEN
          INSERT INTO events (event_id, type, site_id, shopper_id, session_id, promo_id, data)
                             VALUES (eventId, _type, siteId, shopperId, sessionId, promoId, _data);
          NULL
      END IF;
      END;
      $$ LANGUAGE plpgsql;")))

(defn down
  "Migrates the database down from version 20141216215403."
  []
  (println "migrations.20141216215403-usage-discounts-stored-procedures down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "DROP FUNCTION promoUsageCount (siteId uuid, promoId uuid);"
     "DROP FUNCTION upsertEvent (_type text, eventId uuid, siteId uuid, shopperId uuid,
                                 sessionId uuid, promoId uuid, _data json);")))
