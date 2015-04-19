(ns ^{:author "smnirven"
      :doc "Here lies code for interacting with AWS Kinesis"}
  api.kinesis
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream]
   [com.amazonaws.services.kinesis AmazonKinesisClient]
   [com.amazonaws.auth.profile ProfileCredentialsProvider]
   [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
   [java.nio ByteBuffer])
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [amazonica.aws.kinesis :refer (put-record)]
   [clojure.tools.logging :as log]
   [cognitect.transit :as transit]))

(comment

(defn- update-stats
  [q]
  (prn "update")
  {:foo 1})

(def q-stats (atom {}))
(def from-callers (async/chan))
(def to-kinesis (async/chan 10))
(async/>!! from-callers "foo")
(async/>!! to-kinesis "foo")
(async/<!! to-kinesis)

(prn (async/alts!! [(async/timeout 1000)
                    [from-callers "fooz"]]))

(async/go
  (prn (async/alts! [(async/timeout 1000) from-callers])))

(async/<!! to-kinesis)

;; (queue-process-uncontrolled from-callers to-kinesis)

(defn queue-process-uncontrolled
  [input output stats]
  (async/go
    (loop [q clojure.lang.PersistentQueue/EMPTY]
      (let [[val-to-q ch] (async/alts!
                           (if-let [v (peek q)]
                             [input [output v]]
                             [input]))]
        (swap! stats update-stats q)
        (cond
         ;; Read a value from input.
         val-to-q (recur (conj q val-to-q))

         ;; Input channel is closed. => drain queue.
         (identical? ch input) (doseq [v q] (async/>! output v))

         ;; Write happened.
         :else (recur (pop q)))))))

)

(defn- record!
  "Records to an AWS Kinesis Stream."
  [^com.amazonaws.services.kinesis.AmazonKinesisClient kinesis-client
   stream-name message-map]
  (try
    (let [^ByteArrayOutputStream out-stream (ByteArrayOutputStream. 4096)
          writer (transit/writer out-stream :json)]
      (transit/write writer message-map)
      (.putRecord kinesis-client
                  stream-name
                  (ByteBuffer/wrap (.toByteArray out-stream))
                  (str (java.util.UUID/randomUUID))))
    (catch Exception e
      (log/errorf "Can't send kinesis message %s" (:event-name message-map))
      (log/warn e (format "Failed to send Kinesis message to %s: %s"
                          stream-name
                          (pr-str message-map))))))

(defn record-event!
  [kinesis event-name attributes]
  ;; TODO: this is grossly inefficient...
  (future
    (record! (:client kinesis)
             (get-in kinesis [:config :kinesis :event-stream-name])
             {:message-id (java.util.UUID/randomUUID)
              :event-name event-name
              :attributes attributes})))

(defn record-promo-action!
  [kinesis action promo site]
  (future
    (record! (:client kinesis)
             (get-in kinesis [:config :kinesis :promo-stream-name])
             {:message-id (java.util.UUID/randomUUID)
              :action action
              :promo promo
              :site site})))

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

