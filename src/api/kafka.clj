(ns api.kafka
  (:require [clj-kafka.producer :as kafka]
            [clojure.data.fressian :as fress]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema.macros :as sm]))

(def topic "PromotablyAPIEvents")

(def ^:private
  producer (atom nil))

(defn- brokers-list []
  (or (System/getProperty "KAFKA_BROKERS") "localhost:9092"))

(defn init! []
  (if-let [brokers-list (brokers-list)]
    (do (log/info (str "Connecting to Kafka brokers: " brokers-list))
        ;; More options at http://kafka.apache.org/documentation.html#producerconfigs
        (reset! producer (kafka/producer {"metadata.broker.list" brokers-list
                                          "producer.type" "async"})))
    (log/error "No Kafka brokers provided.")))

(defn record!
  [event-name attributes]
  (let [message (fress/write {:message-id (java.util.UUID/randomUUID)
                              :event-name event-name
                              :attributes attributes})]
    (if @producer
      (do
        (future
          (try (kafka/send-message @producer (kafka/message topic (.array message)))
               (catch Exception e
                 (log/warn e (str "Failed to send Kafka message: %s" message))))))
      (log/warn "The Kafka producer has not been initialized."))))
