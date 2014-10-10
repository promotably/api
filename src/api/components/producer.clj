(ns api.components.producer
  (:require [com.stuartsierra.component :as component]
            [port-kafka.producer :as p]))

(defrecord KafkaProducer [config producer]
  component/Lifecycle
  (start [this]
    (if producer
      this
      (let [pr (p/create config)]
        (assoc this :producer pr))))
  (stop [this]
    (if producer
      (do (p/close producer)
          (assoc this :producer nil))
      this)))

(defn kafka-producer [config]
  (map->KafkaProducer {:config config}))
