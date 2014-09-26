(ns api.core
  (:require [clojure.tools.logging :as log]
            [compojure.handler :as handler]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.session :as session]
            [clj-logging-config.log4j :as log-config]
            [api.db :as db]
            [api.middleware :refer [wrap-exceptions
                                    wrap-stacktrace
                                    wrap-request-logging]]
            [api.routes :as routes]
            [api.state]
            [api.env :as env]
            [api.cache :as cache]
            [api.lib.protocols :refer (EventCache init shutdown)]))

(def ns-servlet-handler (atom nil))

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

  (log/info :STARTING "NS Servlet")
  (db/init!)
  (let [events-cache (cache/exporting-event-cache)]
    @(init events-cache)
    (alter-var-root #'api.state/*global-state*
                    (constantly {:events-cache events-cache})))
  (reset! ns-servlet-handler (app {})))

(defn current-app [context]
  (@ns-servlet-handler context))

(defn shutdown-app
  []
  (log/info :SHUTTINGDOWN "NS Servlet")
  (let [{:keys [events-cache]} api.state/*global-state*]
    (when events-cache @(shutdown events-cache))
    (alter-var-root #'api.state/*global-state*
                    (constantly nil))))

