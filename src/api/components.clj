(ns api.components
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http-kit]
            [korma.core :refer :all]
            [korma.db :refer [defdb postgres]]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.reducers :as r]
            [clojure.tools.logging :as log]
            [ring.middleware.session.store :as ss]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [clj-logging-config.log4j :as log-config]
            [clj-time.core :refer [before? after? now] :as t]
            [clj-time.coerce :as t-coerce]
            [taoensso.carmine :as car :refer [wcar]]
            [api.cloudwatch :as cw]
            [api.config :as config]
            [api.route :as route]
            [api.kinesis :as kinesis]
            [api.redis :as redis])
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
;; REDIS Component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord RedisComponent [config logging]
  component/Lifecycle
  (start [this]
    (let [{:keys [host port] :as spec} (-> config :redis)]
      (log/logf :info "Redis connection %s:%s." host port)
      (assoc this :conn {:pool {} :spec spec})))
  (stop [this]
    (log/logf :info "Goodbye Redis.")
    (dissoc this :conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Session Cache Component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord SessionCacheComponent [config logging redis kinesis]
  component/Lifecycle
  (start [this]
    (log/logf :info "Cache is starting.")
    this)
  (stop [this]
    (log/logf :info "Cache is shutting down.")
    this)

  ss/SessionStore
  (read-session [this session-id]
    (when session-id
      (let [data (redis/wcar* (car/get session-id))]
        (or data {}))))
  (write-session [this session-id data]
    (let [session-id* (or session-id (str (UUID/randomUUID)))
          old-data (redis/wcar* (car/get session-id*))
          new-data (if (or (nil? session-id) (nil? old-data))
                     (assoc data :started-at (t-coerce/to-string (t/now)))
                     data)
          s (-> config :session-length-in-seconds)]
      (when (nil? session-id)
        ;; TODO: more data? Shopper's browser, etc???
        (let [k-data {:created-at (t-coerce/to-string (t/now))
                      :shopper-id (:shopper-id data)
                      :session-id session-id*}
              k-data (if (:site-id data) (update-in k-data [:site-id] (constantly (:site-id data))))]
          (kinesis/record-event! kinesis "session-start" k-data)))
      (try
        (redis/wcar*
         (car/set session-id* new-data)
         (car/expire session-id* s))
        (catch Throwable t
          (log/logf :error "Session redis error: %s" (pr-str t))
          (cw/put-metric "session-error" {:config config})))
      session-id*))
  (delete-session [this session-id]
    (try
      (redis/wcar* (car/del session-id))
      (catch Throwable t
        (log/logf :error "Session redis error: %s" (pr-str t))
        (cw/put-metric "session-error" {:config config})))
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
  [{:keys [config-file port repl-port] :as options}]
  (component/system-map
   :config        (component/using (config/map->Config options) [])
   :logging       (component/using (map->LoggingComponent {}) [:config])
   :database      (component/using (map->DatabaseComponent {}) [:config :logging])
   :kinesis       (component/using (map->Kinesis {}) [:config :logging])
   :cider         (component/using (map->ReplComponent {:port (java.lang.Integer. repl-port)}) [:config :logging])
   :redis         (component/using (map->RedisComponent {}) [:config :logging])
   :session-cache (component/using (map->SessionCacheComponent {}) [:config :logging :redis :kinesis])
   :router        (component/using (route/map->Router {}) [:config :logging :session-cache])
   :server        (component/using (map->Server {:port (java.lang.Integer. port)}) [:config :logging :router])))


