(ns api.sorting-hat
  (:require [clojure.walk :refer [postwalk prewalk]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clj-time.format]
            [clj-time.coerce :as t-coerce]
            [clj-time.core :as t]
            [api.config :as config]
            [api.kinesis :as kinesis]
            [api.models.helper :refer :all]
            [api.models.site :as site]
            [api.system :refer [current-system]]
            [api.lib.schema :refer :all]
            [taoensso.carmine :as car :refer [wcar]]
            [api.redis :as redis]
            [api.cloudwatch :refer [put-metric] :as cw]
            [schema.coerce :as sc]
            [schema.core :as s]))

;; TODO: Put this in config, make it more robust
(defn pick-bucket
  "Test or control?"
  []
  (if (< (rand) 0.5)
    :test
    :control))

(defn wrap-record-bucket-assignment
  "Record new bucket assignments."
  [handler]
  (fn [{:keys [session] :as request}]
    (let [response (handler request)]
      (when-let [assignment-data (:new-bucket-assignment response)]
        (cw/put-metric "bucket-assigned")
        (kinesis/record-event! (:kinesis current-system) "bucket-assigned"
                               (assoc assignment-data :session-id (:session/key request))))
      response)))

(defn wrap-sorting-hat
  "Bucket the visitor."
  [handler]
  (fn [{:keys [session] :as request}]
    (if-let [session-bucket (:test-bucket session)]
      (handler request)
      (let [site-id (or
                     (-> request :form-params :site-id)
                     (-> request :query-params :site-id)
                     (-> request :multipart-params :site-id)
                     (-> request :body-params :site-id)
                     (-> request :params :site-id))
            session-id (:session/key request)
            sid (:shopper-id request)
            ssid (or
                  (-> request :form-params :site-shopper-id)
                  (-> request :query-params :site-shopper-id)
                  (-> request :multipart-params :site-shopper-id)
                  (-> request :body-params :site-shopper-id)
                  (-> request :params :site-shopper-id))
            redis-key (str "bucket/" ssid)
            saved-bucket (redis/wcar* (car/get redis-key))
            bucket (or saved-bucket (pick-bucket))
            s (-> current-system :config :bucket-assignment-length-in-seconds)
            assignment-data {:event-format-version "1"
                             :event-name "bucket-assigned"
                             :site-shopper-id ssid
                             :site-id site-id
                             :bucket bucket
                             :shopper-id sid}]
        (when-not saved-bucket
          (try
            (redis/wcar*
             (car/set redis-key bucket)
             (car/expire redis-key s))
            (catch Throwable t
              (log/logf :error "Bucket assignment error: %s" (pr-str t))
              (cw/put-metric "sorting-hat-error"))))
        (let [response (handler (assoc-in request [:session :test-bucket] bucket))]
          (if saved-bucket
            response
            (do
              (assoc response :new-bucket-assignment assignment-data))))))))
