(ns api.system
  (:require [api.components :refer [application-system api-session-cache init-database]]
            [api.config :as config]
            [api.server]
            [com.stuartsierra.component :as component]))

(def servlet-handler (api.server/app {}))

(defn sys-components
  [{:keys [database] :as conf}]
  (application-system conf
                      (init-database database)
                      (api-session-cache)))

(defn init
  [conf]
  (alter-var-root #'api.server/system
                  (constantly (sys-components conf))))

(defn start
  []
  (alter-var-root #'api.server/system
                  component/start))

(defn stop
  []
  (alter-var-root #'api.server/system
                  (fn [sys] (when sys (component/stop sys)))))

(defn init-servlet
  []
  (let [conf (config/lookup)]
    (init conf)
    (start)))