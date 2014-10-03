(ns api.models.offer-condition
  (:require [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher dash-to-underscore-keys]]
            [api.lib.schema :refer :all]
            [api.models.redemption :as redemption]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [korma.db :refer [transaction]]
            [clojure.set :refer [rename-keys intersection]]
            [clj-time.core :refer [before? after? now]]
            [schema.core :as s]
            [schema.coerce :as sc]))

(defn db-to-condition
  [r]
  (let [matcher sc/string-coercion-matcher
        base (sc/coercer BaseOfferCondition matcher)
        final (sc/coercer OutboundOfferCondition matcher)
        ks (keys r)
        remove-nils (fn [x] (apply dissoc x (for [[k v] x :when (nil? v)] k)))]
    (-> (rename-keys r (zipmap ks (map hyphenify-key ks)))
        remove-nils
        (dissoc :uuid :offer-id :id)
        base
        final)))

(defn- jdbc-array->seq
  [^org.postgresql.jdbc4.Jdbc4Array jdbc-array]
  (when-not (nil? jdbc-array)
    (seq (.getArray jdbc-array))))

(defn- condition-to-db
  [{:keys [type] :as condition}]
  (dash-to-underscore-keys
   ((sc/coercer DatabaseOfferCondition
                (sc/first-matcher [custom-matcher
                                   sc/string-coercion-matcher]))
    (merge condition {:type (name type)}))))

;; (condition-to-db (first x))

(defn create-conditions!
  [c]
  (when-let [coerced (seq (map condition-to-db c))]
    (def y coerced)
    (doall (map
            #(do
               (insert offer-conditions (values %)))
            coerced))))

(defn delete-conditions!
  [offer-id]
  (delete offer-conditions (where {:offer_id offer-id})))

(defn update-conditions!
  [promo-id c]
  (transaction
   (delete-conditions! promo-id)
   (create-conditions! c)))
