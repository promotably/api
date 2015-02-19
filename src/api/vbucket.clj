(ns api.vbucket
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

(def total-vbuckets 100)

(defn pick-vbucket
  "Do a modulus using vbucket-total to determine virtual bucket"
  [id]
  (mod (hash id) total-vbuckets))

(defn test-or-control
  "Choose test or control sybmol based on a 50/50 split"
  [uuid override]
  (prn uuid override)
  (cond
    (boolean (resolve 'override)) :test
    (> (pick-vbucket uuid) 50) :test
    :else :control))

(defn wrap-record-vbucket-assignment
  "Record new bucket assignments."
  [handler]
  (fn [{:keys [session] :as request}]
    (let [response (handler request)]
      (when-let [assignment-data (:new-bucket-assignment response)]
        (cw/put-metric "bucket-assigned")
        (kinesis/record-event! (:kinesis current-system) "bucket-assigned"
                               (assoc assignment-data :session-id (:session/key response))))
      response)))

(defn wrap-vbucket
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
            bucket-id (or ssid sid session-id)
            bucket (test-or-control bucket-id (:xyzzy (:params request)))
            assignment-data {:event-format-version "1"
                             :event-name "bucket-assigned"
                             :site-shopper-id ssid
                             :site-id site-id
                             :bucket bucket
                             :shopper-id sid}]

        (let [response (handler (assoc-in request [:session :test-bucket] bucket))]
          response
          (do
            (assoc response :new-bucket-assignment assignment-data)))))))