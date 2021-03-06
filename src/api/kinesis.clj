(ns ^{:author "smnirven"
      :doc "Here lies code for interacting with AWS Kinesis"}
  api.kinesis
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream]
   [com.amazonaws.services.kinesis AmazonKinesisClient]
   [com.amazonaws.auth.profile ProfileCredentialsProvider]
   [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
   [java.nio ByteBuffer]
   [java.util UUID])
  (:require
   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [com.stuartsierra.component :as component]
   [amazonica.aws.kinesis :refer (put-record)]
   [clj-time.core :as t]
   [clj-time.format :as tf]
   [clojure.tools.logging :as log]
   [api.system :refer [current-system]]
   [cognitect.transit :as transit]))

;; Allow for some burstiness
(def queue-size 50)

(defn- wrap-message-envelope
  [message-map]
  {:v "1"
   :env (let [env (get-in current-system [:config :env])]
          (if (= :dev env)
            (str "dev-" (System/getProperty "user.name"))
            (name env)))
   :src "api"
   :type (or (:event-name message-map)
             (:action message-map))
   :msg message-map})

(defn- record!
  "Records to an AWS Kinesis Stream."
  [^com.amazonaws.services.kinesis.AmazonKinesisClient kinesis-client
   stream-name message-map]
  (try
    (let [msg (-> message-map
                  wrap-message-envelope
                  (json/write-str :value-fn (fn [k v]
                                              (if (instance? java.util.UUID v)
                                                (str v)
                                                (if (and (coll? v)
                                                         (not (map? v)))
                                                  (map (fn [cv]
                                                         (if (instance? java.util.UUID cv)
                                                           (str cv)
                                                           cv)) v)
                                                  v)))))]
      (.putRecord kinesis-client
                  stream-name
                  (ByteBuffer/wrap (.getBytes msg))
                  (str (UUID/randomUUID))))
    (catch Throwable t
      (log/errorf "Can't send kinesis message %s" (:event-name message-map))
      (log/warn t (format "Failed to send Kinesis message to %s: %s"
                          stream-name
                          (pr-str message-map))))))

(defn- start-consumer
  [queue]
  (future
    (loop [result (async/<!! queue)]
      (record! (:client result)
               (:stream-name result)
               (:message-map result))
      (recur (async/<!! queue)))))

(defn- enqueue!
  "Enqueue a record for sending to kinesis"
  [^com.amazonaws.services.kinesis.AmazonKinesisClient kinesis-client
   queue stream-name message-map]
  (let [timeout (async/timeout 100)
        [res c] (async/alts!! [timeout [queue {:client kinesis-client
                                               :stream-name stream-name
                                               :message-map message-map}]])]
    (when (= timeout c)
      (log/errorf "Can't enqueue kinesis event %s" (:event-name message-map))
      (let [cw (get-in current-system [:cloudwatch :recorder])]
        (cw "kinesis-write-error" 1 :Count)))))

(defn record-event!
  [kinesis event-name attributes]
  (enqueue! (:client kinesis)
            (:queue kinesis)
            (get-in kinesis [:config :kinesis :event-stream-name])
            {:message-id (UUID/randomUUID)
             :recorded-at (tf/unparse (tf/formatters :basic-date-time-no-ms) (t/now))
             :event-name event-name
             :attributes attributes}))

(defn record-promo-action!
  [kinesis action promo site]
  (enqueue! (:client kinesis)
            (:queue kinesis)
            (get-in kinesis [:config :kinesis :promo-stream-name])
            {:message-id (UUID/randomUUID)
             :recorded-at (tf/unparse (tf/formatters :basic-date-time-no-ms) (t/now))
             :action action
             :promo promo
             :site site}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; AWS Kinesis Component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord Kinesis [config logging aws]
  component/Lifecycle
  (start [this]
    (log/logf :info
              "Kinesis is starting, using credentials for '%s'."
              (-> config :aws :credential-profile))
    (let [cp (:credential-provider aws)
          c (com.amazonaws.services.kinesis.AmazonKinesisClient. cp)
          q (async/chan queue-size)
          consumer (start-consumer q)]
      (merge this
             (:kinesis config)
             {:queue q
              :consumer consumer
              :client c})))
  (stop [this]
    (log/logf :info "Kinesis is shutting down.")
    (if-let [c (:consumer this)]
      (future-cancel c))
    (dissoc this :client :queue :consumer)))

;; (future-cancel (-> api.system/current-system :kinesis :consumer))
