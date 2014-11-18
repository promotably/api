(ns ^{:author "smnirven"
      :doc "Here lies code for interacting with AWS Kinesis"}
  api.kinesis
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.nio ByteBuffer])
  (:require [amazonica.aws.kinesis :refer (put-record worker!)]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]))

(def event-stream-name (or (System/getenv "EVENT_STREAM_NAME")
                           "dev-PromotablyAPIEvents"))

(def promo-stream-name (or (System/getenv "PROMO_STREAM_NAME")
                           "dev-PromoStream"))

(defn- record!
  "Records to an AWS Kinesis Stream."
  [stream-name message-map]
  (try
    (let [^ByteArrayOutputStream out-stream (ByteArrayOutputStream. 4096)
          writer (transit/writer out-stream :json)]
      (transit/write writer message-map)
      (put-record stream-name
                  (ByteBuffer/wrap (.toByteArray out-stream))
                  (str (java.util.UUID/randomUUID))))
    (catch Exception e
      (log/warn e (str "Failed to send Kinesis message: %s"
                       (pr-str message-map))))))

(defn record-event!
  [event-name attributes]
  (future
    (record! event-stream-name
             {:message-id (java.util.UUID/randomUUID)
              :event-name event-name
              :attributes attributes})))

(defn record-promo-action!
  [action promo site]
  (future
    (record! promo-stream-name
             {:message-id (java.util.UUID/randomUUID)
              :action action
              :promo promo
              :site site})))
