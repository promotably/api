(ns api.events
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [api.state :as state]
            [api.kafka :as kafka]
            [api.models.helper :refer :all]
            [api.models.site :as site]
            [api.lib.schema :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher underscore-to-dash-keys]]
            [api.lib.protocols :refer (EventCache insert query)]
            [schema.coerce :as sc]
            [schema.core :as s]))

;; TODO: this does not work yet.
(defn parse-event
  "Convert an incoming event to a regular data structure."
  [r]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer InboundEvent matcher)
        hyphenified-params (underscore-to-dash-keys r)
        cleaned (apply dissoc
                       hyphenified-params
                       (for [[k v] hyphenified-params :when (nil? v)] k))
        site (first (site/find-by-site-uuid (:site-id cleaned)))
        renamed cleaned]
    (coercer cleaned)))

(defn record-event
  [{:keys [params] :as request}]
  (clojure.pprint/pprint params)
  (let [record (parse-event params)]
    (if-let [cache (state/events-cache)]
      (insert cache record))
    (kafka/record! record)))

;; Below here serious WIP

(defn simple-coercion [schema]
  (s/start-walker
   (fn [s]
     (let [walk (s/walker s)]
       (fn [x]
         (prn s x)
         (if (and (= s s/Keyword) (string? x))
           (walk (keyword x))
           (walk x)))))
   schema))

((simple-coercion InboundEvent)
 {:event-name "_trackProductView",
  :_ "1412713420022",
  :short-description "",
  :modified-at "2014-10-07 15:47:36",
  :product-name "widget",
  :site-id "26b28c70-2144-4427-aee3-b51031b08426",
  :callback "jQuery1110008581734518520534_1412713420021",
  :product-id "W100",
  :promotably-auth
  "hmac-sha1//event-name,product-id,product-name,short-description,site-id/20141007T202039Z/a5f40be72adb92e460e1fc32610989fe22563b31"})
