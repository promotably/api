(ns config.migrate-config
  (:require [api.db :as db]))

 (defn migrate-config []
   {:directory "/src/migrations"
    :ns-content "\n  (:require [api.db :as db] \n            [clojure.java.jdbc :as jdbc])"
    :current-version db/db-version
    :update-version db/update-db-version})
