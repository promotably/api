(ns api.events
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
            [api.models.promo :as promo]
            [api.models.offer :as offer :refer [fallback-to-exploding
                                                lookup-exploding]]
            [api.lib.schema :refer :all]
            [api.lib.coercion-helper :refer [transform-map
                                             make-trans
                                             remove-nils
                                             custom-matcher
                                             underscore-to-dash-keys]]
            [api.lib.auth :refer [parse-auth-string auth-valid? transform-auth]]
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
                                (clojure.string/replace "track" "")
                                keyword)))))

(defn make-coupon-fixer
  [site-uuid]
  (make-trans
   #{"applied-coupon[]"}
   (fn [k items]
     [:applied-coupons
      (let [items (cond
                   (string? items) [items]
                   (seq items) items
                   :else nil)]
        (mapcat #(let [[code discount] (clojure.string/split % #"," 2)
                       c (clojure.string/upper-case code)
                       p (promo/find-by-site-uuid-and-code site-uuid c)
                       [o-id o-promo] (lookup-exploding site-uuid c)]
                   [{:code c
                     :promo-uuid (:uuid (or p o-promo))
                     :offer-uuid o-id
                     :discount (str discount)}])
                items))])))

(def fix-shipping-methods
  (make-trans
   #{"shipping-method[]"}
   (fn [k items]
     [:shipping-methods
      (let [items (cond
                   (string? items) [items]
                   (seq items) items
                   :else nil)]
        (mapcat #(let [[method cost] (clojure.string/split % #"," 2)]
                   [{:cost cost :method method}])
                items))])))

(def coerce-site-id
  (make-trans #{:site-id}
              #(vector :site (if (string? %2)
                               (-> %2 java.util.UUID/fromString site/find-by-site-uuid)
                               nil))))

(def fix-cart-items
  (comp
   (fn [m]
     (if (and (not (#{:productview :productadd :offershown} (:event-name m)))
              (not (contains? m :cart-items)))
       (assoc m :cart-items [])
       m))
   (make-trans
    #{"cart-item[]"}
    (fn [k items]
      [:cart-items
       (let [items (cond
                    (string? items) [items]
                    (seq items) items
                    :else nil)]
         (mapcat #(let [[sku title category var-id var q subtotal total]
                        (clojure.string/split % #"," 8)]
                    [{:sku sku
                      :title title
                      :categories (clojure.string/split category #"\|")
                      :variation-id var-id
                      :variation var
                      :quantity q
                      :subtotal subtotal
                      :total total}])
                 items))]))))

(defn prep-incoming
  [params]
  (let [dbg (partial prn "---")
        fix-applied-coupons (make-coupon-fixer (-> params
                                                   :site-id
                                                   java.util.UUID/fromString))]
    (-> params
        del-unused
        underscore-to-dash-keys
        remove-nils
        ;; (doto dbg)
        fix-en
        fix-cart-items
        fix-shipping-methods
        transform-auth
        fix-applied-coupons
        coerce-site-id
        rename-pn
        fix-mod)))

(defn parse-event
  "Convert an incoming event to a normalized data structure."
  [r]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer InboundEvent matcher)]
    (-> r
        prep-incoming
        coercer)))

(defn record-event
  [kinesis-comp {:keys [session params cookies cloudwatch-recorder] :as request}]
  (cloudwatch-recorder "event-record" 1 :Count :dimensions {:endpoint "events"})
  ;; for debug
  ;; (prn "PARAMS" params)
  (let [base-response {:context {:cloudwatch-endpoint "events-track"}}
        event-params (assoc params :control-group (= (:test-bucket (:session request)) :control))
        parsed (parse-event event-params)]
    ;; for debug
    ;; (prn "PARSED" parsed)
    (cond
     (= schema.utils.ErrorContainer (type parsed))
     (do
       (log/logf :error "Event parse error: %s, params: %s" (pr-str parsed) params)
       (cloudwatch-recorder "event-record-parse-error" 1 :Count :dimensions {:endpoint "events"})
       (merge base-response {:status 400 :session (:session request)}))

     (nil? (:site parsed))
     (do
       (cloudwatch-recorder "event-record-unknown-site" 1 :Count :dimensions {:endpoint "events"})
       (merge base-response {:status 404 :session (:session request)}))

     (and
      (not (= (:event-name params) "_trackOfferShown"))
      (not (auth-valid? (-> parsed :site :site-id)
                        (-> parsed :site :api-secret)
                        (:auth parsed)
                        request)))
     (do
       (cloudwatch-recorder "event-record-auth-error" 1 :Count :dimensions {:endpoint "events"})
       (merge base-response {:status 403 :session (:session request)}))

     :else
     (do
       (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
             coercer (sc/coercer OutboundEvent matcher)
             out (-> parsed
                     (dissoc :auth :site)
                     (assoc :site-shopper-id (-> request :params :site-shopper-id))
                     (assoc :shopper-id (:shopper-id request))
                     (assoc :site-id (-> parsed :site :site-id))
                     (assoc :session-id (get-in cookies [config/session-cookie-name :value]))
                     (assoc :control-group (= (:test-bucket session)
                                              :control))
                     coercer
                     (assoc :event-format-version "1"))]
         ;; For debugging
         ;; (clojure.pprint/pprint out)
         (when (= schema.utils.ErrorContainer (type out))
           (log/logf :error
                     "Tracking event in invalid format: %s %s"
                     (pr-str parsed)
                     (pr-str out))
           (cloudwatch-recorder "event-format-invalid" 1
                                :Count :dimensions {:endpoint "events"}))
         (kinesis/record-event! kinesis-comp (:event-name out) out)
         (cloudwatch-recorder "event-record-success" 1
                              :Count :dimensions {:endpoint "events"})
         (let [session (cond-> (:session request)
                               (#{:productadd :cartview :cartupdate :checkout}
                                (:event-name out))
                                 (assoc :last-cart-event out)
                               true
                                 (assoc :last-event-at (t-coerce/to-string (t/now))))
               response (merge base-response {:headers {"Content-Type" "text/javascript"}
                                              :body ""
                                              :session session
                                              :status 200})]
           response))))))
