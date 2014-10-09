(ns api.events
  (:require [clojure.walk :refer [postwalk prewalk]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clj-time.format]
            [clj-time.coerce]
            [api.state :as state]
            [api.kafka :as kafka]
            [api.models.helper :refer :all]
            [api.models.site :as site]
            [api.lib.schema :refer :all]
            [api.lib.coercion-helper :refer [transform-map
                                             custom-matcher
                                             underscore-to-dash-keys]]
            [api.lib.protocols :refer (EventCache insert query)]
            [api.lib.seal :refer [hmac-sha1 url-encode]]
            [schema.coerce :as sc]
            [schema.core :as s]))

(defn parse-auth
  [auth-string]
  (let [parts (clojure.string/split auth-string #"/" 5)
        [scheme headers qs-fields ts sig] parts
        headers (filter #(not (or (nil? %) (= "" %)))
                        (clojure.string/split headers #","))]
    {:scheme scheme
     :qs-fields (clojure.string/split qs-fields #",")
     :timestamp ts
     :signature sig
     :headers headers}))

(defn auth-valid?
  [{:keys [api-secret site-id] :as site}
   {:keys [scheme qs-fields timestamp signature headers] :as auth-record}
   {:keys [body query-string params] :as request}]
  (let [request-headers (:headers request)
        slurped (try (slurp body) (catch Throwable t))
        body-hmac (if-not (or (= "" slurped) (nil? slurped))
                    (hmac-sha1 (.getBytes ^String (str api-secret))
                               (.getBytes ^String slurped)))
        header-values (mapcat #(vector (str % ":" (get request-headers %)))
                              headers)
        header-str (apply str (interpose "\n" header-values))
        qs-fields (filter #(not (or (nil? %) (= "" %))) qs-fields)
        qs-vals (mapcat #(vector (str % "=" (get params (keyword %))))
                        qs-fields)
        qs-str (apply str (interpose "&" qs-vals))
        sign-me (apply str
                       site-id "\n"
                       api-secret "\n"
                       (:server-name request) "\n"
                       (-> request
                           :request-method name clojure.string/upper-case) "\n"
                       (url-encode (:uri request)) "\n"
                       timestamp "\n"
                       body-hmac "\n"
                       header-str "\n"
                       qs-str "\n")
        computed-sig (hmac-sha1 (.getBytes ^String (str api-secret))
                                (.getBytes ^String sign-me))]
    (= computed-sig signature)))

(defn make-trans
  [pred f]
  (fn [m] (transform-map m pred f)))

(def remove-nils
  (make-trans (constantly true)
              #(if (nil? %2) nil [%1 %2])))

(def rename-pn
  (make-trans #{:product-name}
              (fn [k v]
                [:title v])))

(def fix-auth
  (make-trans #{:promotably-auth}
              (fn [k v]
                [:auth (parse-auth v)])))

(def fix-mod
  (make-trans #{:modified-at}
              (fn [k v]
                [k (-> v clj-time.format/parse clj-time.coerce/to-date)])))

(def del-f
  (make-trans #{:_ :callback} (constantly nil)))

(def fix-en
  (make-trans #{:event-name}
              #(do
                 (vector %1 (-> %2
                                (subs 1)
                                clojure.string/lower-case
                                keyword)))))

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
        (mapcat #(let [[id title category var-id var q]
                       (clojure.string/split % #"," 6)]
                   [{:id id
                     :title title
                     :category category
                     :variation-id var-id
                     :variation var
                     :quantity q}])
                items))])))

(defn prep-incoming
  [params]
  (-> params
      remove-nils
      fix-en
      fix-cart-items
      fix-auth
      coerce-site-id
      rename-pn
      fix-mod
      del-f))

(defn parse-event
  "Convert an incoming event to a regular data structure."
  [r]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer InboundEvent matcher)]
    (-> r
        prep-incoming
        coercer)))

(defn record-event
  [{:keys [params] :as request}]
  (let [parsed (parse-event params)]
    (cond

     (= schema.utils.ErrorContainer (type parsed))
     {:status 400}

     (nil? (:site parsed))
     {:status 404}

     (not (auth-valid? (:site parsed) (:auth parsed) request))
     {:status 403}

     :else
     (do
       (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
             coercer (sc/coercer OutboundEvent matcher)
             out (-> parsed
                     (dissoc :auth :site)
                     (assoc :visitor-id (:visitor-id request))
                     (assoc :site-id (-> parsed :site :site-id))
                     coercer)]
         (if-let [cache (state/events-cache)]
           (insert cache out))
         (kafka/record! out)
         {:status 200})))))

