(ns api.db
  (:require [korma.core :refer :all]
            [korma.db :as kdb]
            [joda-time :refer [date-time to-millis-from-epoch]]
            [clojure.java.jdbc :as jdbc]
            [api.config :as config]))

(defonce $db-config (atom nil))

(defn- init!
  "Initialize db subsystem."
  []
  (reset! $db-config (kdb/postgres (-> (config/lookup) :database)))
  (kdb/defdb $the-db @$db-config))

(defn db-version
  "Gets the current version of the database"
  []
  (if-not @$db-config (init!))
  (let [v (:version
           (first
            (jdbc/query
             @$db-config
             ["SELECT version FROM migrations ORDER BY version DESC LIMIT 1"])))]
    (Long/parseLong (or v "0"))))

(defn update-db-version
  "Updates the current version of the database"
  [version]
  (if-not @$db-config (init!))
  (jdbc/with-db-transaction [t-con @$db-config]
    (jdbc/delete! t-con :migrations ["version IS NOT NULL"])
    (jdbc/insert! t-con :migrations {:version version})))
