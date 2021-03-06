(ns api.components
  (:require
   [com.stuartsierra.component :as component]
   [org.httpkit.server :as http-kit]
   [korma.core :refer :all]
   [korma.db :refer [default-connection]]
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
   [api.lib.coercion-helper :refer [remove-nils]]
   [api.config :as config]
   [api.route :as route]
   [api.kinesis :as kinesis]
   [api.redis :as redis]
   [apollo.core :as apollo]
   [api.system :refer [current-system]]
   [api.models.event :as event])
  (:import
   [java.util UUID]
   [com.mchange.v2.c3p0 ComboPooledDataSource]
   [com.amazonaws.auth.profile ProfileCredentialsProvider]
   [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
   [org.apache.log4j Logger Level]))

;;;;;;;;;;;;;;;;;;;;;;
;;
;; logging component
;;
;;;;;;;;;;;;;;;;;;;;;;

(defrecord LoggingComponent [config]
  component/Lifecycle
  (start [this]
    (if-let [loggly-url (-> config :logging :loggly-url)]
      (do (log-config/set-loggers!
           :root
           (-> config :logging :base))
          (let [^Logger root-logger (log-config/as-logger :root)
                loggly-appender (doto (org.apache.log4j.AsyncAppender.)
                                  (.setName "async")
                                  (.setLayout (net.logstash.log4j.JSONEventLayoutV1.))
                                  (.setBlocking false)
                                  (.setBufferSize (int 500))
                                  (.addAppender (doto (com.promotably.proggly.LogglyAppender.)
                                                  (.setName "loggly")
                                                  (.setLayout (net.logstash.log4j.JSONEventLayoutV1.))
                                                  (.logglyURL loggly-url))))]
            (doto root-logger
              (.addAppender loggly-appender))
            (log/info "Loggly appender is attached?" (.isAttached root-logger loggly-appender))))
      (log-config/set-loggers!
       :root
       (-> config :logging :base)))
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
    (log/info (format "Starting cider (nrepl) on %d" port))
    (assoc this :server (clojure.tools.nrepl.server/start-server
                         :port port
                         :handler cider.nrepl/cider-nrepl-handler)))
  (stop [this]
    (when (:server this)
      (log/info (format "Stopping cider (nrepl)"))
      (clojure.tools.nrepl.server/stop-server (:server this)))
    (dissoc this :server)))

;;;;;;;;;;;;;;;;;;;;;;
;;
;; AWS Component
;;
;;;;;;;;;;;;;;;;;;;;;;

(defrecord AWSComponent [config logging]
  component/Lifecycle
  (start [this]
    (let [credential-profile (-> config :aws :credential-profile)
          cp (if-not (empty? credential-profile)
               (ProfileCredentialsProvider. credential-profile)
               (DefaultAWSCredentialsProviderChain.))]
      (log/logf :info "AWS Component established.")
      (-> this
          (assoc :credential-provider cp)
          (assoc :credential-profile credential-profile))))
  (stop [this]
    (log/logf :info "AWS Component de-established.")
    (dissoc this :credential-provider :credential-profile)))

;;;;;;;;;;;;;;;;;;;;;;
;;
;; Database Component
;;
;;;;;;;;;;;;;;;;;;;;;;

;; Database component, just initializes the connection pool for Korma.
(defrecord DatabaseComponent [config logging connection-pool]
  component/Lifecycle
  (start [this]
    ;; Initialize Korma db connection pool.
    (let [{{:keys [db user password host port]} :database} config
          cpds (doto (ComboPooledDataSource.)
                 (.setDriverClass "org.postgresql.Driver")
                 (.setJdbcUrl (str "jdbc:postgresql://" host ":" port "/" db))
                 (.setUser user)
                 (.setPassword password)
                 ;; expire excess connections after 30 minutes of inactivity:
                 (.setMaxIdleTimeExcessConnections (* 30 60))
                 ;; expire connections after 3 hours of inactivity:
                 (.setMaxIdleTime (* 3 60 60))
                 (.setConnectionCustomizerClassName "com.promotably.api.ConnectionCustomizer"))]
      (log/logf :info "Connecting to DB at %s:%s" host port)
      (assoc this :connection-pool {:datasource cpds})
      (default-connection {:datasource cpds})))
  (stop [this]
    (.close (-> this :connection-pool :datasource))
    (dissoc this :connection-pool)))


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

(defn session-expired
  [kinesis-spec cloudwatch-recorder msg]
  (let [[type channel session-id] msg
        uuid (if (string? session-id) (java.util.UUID/fromString session-id))
        start (if uuid (event/last-event-by-session-id uuid "session-start"))]
    (when (and (string? session-id) uuid start)
      (kinesis/record-event! kinesis-spec
                             :session-end
                             (-> (start :data)
                                 (assoc :event-name "session-end")
                                 (assoc :event-format-version 1)
                                 (dissoc :request-headers)))
      (let [dims {:site-id (-> start :data :site-id str)}]
        (cloudwatch-recorder "session-end" 1 :Count)
        (cloudwatch-recorder "session-end" 1 :Count :dimensions dims)))))

(defrecord SessionCacheComponent [config logging redis kinesis cloudwatch]
  component/Lifecycle

  (start [this]
    (log/logf :info "Cache is starting.")
    (try
      (car/wcar (:conn redis)
                (car/config-set "notify-keyspace-events" "KEx"))
      (catch Throwable t))
    (let [listener (car/with-new-pubsub-listener (-> redis :conn :spec)
                     {"__keyevent@0__:expired" (fn [msg] (session-expired
                                                          kinesis
                                                          (:recorder cloudwatch)
                                                          msg))}
                     (car/subscribe  "__keyevent@0__:expired"))]
      (assoc this :listener listener)))

  (stop [this]
    (log/logf :info "Cache is shutting down.")
    (when-let [l (:listener this)]
      (car/wcar (:conn redis) (car/close-listener l)))
        (dissoc this :listener))

  ss/SessionStore
  (read-session [this session-id]
    (when session-id
      (let [data (redis/wcar* (car/get session-id))]
        (or data {}))))
  (write-session [this session-id data]
    (let [session-id* (or session-id (str (UUID/randomUUID)))
          old-data (redis/wcar* (car/get session-id*))
          new-data (remove-nils (merge old-data data))
          s (-> config :session-length-in-seconds)
          cloudwatch-recorder (:recorder cloudwatch)]
      (try
        (redis/wcar*
         (car/set session-id* new-data)
         (car/expire session-id* s))
        (catch Throwable t
          (log/logf :error "Session redis error: %s" (pr-str t))
          (cloudwatch-recorder "session-error" 1 :Count)))
      session-id*))
  (delete-session [this session-id]
    (try
      (redis/wcar* (car/del session-id))
      (catch Throwable t
        (log/logf :error "Session redis error: %s" (pr-str t))
        (let [cloudwatch-recorder (:recorder cloudwatch)]
          (cloudwatch-recorder "session-error" 1 :Count))))
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; AWS Cloudwatch Component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord Cloudwatch [config logging aws]
  component/Lifecycle
  (start [this]
    (let [{:keys [delay-seconds interval-seconds]} (:cloudwatch config)
          cp (:credential-provider aws)
          c (apollo/create-async-cw-client :provider cp)
          s (apollo/create-vacuum-scheduler)
          recorder-namespace (str "api-" (name (:env config)))]
      (log/infof "Cloudwatch is starting with credential profile '%s'." (-> config :aws :credential-profile))
      (apollo/start-vacuum-scheduler! delay-seconds interval-seconds s c)
      (log/infof "Cloudwatch Recording Namespace: %s" recorder-namespace)
      (-> this
          (assoc :client c)
          (assoc :scheduler s)
          (assoc :recorder (apollo/get-context-recorder recorder-namespace {})))))
  (stop [this]
    (log/info "Cloudwatch is stopping")
    (apollo/stop-vacuum-scheduler! (:scheduler this))))


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
   :aws           (component/using (map->AWSComponent {}) [:config])
   :database      (component/using (map->DatabaseComponent {}) [:config :logging])
   :kinesis       (component/using (kinesis/map->Kinesis {}) [:config :logging :aws])
   :cloudwatch    (component/using (map->Cloudwatch {}) [:config :logging :aws])
   :cider         (component/using (map->ReplComponent {:port (java.lang.Integer. repl-port)}) [:config :logging])
   :redis         (component/using (map->RedisComponent {}) [:config :logging])
   :session-cache (component/using (map->SessionCacheComponent {}) [:config :logging :redis :kinesis :cloudwatch])
   :router        (component/using (route/map->Router {}) [:config :logging :session-cache :aws :cloudwatch])
   :server        (component/using (map->Server {:port (java.lang.Integer. port)}) [:config :logging :router])))
