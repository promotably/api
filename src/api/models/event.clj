(ns api.models.event
  (:require [api.entities :refer :all]
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

(sm/defn ^:always-validate find-by-id :- (s/maybe BaseEvent)
         [id :- s/Int]
         (db-to-event
           (first (select events (where {:id id})))))


