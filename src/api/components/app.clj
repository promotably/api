(ns api.components.app
  (:require [com.stuartsierra.component :as component]))

(defrecord ApiApp [app-config db k-producer]
  component/Lifecycle
  (start [this]
    (assoc this :database (:postgres-config db) :producer (:producer k-producer)))
  (stop [this]
    this))

(defn api-app [app-config]
  (map->ApiApp {:app-config app-config}))
