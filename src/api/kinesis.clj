(ns ^{:author "smnirven"
      :doc "Here lies code for interacting with AWS Kinesis"}
  api.kinesis
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.nio ByteBuffer])
  (:require [amazonica.aws.kinesis :refer (put-record)]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]))

(defn- record!
  "Records to an AWS Kinesis Stream."
  [kinesis-client stream-name message-map]
  (try
    (let [^ByteArrayOutputStream out-stream (ByteArrayOutputStream. 4096)
          writer (transit/writer out-stream :json)]
      (transit/write writer message-map)
      (.putRecord kinesis-client
                  stream-name
                  (ByteBuffer/wrap (.toByteArray out-stream))
                  (str (java.util.UUID/randomUUID))))
    (catch Exception e
      (log/warn e (format "Failed to send Kinesis message: %s"
                          (pr-str message-map))))))

(defn record-event!
  [kinesis event-name attributes]
  (future
    (record! (:client kinesis)
             (:event-stream-name kinesis)
             {:message-id (java.util.UUID/randomUUID)
              :event-name event-name
              :attributes attributes})))

(defn record-promo-action!
  [kinesis action promo site]
  (future
    (record! (:client kinesis)
             (:promo-stream-name kinesis)
             {:message-id (java.util.UUID/randomUUID)
              :action action
              :promo promo
              :site site})))
