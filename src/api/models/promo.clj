(ns api.models.promo
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [clojure.walk :refer [postwalk]]
            [api.db :refer :all]
            [api.models.helper :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher underscore-to-dash-keys]]
            [api.lib.schema :refer :all]
            [api.models.promo-condition :as c]
            [api.models.linked-products :as lp]
            [api.models.redemption :as rd]
            [api.models.site :as site]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [korma.db :refer [transaction] :as kdb]
            [schema.core :as s]
            [schema.macros :as sm]
            [schema.coerce :as sc]))

(defn db-to-promo
  "Convert a database result to a promo that obeys the PromoSchema"
  [r]
  (let [hyphenified-params (underscore-to-dash-keys r)
        cleaned (apply dissoc
                       hyphenified-params
                       (filter (complement #{:exceptions})
                               (for [[k v] hyphenified-params
                                     :when (nil? v)] k)))

        conditions (map
                    #(-> (apply dissoc
                                %
                                (for [[k v] % :when (nil? v)] k))
                         (dissoc :uuid :promo-id :id)
                         (unwrap-jdbc)
                         (->> ((sc/coercer OutboundPromoCondition
                                           (sc/first-matcher
                                            [custom-matcher
                                             sc/string-coercion-matcher])))))
                    (:promo-conditions cleaned))
        lps (map
             #(-> (apply dissoc
                         %
                         (for [[k v] % :when (nil? v)] k))
                  (dissoc :uuid :promo-id :id :created-at)
                  (->> ((sc/coercer OutboundLinked
                                    (sc/first-matcher
                                     [custom-matcher
                                      sc/string-coercion-matcher])))))
             (:linked-products cleaned))
        cleaned (-> cleaned
                    (dissoc :promo-conditions :id :site-id)
                    (assoc :conditions conditions)
                    (assoc :linked-products lps))
        matcher (sc/first-matcher [custom-matcher
                                   sc/string-coercion-matcher])
        coercer (sc/coercer OutboundPromo matcher)]
    (coercer cleaned)))

(defn exists?
  [site-id code]
  (seq (select promos
               (where {:site_id site-id :code code}))))

(defn find-by-id
  [promo-id]
  (seq (select promos
               (where {:id promo-id}))))

(defn find-by-site-and-uuid
  [site-id promo-id]
  (seq (select promos
               (where {:site_id site-id :uuid promo-id}))))

(defn by-promo-id
  [site-id promo-id]
  (find-by-site-and-uuid site-id promo-id))

(sm/defn new-promo!
  "Creates a new promo in the database"
  [{:keys [description name code exceptions
           reward-type reward-applied-to reward-tax reward-amount
           site-id linked-products conditions promo-id] :as params}]
  (transaction
   (if (seq (exists? site-id code))
     {:success false
      :error :already-exists
      :message (format "A promo with code %s already exists" code)}
     (let [new-values {:active true
                       :site_id site-id
                       :name name
                       :description description
                       :exceptions (if exceptions
                                     (clojure.core/name exceptions))
                       :reward_applied_to (clojure.core/name reward-applied-to)
                       :reward_tax (clojure.core/name reward-tax)
                       :reward_type (clojure.core/name reward-type)
                       :reward_amount reward-amount
                       :code code
                       :created_at (sqlfn now)
                       :updated_at (sqlfn now)
                       :uuid (java.util.UUID/randomUUID)}
           raw (insert promos (values new-values))
           id (:id raw)]
       (when (seq conditions)
         (c/create-conditions! (map #(-> %
                                         (assoc :uuid (java.util.UUID/randomUUID))
                                         (assoc :promo-id id))
                                    conditions)))
       (when (seq linked-products)
         (lp/create! (map #(-> %
                               (assoc :uuid (java.util.UUID/randomUUID))
                               (assoc :promo-id id))
                          linked-products)))
       {:success true}))))

;; (sm/defn update-promo!
(defn update-promo!
  "Updates a promo in the database"
  [promo-id {:keys [description name code exceptions
                    reward-type reward-applied-to reward-tax reward-amount
                    site-id conditions linked-products] :as params}]
  (let [promo-id (java.util.UUID/fromString promo-id)
        found (first (by-promo-id site-id promo-id))
        id (:id found)]
    (if-not found
      {:success false
       :error :not-found
       :message (format "Promo id %s does not exist." promo-id)}
      (let [new-values {:active true
                        :site_id site-id
                        :name name
                        :description description
                        :exceptions (if exceptions
                                      (clojure.core/name exceptions))
                        :reward_applied_to (clojure.core/name reward-applied-to)
                        :reward_tax (clojure.core/name reward-tax)
                        :reward_type (clojure.core/name reward-type)
                        :reward_amount reward-amount
                        :code code
                        :updated_at (sqlfn now)}
            result (update promos
                           (set-fields new-values)
                           (where {:id (:id found)}))]
        (c/update-conditions! (:id found)
                              (map #(-> %
                                        (assoc :uuid (java.util.UUID/randomUUID))
                                        (assoc :promo-id (:id found)))
                                   conditions))
        (lp/update! (:id found)
                    (map #(-> %
                              (assoc :uuid (java.util.UUID/randomUUID))
                              (assoc :promo-id (:id found)))
                         linked-products))
        {:success true}))))

(sm/defn find-by-site-uuid
  "Finds all promos for a given site id. Returns a collection (empty
  array if no results found)"
  [site-uuid :- s/Uuid]
  (let [results (select promos
                        (with promo-conditions)
                        (with linked-products)
                        (join sites (= :sites.id :site_id))
                        (where {:sites.uuid site-uuid}))]
    (map db-to-promo results)))

(defn find-raw
  "Finds a promo by uuid and doesn't munge the result from the db at all."
  [column value]
  (let [results (select promos
                        (where {(keyword column) value}))]
    (-> results first underscore-to-dash-keys)))

(sm/defn find-by-uuid
  "Finds a promo by uuid."
  [promo-uuid :- s/Uuid]
  (let [results (select promos
                        (with promo-conditions)
                        (with linked-products)
                        (where {:promos.uuid promo-uuid}))]
    (first (map db-to-promo results))))

(sm/defn delete-by-uuid
  "Deletes a promo by uuid."
  [site-id promo-uuid :- s/Uuid]
  (let [found (first (by-promo-id site-id promo-uuid))]
    (transaction
     (c/delete-conditions! (:id found))
     (lp/delete! (:id found))
     (delete promos (where {:promos.uuid promo-uuid})))))

(sm/defn ^:always-validate find-by-site-uuid-and-code
  "Finds a promo with the given site id and code combination.
  Returns nil if no results found"
  [site-uuid :- s/Uuid promo-code :- s/Str]
  (let [row (first (select promos
                           (with promo-conditions)
                           (with linked-products)
                           (join sites (= :sites.id :site_id))
                           (where {:sites.uuid site-uuid
                                   :promos.code promo-code})))]
    (if row (db-to-promo row))))

(def condition-order
  [:dates
   :times
   :product-ids
   :category-ids
   :not-product-ids
   :not-category-ids
   :combo-product-ids
   :item-count
   :item-value
   :individual-use
   :min-order-value
   :usage-count
   :total-discounts])

(defn valid?
  [{:keys [active conditions] :as promo}
   {:keys [cart-contents] :as context}]
  (if-not active
    [context ["That promo is currently inactive"]]
    (let [ordered-conditions (mapcat #(filter (fn [c] (= % (:type c))) conditions)
                                     condition-order)
          validation (reduce
                      #(c/validate %1 %2)
                      context
                      ordered-conditions)
          errors (:errors validation)
          errors (if errors (reverse errors))]
      [validation errors])))

(defn discount-amount
  [{:keys [reward-applied-to reward-type reward-amount active conditions] :as promo}
   {:keys [cart-contents matching-products selected-product-id] :as context}
   errors]

  (let [selected-cart-item (if selected-product-id
                             (first (filter #(= selected-product-id (:product-id %))
                                            (or matching-products cart-contents))))
        {:keys [line-subtotal quantity product-categories]} selected-cart-item
        unit-price (/ line-subtotal quantity)]
    (cond

     errors
     [context errors]

     :else
     (let [discount-amount-per-item (cond (= :percent reward-type)
                                          (* (/ reward-amount 100.0) unit-price)
                                          (= :dollar reward-type)
                                          reward-amount)]
       (cond

        (= :one-item reward-applied-to)
        (format "%.4f" (/ (float discount-amount-per-item) quantity))

        (= :all-items reward-applied-to)
        discount-amount-per-item

        (= :cart reward-applied-to)
        discount-amount-per-item)))))


