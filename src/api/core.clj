(ns api.core
  (:require [clojure.tools.logging :as log]
            [compojure.handler :as handler]
            [com.stuartsierra.component :as component]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.session :as session]
            [clj-logging-config.log4j :as log-config]
            [api.components.app :as a]
            [api.components.database :as d]
            [api.components.producer :as p]
            [api.config :as config]
            [api.middleware :refer [wrap-exceptions
                                    wrap-stacktrace
                                    wrap-request-logging]]
            [api.routes :as routes]
            [api.env :as env]))

(def ns-servlet-handler (atom nil))

(defn api-system [app-config]
  (let [{:keys [kafka-producer cookies postgres]} app-config]
    (-> (component/system-map
         :db (d/new-database postgres)
         :cookies cookies
         :k-producer (p/kafka-producer kafka-producer)
         :app (a/api-app app-config))
        (component/system-using {:app {:db :db
                                       :k-producer :k-producer}}))))

(defn start []
  (alter-var-root #'api.system/system component/start))

(defn stop []
  (alter-var-root #'api.system/system (fn [s] (when s (component/stop s)))))

(defn prepare []
  (let [app-config (config/lookup)]
    (alter-var-root #'api.system/system
                    (constantly (api-system app-config)))))

(defn sys-init! []
  (prepare)
  (start))

(defn app
  [options]
  (-> (handler/site routes/all-routes)
      (wrap-params)
      (wrap-keyword-params)
      (session/wrap-session)
      (wrap-exceptions)
      (wrap-stacktrace)
      (wrap-request-logging)
      (wrap-content-type)))

(defn init-app
  []
  (env/init!)

  ;; Configure logging if on tomcat
  (if (not (empty? (. System getProperty "catalina.base")))
    (log-config/set-logger!
     "api"
     :name "catalina"
     :level :info
     :out (org.apache.log4j.FileAppender.
           (org.apache.log4j.PatternLayout.
            "%d{HH:mm:ss} %-5p %22.22t %-22.22c{2} %m%n")
           (str (. System getProperty "catalina.base")
                "/logs/tail_catalina.log")
           true))
    (log-config/set-logger!
     "api"
     :name "console"
     :level :info
     :out (org.apache.log4j.ConsoleAppender.
           (org.apache.log4j.PatternLayout.
            "%d{HH:mm:ss} %-5p %22.22t %-22.22c{2} %m%n"))))

  ;; start nrepl
  (env/when-env "dev"
                (let [s (nrepl-server/start-server :handler cider-nrepl-handler)]
                  (log/info (str "Started cider (nrepl) on " (:port s)))))
  ;;Initialize system components (db, kafka producer, etc)
  (sys-init!)
  (log/info :STARTING "NS Servlet")
  (reset! ns-servlet-handler (app {})))

(defn current-app [context]
  (@ns-servlet-handler context))

(defn shutdown-app
  []
  (log/info :SHUTTINGDOWN "NS Servlet")
  (stop))
