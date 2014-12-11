(ns api.components
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http-kit]
            [korma.core :refer :all]
            [korma.db :refer [defdb postgres]]
            [clojure.java.jdbc :as jdbc]
            [joda-time :as jt]
            [clojure.core.reducers :as r]
            [clojure.tools.logging :as log]
            [ring.middleware.session.store :as ss]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [clj-logging-config.log4j :as log-config]
            [api.config :as config]
            [api.route :as route])
  (:import (java.util.concurrent Executors TimeUnit
                                 ScheduledExecutorService)
           [java.util UUID]
           [com.amazonaws.services.kinesis AmazonKinesisClient]
           [com.amazonaws.auth.profile ProfileCredentialsProvider]
           [com.amazonaws.auth DefaultAWSCredentialsProviderChain]))

;;;;;;;;;;;;;;;;;;;;;;
;;
;; logging component
;;
;;;;;;;;;;;;;;;;;;;;;;

(defrecord LoggingComponent [config]
  component/Lifecycle
  (start [this]
    (log-config/set-logger!
     "api"
     :name (-> config :logging :name)
     :level (-> config :logging :level)
     :out (-> config :logging :out))
    (log/logf :info "Environment is %s" (-> config :env))
    this)
  (stop [this]
    this))

;;;;;;;;;;;;;;;;;;;;;;
;;
;; repl component
;;
;;;;;;;;;;;;;;;;;;;;;;

(defrecord ReplComponent [port config logging]
  component/Lifecycle
  (start [this]
    (when ((-> config :env) #{:dev :localdev})
      (log/info (format "Starting cider (nrepl) on %d" port))
      (assoc this :server (clojure.tools.nrepl.server/start-server
                           :port port
                           :handler cider.nrepl/cider-nrepl-handler))))
  (stop [this]
    (if ((-> config :env) #{:dev :localdev})
      (when (:server this)
        (log/info (format "Stopping cider (nrepl)"))
        (clojure.tools.nrepl.server/stop-server (:server this))))
    (dissoc this :server)))

;;;;;;;;;;;;;;;;;;;;;;
;;
;; Database Component
;;
;;;;;;;;;;;;;;;;;;;;;;

;; Database component, just initializes the connection pool for Korma.
(defrecord DatabaseComponent [config logging]
  component/Lifecycle
  (start [this]
    ;; Initialize Korma db connection pool.
    (log/logf :info
              "Connecting to DB at %s:%s"
              (-> config :database :host)
              (pr-str (-> config :database :port)))
    (assoc this :db (defdb $the-db (postgres (:database config)))))
  (stop [this]
    (dissoc this :db)))

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

;; Session cache component. Starts a scheduled thread to remove old items
;; out of the cache.
(defrecord SessionCacheComponent [config logging]
  component/Lifecycle
  (start [this]
    (log/info :INITIALIZING "Cache is starting...")
    (let [s (Executors/newSingleThreadScheduledExecutor)
          c (atom {})]
      (.scheduleWithFixedDelay s #(clean-cache c) 5 5 TimeUnit/MINUTES)
      (-> this
          (assoc :cache c)
          (assoc :scheduler s))))
  (stop [this]
    (log/info :SHUTTINGDOWN "Cache is shutting down...")
    (.shutdownNow ^ScheduledExecutorService (:scheduler this))
    (reset! (:cache this) nil)
    (dissoc this :scheduler :cache))
  ss/SessionStore
  (read-session [this session-id]
    (when session-id
      (session-id @(:cache this))))
  (write-session [this session-id data]
    (let [session-id* (or session-id
                          (UUID/randomUUID))
          expires (jt/plus (jt/date-time) (jt/hours 2))
          data* (assoc (merge (session-id* @(:cache this)) data)
                  :expires expires)]
      (swap! (:cache this) (fn [c] (assoc c session-id* data*)))
      session-id*))
  (delete-session [this session-id]
    (swap! (:cache this) (fn [c] (dissoc c [session-id])))
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; AWS Kinesis Component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Kinesis [config logging]
  component/Lifecycle
  (start [this]
    (log/logf :info
              "Kinesis is starting, using credentials for '%s'."
              (-> config :kinesis :aws-credential-profile))
    (let [creds (-> config :kinesis :aws-credential-profile)
          ^com.amazonaws.auth.AWSCredentialsProvider cp
          (if-not (nil? creds)
            (ProfileCredentialsProvider. creds)
            (DefaultAWSCredentialsProviderChain.))
          c (com.amazonaws.services.kinesis.AmazonKinesisClient. cp)]
      (merge this
             (:kinesis config)
             {:client c})))
  (stop [this]
    (log/logf :info "Kinesis is shutting down.")
    (dissoc this :client)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Server component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Server [port config logging router]
  component/Lifecycle
  (start
   [component]
   (if (:stop! component)
     component
     (let [server (-> component
                      :router
                      :ring-routes
                      (http-kit/run-server {:port (or port 0)}))
           port (-> server meta :local-port)]
       (log/logf :info "Web server running on port %d" port)
       (assoc component :stop! server :port port))))
  (stop
   [component]
   (when-let [stop! (:stop! component)]
     (stop! :timeout 250))
   (dissoc component :stop! :router :port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; High Level Application System
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn system
  [{:keys [port repl-port] :as options}]
  (component/system-map
   :config        (component/using (config/map->Config {}) [])
   :logging       (component/using (map->LoggingComponent {}) [:config])
   :database      (component/using (map->DatabaseComponent {}) [:config :logging])
   :kinesis       (component/using (map->Kinesis {}) [:config :logging])
   :cider         (component/using (map->ReplComponent {:port repl-port}) [:config :logging])
   :session-cache (component/using (map->SessionCacheComponent {}) [:config :logging])
   :router        (component/using (route/map->Router {}) [:config :logging :session-cache])
   :server        (component/using (map->Server {:port port}) [:config :logging :router])))
