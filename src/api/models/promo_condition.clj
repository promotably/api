(ns api.models.promo-condition
  (:require
   [clojure.string :refer [trim]]
   [api.entities :refer :all]
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

(defn- db-to-condition
  [r]
  (let [ks (keys r)
        hyphenified-params (rename-keys r (zipmap ks (map hyphenify-key ks)))]
    ((sc/coercer PromoCondition
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     hyphenified-params)))

(defn create-conditions!
  [conditions]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer DatabaseCondition matcher)
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
                  (insert promo-conditions (values fixed))))
              conditions)))))

(defn delete-conditions!
  [promo-id]
  (delete promo-conditions (where {:promo_id promo-id})))

(defn update-conditions!
  [promo-id c]
  (transaction
   (delete-conditions! promo-id)
   (create-conditions! c)))

(defmulti validate
  (fn [{:keys [type]} context] type))

(defmethod validate :dates
  [{:keys [start-date end-date] :as condition} context]
  (cond
   :else {:valid true}))

(defmethod validate :total-discounts
  [{:keys [total-discounts] :as condition} context]
  (cond
   :else {:valid true}))

(defmethod validate :usage-count
  [{:keys [usage-count id] :as condition}
   {:keys [shopper-email] :as context}]
  (let [rc (redemption/count-by-promo-and-shopper-email id shopper-email)]
    (if (> rc usage-count)
      {:valid false :message "That promo is no longer available"}
      {:valid true})))
