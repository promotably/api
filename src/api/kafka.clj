(ns api.kafka
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream])
  (:require [clj-kafka.producer :as kafka]
            [cognitect.transit :as transit]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema.macros :as sm]))

(def event-topic "PromotablyAPIEvents")

(defonce ^:private producer (atom nil))

(defn- brokers-list []
  (or (System/getProperty "KAFKA_BROKERS") "localhost:9092"))

(defn init! []
  (if-let [brokers-list (brokers-list)]
    (do (log/info (str "Connecting to Kafka brokers: " brokers-list))
        ;; More options at http://kafka.apache.org/documentation.html#producerconfigs
        (reset! producer (kafka/producer {"metadata.broker.list" brokers-list
                                          "producer.type" "async"})))
    (log/error "No Kafka brokers provided.")))

;; TODO: don't spin up a thread for every write to kafka - use core.async
(defn record!
  [topic message-map]
  (if @producer
    (let [^ByteArrayOutputStream out (ByteArrayOutputStream. 4096)
          writer (transit/writer out :json)]
      (future
        (try
          (transit/write writer message-map)
          (kafka/send-message @producer (kafka/message topic
                                                       (.toByteArray out)))
          (catch Exception e
            (log/warn e (str "Failed to send Kafka message: %s"
                             (pr-str message-map)))))))
    (log/warn "The Kafka producer has not been initialized.")))

(defn record-event!
  [event-name attributes]
  (record! event-topic {:message-id (java.util.UUID/randomUUID)
                        :event-name event-name
                        :attributes attributes}))
