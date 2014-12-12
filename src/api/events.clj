(ns api.events
  (:require [clojure.walk :refer [postwalk prewalk]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clj-time.format]
            [clj-time.coerce]
            [api.kinesis :as kinesis]
            [api.models.helper :refer :all]
            [api.models.site :as site]
            [api.lib.schema :refer :all]
            [api.lib.coercion-helper :refer [transform-map
                                             make-trans
                                             remove-nils
                                             custom-matcher
                                             underscore-to-dash-keys]]
            [api.lib.auth :refer [parse-auth-string auth-valid? transform-auth]]
            [api.cloudwatch :refer [put-metric]]
            [schema.coerce :as sc]
            [schema.core :as s]))

(def rename-pn
  (make-trans #{:product-name}
              (fn [k v]
                [:title v])))

(def fix-mod
  (make-trans #{:modified-at}
              (fn [k v]
                [k (-> v clj-time.format/parse clj-time.coerce/to-date)])))

(def del-unused
  (make-trans #{:_ :callback} (constantly nil)))

(def fix-en
  (make-trans #{:event-name}
              #(do
                 (vector %1 (-> %2
                                (subs 1)
                                clojure.string/lower-case
                                keyword)))))

(def fix-applied-coupons
  (make-trans #{:applied-coupons}
              #(do
                 ;; (prn %1 %2 (mapcat (fn [c] [{:code c}]) %2))
                 (vector %1 (mapcat (fn [c] [{:code c}]) %2)))))

(def coerce-site-id
  (make-trans #{:site-id}
              #(vector :site (if (string? %2)
                               (-> %2 java.util.UUID/fromString site/find-by-site-uuid)
                               nil))))

(def fix-cart-items
  (make-trans
   #{:cart-item}
   (fn [k items]
     [:cart-items
      (if (seq items)
        (mapcat #(let [[id title category var-id var q subtotal total]
                       (clojure.string/split % #"," 8)]
                   [{:id id
                     :title title
                     :categories (clojure.string/split category #"\|")
                     :variation-id var-id
                     :variation var
                     :quantity q
                     :subtotal subtotal
                     :total total}])
                items))])))

(defn prep-incoming
  [params]
  (let [dbg (partial prn "---")]
    (-> params
        del-unused
        underscore-to-dash-keys
        remove-nils
        ;; (doto dbg)
        fix-en
        fix-cart-items
        transform-auth
        fix-applied-coupons
        coerce-site-id
        rename-pn
        fix-mod)))

(defn parse-event
  "Convert an incoming event to a regular data structure."
  [r]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer InboundEvent matcher)]
    (-> r
        prep-incoming
        coercer)))

(defn record-event
  [kinesis-comp
   {:keys [params] :as request}]
  (put-metric "event-record")
  ;; for debug
  ;; (prn "PARAMS" params)
  (let [parsed (parse-event params)
        product-view-count (get-in request [:session :product-view-count] 0)]
    ;; for debug
    ;; (prn "PARSED" parsed)
    (cond
     (= schema.utils.ErrorContainer (type parsed))
     (do
       (put-metric "event-record-parse-error")
       {:status 400})

     (nil? (:site parsed))
     (do
       (put-metric "event-record-unknown-site")
       {:status 404})

     (not (auth-valid? (-> parsed :site :site-id)
                       (-> parsed :site :api-secret)
                       (:auth parsed)
                       request))
     (do
       (put-metric "event-record-auth-error")
       {:status 403})

     :else
     (do
       (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
             coercer (sc/coercer OutboundEvent matcher)
             out (-> parsed
                     (dissoc :auth :site)
                     (assoc :visitor-id (:visitor-id request))
                     (assoc :site-id (-> parsed :site :site-id))
                     coercer)]
         ;; TODO: check return val...
         (kinesis/record-event! kinesis-comp
                                (:event-name out)
                                out)
         (put-metric "event-record-success")
         (let [response {:headers {"Content-Type" "text/javascript"}
                         :body ""
                         :status 200}]
           (if (= (:event-name out) :trackproductview)
             (merge response {:session {:product-view-count (inc product-view-count)}})
             response)))))))
