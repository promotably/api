(ns migrations.20150217063831-modify-upsertevent-to-track-control-group
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150217063831."
  []
  (println "migrations.20150217063831-modify-upsertevent-to-track-control-group up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "DROP FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                             siteShopperId uuid, sessionId uuid, promoId uuid, _data json);"
     "CREATE OR REPLACE FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                             siteShopperId uuid, sessionId uuid, promoId uuid, controlGroup boolean, _data json)
      RETURNS void AS $$
      BEGIN
      IF _type = 'thankyou' THEN
          IF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)
                                              OR (events.type=_type
                                                  AND events.site_id=siteId
                                                  AND events.shopper_id=shopperId
                                                  AND events.site_shopper_id=siteShopperId
                                                  AND events.session_id=sessionId
                                                  AND events.control_group=controlGroup
                                                  AND json_extract_path_text(events.data, 'order-id')=json_extract_path_text(_data, 'order-id'))) THEN
              INSERT INTO events (event_id, type, site_id, shopper_id, site_shopper_id, session_id, control_group, data)
                                 VALUES (eventId, _type, siteId, shopperId, siteShopperId, sessionId, controlGroup, _data);
              NULL;
          END IF;
      ELSIF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)) THEN
          INSERT INTO events (event_id, type, site_id, shopper_id, site_shopper_id, session_id, promo_id, control_group, data)
                             VALUES (eventId, _type, siteId, shopperId, siteShopperId, sessionId, promoId, controlGroup, _data);
          NULL;
      END IF;
      END;
      $$ LANGUAGE plpgsql VOLATILE;")))


(defn down
  "Migrates the database down from version 20150217063831."
  []
  (println "migrations.20150217063831-modify-upsertevent-to-track-control-group down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "DROP FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                             siteShopperId uuid, sessionId uuid, promoId uuid, controlGroup boolean, _data json);"
     "CREATE OR REPLACE FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                             siteShopperId uuid, sessionId uuid, promoId uuid, _data json)
      RETURNS void AS $$
      BEGIN
      IF _type = 'thankyou' THEN
          IF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)
                                              OR (events.type=_type
                                                  AND events.site_id=siteId
                                                  AND events.shopper_id=shopperId
                                                  AND events.site_shopper_id=siteShopperId
                                                  AND events.session_id=sessionId
                                                  AND json_extract_path_text(events.data, 'order-id')=json_extract_path_text(_data, 'order-id'))) THEN
              INSERT INTO events (event_id, type, site_id, shopper_id, site_shopper_id, session_id, data)
                                 VALUES (eventId, _type, siteId, shopperId, siteShopperId, sessionId, _data);
              NULL;
          END IF;
      ELSIF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)) THEN
          INSERT INTO events (event_id, type, site_id, shopper_id, site_shopper_id, session_id, promo_id, data)
                             VALUES (eventId, _type, siteId, shopperId, siteShopperId, sessionId, promoId, _data);
          NULL;
      END IF;
      END;
      $$ LANGUAGE plpgsql VOLATILE;")))