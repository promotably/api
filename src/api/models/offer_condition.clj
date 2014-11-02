(ns api.models.offer-condition
  (:require [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher dash-to-underscore-keys]]
            [api.lib.redis :refer [get-integer]]
            [api.lib.schema :refer :all]
            [api.models.redemption :as redemption]
            [api.util :refer [hyphenify-key]]
            [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [clojure.string :refer [trim]]
            [korma.core :refer :all]
            [korma.db :refer [transaction]]
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

;; TODO: DRY up with promo-condition/create-conditions!
(defn create-conditions!
  [conditions]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer DatabaseOfferCondition matcher)
        arrify (fn [m k]
                 (assoc m k (sqlfn "string_to_array"
                                   (apply str (interpose "," (map trim (k m))))
                                   ",")))]
    (if (seq conditions)
      (doall (map
              (fn [c]
                (let [coerced (-> c
                                  (assoc :type (name (:type c)))
                                  coercer)
                      undered (dash-to-underscore-keys coerced)
                      fixers (for [[k v] undered :when (vector? v)] k)
                      fixed (reduce
                             arrify
                             undered
                             fixers)]
                  (insert offer-conditions (values fixed))))
              conditions)))))

;; TODO: DRY up with promo-condition/create-conditions!
(defn delete-conditions!
  [offer-id]
  (delete offer-conditions (where {:offer_id offer-id})))

;; TODO: DRY up with promo-condition/create-conditions!
(defn update-conditions!
  [promo-id c]
  (transaction
   (delete-conditions! promo-id)
   (create-conditions! c)))

(defmulti validate
  (fn [context
      {:keys [type] :as condition}]
    (keyword type)))

(defmethod validate :dates
  [context {:keys [start-date end-date] :as condition}]
  (and (after? (now) (from-sql-date start-date))
       (before? (now) (from-sql-date end-date))))

(defmethod validate :product-views
  [{:keys [site-id visitor-id] :as context}
   {:keys [product-views] :as condition}]
  (let [k (str site-id "/" visitor-id "/product-any")
        product-any-views (get-integer k)]
    (>= product-any-views product-views)))
