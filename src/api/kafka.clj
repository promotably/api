(ns api.kafka
  (:require [api.system :refer [system]]
            [port-kafka.messages :as m]
            [port-kafka.producer :as p]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema.macros :as sm]))

(def topic "PromotablyAPIEvents")

(defn record!
  [attributes]
  (let [producer (get-in system [:app :producer])
        message {:message-id (java.util.UUID/randomUUID)
                 :attributes attributes}]
    (future
      (try (p/send! producer (m/create-message topic message :msgpack))
           (catch Exception e
             (log/warn e (str "Failed to send Kafka message: %s" message)))))))
