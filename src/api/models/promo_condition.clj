(ns api.models.promo-condition
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

(defn- db-to-condition
  [r]
  (let [ks (keys r)
        hyphenified-params (rename-keys r (zipmap ks (map hyphenify-key ks)))]
    ((sc/coercer PromoCondition
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     hyphenified-params)))


(defn- condition-to-db
  [{:keys [type] :as condition}]
  (dash-to-underscore-keys
   ((sc/coercer DatabaseCondition
                (sc/first-matcher [custom-matcher
                                   sc/string-coercion-matcher]))
    (merge condition {:type (name type)}))))

(defn create-conditions!
  [c]
  (let [coerced-conditions (map condition-to-db c)]
    (db-to-condition (insert promo-conditions
                             (values (map condition-to-db c))))))

(defn delete-conditions!
  [promo-id]
  (delete promo-conditions (where {:promo_id promo-id})))

(defn update-conditions!
  [promo-id c]
  (transaction
   (delete-conditions! promo-id)
   (when-let [coerced-conditions (seq (map condition-to-db c))]
     (db-to-condition (insert promo-conditions
                              (values (map condition-to-db c)))))))

(defmulti validate
  (fn [{:keys [type]} context] type))

(defmethod validate :dates
  [{:keys [start-date end-date] :as condition} context]
  (cond
   (before? (now) start-date) {:valid false :message "That promo hasn't started yet"}
   (after? (now) end-date) {:valid false :message "That promo has ended"}
   :else {:valid true}))

(defmethod validate :usage-count
  [{:keys [usage-count id] :as condition}
   {:keys [shopper-email] :as context}]
  (let [rc (redemption/count-by-promo-and-shopper-email id shopper-email)]
    (if (> rc usage-count)
      {:valid false :message "That promo is no longer available"}
      {:valid true})))
