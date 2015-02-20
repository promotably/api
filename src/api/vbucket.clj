(ns api.vbucket
  (:require [api.kinesis :as kinesis]
            [api.system :refer [current-system]]
            [api.cloudwatch :refer [put-metric] :as cw]))

(def not-nil? (complement nil?))

(def total-vbuckets 100)

(defn pick-vbucket
  "Do a modulus using vbucket-total to determine virtual bucket"
  [uuid]
  (mod (hash uuid) total-vbuckets))

(defn pick-bucket
  "Choose test or control sybmol based on a 50/50 split"
  [uuid test-override]
  (cond
    (not-nil? test-override) :test
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
  (fn [request]
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
          bucket-id (or ssid sid session-id (str (java.util.UUID/randomUUID)))
          test-override (:xyzzy (:params request)) ;; xyzzy param forces :test bucket
          bucket (pick-bucket bucket-id test-override)
          assignment-data {:event-format-version "1"
                           :event-name "bucket-assigned"
                           :site-shopper-id ssid
                           :site-id site-id
                           :bucket bucket
                           :shopper-id sid}]

      (let [response (handler (assoc-in request [:session :test-bucket] bucket))]
          (assoc response :new-bucket-assignment assignment-data)))))

