(ns api.components.database
  (:require [com.stuartsierra.component :as component]
            [korma.db :as kdb]))

(defrecord Database [config postgres-config]
  component/Lifecycle
  (start [this]
    (if postgres-config
      this
      (let [pg-conf (kdb/postgres config)]
        (do (kdb/defdb $the-db pg-conf))
        (assoc this :postgres-config pg-conf))))
  (stop [this]
    (if postgres-config
      (assoc this :postgres-config nil)
      this)))

(defn new-database [config]
  (map->Database {:config config}))
