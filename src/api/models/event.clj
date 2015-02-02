(ns api.models.event
  (:require [api.entities :refer :all]
            [clj-time.core :refer [now minus days]]
            [clj-time.coerce :refer [to-sql-date]]
            [clojure.set :refer [rename-keys]]
            [api.util :refer [hyphenify-key assoc*]]
            [api.lib.schema :refer :all]
            [korma.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]))

(defn db-to-event
  "Translates a database result to a map"
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(sm/defn count-shopper-events-by-days
  [site-shopper-uuid :- s/Uuid event-type :- s/Str days-ago :- s/Int]
  (let [r (select events
            (aggregate (count :*) :cnt)
            (where {:site_shopper_id site-shopper-uuid
                    :type event-type
                    :created_at [>= (to-sql-date (minus (now) (days days-ago)))]}))
        count (get (first r) :cnt 0)]
    count))

(sm/defn ^:always-validate find-by-id :- (s/maybe BaseEvent)
         [id :- s/Int]
         (db-to-event
           (first (select events (where {:id id})))))

