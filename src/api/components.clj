(ns api.components
  (:require [com.stuartsierra.component :as component]
            [korma.core :refer :all]
            [korma.db :refer [defdb postgres]]
            [clojure.java.jdbc :as jdbc]
            [joda-time :as jt]
            [clojure.core.reducers :as r]
            [clojure.tools.logging :as log]
            [ring.middleware.session.store :as ss]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [clj-logging-config.log4j :as log-config])
  (:import (java.util.concurrent Executors TimeUnit
                                 ScheduledExecutorService)
           [java.util UUID]))

;;;;;;;;;;;;;;;;;;;;;;
;;
;; Database Component
;;
;;;;;;;;;;;;;;;;;;;;;;

(defn init-pool
  "Initialize Korma db connection pool."
  [config]
  (defdb $the-db (postgres config)))

;; Database component, just initializes the connection pool for Korma.
(defrecord Database [config]
  component/Lifecycle
  (start [this]
    (init-pool config)
    this)
  (stop [this]
    this))

(defn init-database
  "Constructor for our database component."
  [config]
  (map->Database {:config config}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Session Cache Component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- clean-cache
  "The callback for the timer thread that removes expired items."
  [cache]
  (swap! cache (fn [c]
                 (remove (fn [[session-key data]]
                           (jt/after? (:expires data) (jt/date-time))) c))))

(defn- init-scheduler
  "Initialize the scheduled executor that removes expired items from the
  cache."
  [cache ^ScheduledExecutorService scheduler]
  (.scheduleWithFixedDelay
   scheduler
   #(clean-cache cache)
   5 5 TimeUnit/MINUTES))

;; Session cache component. Starts a scheduled thread to remove old items
;; out of the cache.
(defrecord ApiSessionCache [cache scheduler]
  component/Lifecycle
  (start [this]
    (log/info :INITIALIZING "Cache is starting...")
    (init-scheduler cache scheduler)
    this)
  (stop [this]
    (log/info :SHUTTINGDOWN "Cache is shutting down...")
    (.shutdownNow ^ScheduledExecutorService scheduler)
    (reset! cache nil)
    this)
  ss/SessionStore
  (read-session [this session-id]
    (when session-id
      (session-id @cache)))
  (write-session [this session-id data]
    (let [session-id* (or session-id
                          (UUID/randomUUID))
          expires (jt/plus (jt/date-time) (jt/hours 2))
          data* (assoc (merge (session-id* @cache) data)
                  :expires expires)]
      (swap! cache (fn [c]
                     (assoc c session-id* data*)))
      session-id*))
  (delete-session [this session-id]
    (swap! cache (fn [c]
                   (dissoc c [session-id])))
    nil))

(defn api-session-cache
  "Constructor for our custom ring session store."
  []
  (let [session-cache (atom {})
        scheduler (Executors/newSingleThreadScheduledExecutor)]
    (->ApiSessionCache session-cache scheduler)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; High Level Application System
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def app-system-components
  "The components whose lifecycles are managed by the application system."
  [:database :session-cache])

(defn configure-logging
  "Configure logging for the application."
  [log-conf]
  (log-config/set-logger!
   "api"
   :name (:name log-conf)
   :level (:level log-conf)
   :out (:out log-conf)))

(defn start-nrepl
  "Start an nrepl server. Usually only in dev mode."
  []
  (let [s (nrepl-server/start-server :handler cider-nrepl-handler)]
    (log/info (str "Started cider (nrepl) on " (:port s)))))


;; Our master system component.
(defrecord ApplicationSystem [config env database session-cache]
  component/Lifecycle
  (start [this]
    (log/info "Starting Application System Components...")
    (configure-logging (:logging config))
    (when (= :dev env)
      (start-nrepl))
    (component/start-system this app-system-components))
  (stop [this]
    (log/info "Stopping Application System Components...")
    (component/stop-system this app-system-components)))

(defn application-system
  "Constructor for our main system component."
  [config database session-cache]
  (map->ApplicationSystem {:config config
                           :env (:env config)
                           :database database
                           :session-cache session-cache}))
