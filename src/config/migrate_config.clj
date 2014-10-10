(ns config.migrate-config
  (:require [api.core :as api]
            [api.env :as e]
            [api.db :as db]))

 (defn migrate-config []
   {:directory "/src/migrations"
    :ns-content "\n  (:require [clojure.java.jdbc :as jdbc] \n [api.system :refer [system]])"
    :init (fn [_]
            (e/init!)
            (api/sys-init!))
    :current-version db/db-version
    :update-version db/update-db-version})
