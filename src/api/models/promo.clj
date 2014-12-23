(ns api.models.promo
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [clojure.walk :refer [postwalk]]
            [clojure.java.jdbc :as jdbc]
            [api.models.helper :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher underscore-to-dash-keys]]
            [api.lib.schema :refer :all]
            [api.models.promo-condition :as c]
            [api.models.linked-products :as lp]
            [api.models.redemption :as rd]
            [api.models.site :as site]
            [api.util :refer [hyphenify-key]]
            [api.system :refer [current-system]]
            [korma.core :refer :all]
            [korma.db :refer [transaction] :as kdb]
            [api.kinesis :as kinesis]
            [slingshot.slingshot :only [throw+] :as ss]
            [schema.core :as s]
            [schema.macros :as sm]
            [schema.coerce :as sc]))

(defn db-to-promo
  "Convert a database result to a promo that obeys the PromoSchema"
  [r]
  (let [hyphenified-params (underscore-to-dash-keys r)
        cleaned (apply dissoc
                       hyphenified-params
                       (for [[k v] hyphenified-params
                             :when (nil? v)] k))

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
  [site-id promo-id & [raw]]
  (let [results (select promos
                        (with promo-conditions)
                        (with linked-products)
                        (where {:site_id site-id :uuid promo-id}))]
    (first (if raw results (map db-to-promo results)))))

(sm/defn find-by-uuid
  "Finds a promo by uuid."
  [promo-uuid :- s/Uuid]
  (let [results (select promos
                        (with promo-conditions)
                        (with linked-products)
                        (where {:promos.uuid promo-uuid}))]
    (first (map db-to-promo results))))

(defn to-kinesis!
  [kinesis-comp action promo-id site-id]
  (if-let [the-promo (first (find-by-id promo-id))]
    (kinesis/record-promo-action! kinesis-comp
                                  action
                                  the-promo
                                  (site/find-by-site-id site-id))
    (ss/throw+ {:type ::missing-promo :promo-id promo-id :site-id site-id})))

(sm/defn new-promo!
  "Creates a new promo in the database"
  [kinesis-comp
   {:keys [description seo-text code reward-type reward-applied-to
           reward-tax reward-amount site-id linked-products
           conditions promo-id] :as params}]
  (transaction
   (if (seq (exists? site-id code))
     {:success false
      :error :already-exists
      :message (format "A promo with code %s already exists" code)}
     (let [new-values {:active true
                       :site_id site-id
                       :description description
                       :seo_text seo-text
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
       (to-kinesis! kinesis-comp :create id site-id)
       {:success true}))))

;; (sm/defn update-promo!
(defn update-promo!
  "Updates a promo in the database"
  [promo-id {:keys [description seo-text code reward-type reward-applied-to
                    active
                    reward-tax reward-amount site-id conditions
                    linked-products] :as params}]
  (let [promo-id (java.util.UUID/fromString promo-id)
        {:keys [id] :as found} (find-by-site-and-uuid site-id promo-id true)]
    (if-not found
      {:success false
       :error :not-found
       :message (format "Promo id %s does not exist." promo-id)}
      (let [new-values {:site_id site-id
                        :description description
                        :seo_text seo-text
                        :reward_applied_to (clojure.core/name reward-applied-to)
                        :reward_tax (clojure.core/name reward-tax)
                        :reward_type (clojure.core/name reward-type)
                        :reward_amount reward-amount
                        :code code
                        :updated_at (sqlfn now)}
            new-values (if-not (nil? active)
                         (assoc new-values :active active)
                         new-values)
            result (update promos
                           (set-fields new-values)
                           (where {:id id}))]
        (c/update-conditions! id
                              (map #(-> %
                                        (assoc :uuid (java.util.UUID/randomUUID))
                                        (assoc :promo-id id))
                                   conditions))
        (lp/update! id
                    (map #(-> %
                              (assoc :uuid (java.util.UUID/randomUUID))
                              (assoc :promo-id id))
                         linked-products))
        {:success true}))))

(sm/defn find-by-site-uuid
  "Finds all promos for a given site id. Returns a collection (empty
  array if no results found)"
  ([site-uuid :- s/Uuid] (find-by-site-uuid site-uuid true))
  ([site-uuid :- s/Uuid cooked-mode]
     (let [results (select promos
                           (with promo-conditions)
                           (with linked-products)
                           (join sites (= :sites.id :site_id))
                           (where {:sites.uuid site-uuid}))]
       (if cooked-mode
         (map db-to-promo results)
         results))))

(defn find-raw
  "Finds a promo by uuid and doesn't munge the result from the db at all."
  [column value]
  (let [results (select promos
                        (where {(keyword column) value}))]
    (-> results first underscore-to-dash-keys)))

(sm/defn delete-by-uuid
  "Deletes a promo by uuid."
  [site-id promo-uuid :- s/Uuid]
  (let [found (find-by-site-and-uuid site-id promo-uuid)]
    (transaction
     ;;(to-kinesis! :delete (:id found) site-id)
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

(sm/defn count-by-site
  [site-uuid :- s/Uuid]
  (select promos
          (aggregate (count :*) :cnt)
          (where {:sites.uuid site-uuid})
          (join sites (= :sites.id :site_id))))

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

(defn total-usage
  [site-id promo-id]
  (try
    (let [statement (.prepareCall (.getConnection (:datasource @(:pool @korma.db/_default)))
                                  "{?= call promoUsageCount(?,?)}")]
      (.execute (doto statement
                  (.registerOutParameter 1 java.sql.Types/INTEGER)
                  (.setObject 2 site-id)
                  (.setObject 3 promo-id)))
      (.getInt statement 1))
    (catch java.sql.BatchUpdateException ex
      (println (.getNextException ex)))))

(defn valid?
  [{:keys [active conditions] :as promo}
   {:keys [cart-contents] :as context}]
  (if-not active
    [context ["That promo is currently inactive"]]
    (let [ordered-conditions (mapcat #(filter (fn [c] (= % (:type c))) conditions)
                                     condition-order)
          c2 (if (some? (filterv #(= (:type %) :usage-count) conditions))
               (assoc context :current-usage-count (total-usage (:site-id context)
                                                                (:uuid promo)))
               context)
          validation (reduce
                      #(c/validate %1 %2)
                      c2
                      ordered-conditions)
          errors (:errors validation)
          errors (if errors (reverse errors))]
      [validation errors])))

(defn discount-amount
  [{:keys [reward-applied-to reward-type reward-amount active conditions] :as promo}
   {:keys [cart-contents matching-products selected-product-sku] :as context}
   errors]
  (let [selected-cart-contents (if selected-product-sku
                                 (first (filter #(= selected-product-sku (:sku %))
                                                (or matching-products cart-contents)))
                                 (or matching-products cart-contents))]
    (cond

     errors
     ["0" context errors]

     (not selected-cart-contents)
     ["0" context nil]

     :else
     (let [sort-fn (fn [a b]
                     (let [{:keys [line-subtotal quantity]} a
                           unit-price-a (if (and line-subtotal quantity)
                                          (/ line-subtotal quantity) 0)
                           {:keys [line-subtotal quantity]} b
                           unit-price-b (if (and line-subtotal quantity)
                                          (/ line-subtotal quantity) 0)]
                       (compare unit-price-a unit-price-b)))
           sorted-by-unit-price (->>
                                 (sort sort-fn selected-cart-contents)
                                 (remove #(= 0 (:line-subtotal %))))]
       (cond

        ;; Discount applies to everything in selected-cart-contents
        (= :matching-items reward-applied-to)
        (let [total (apply + (map :line-subtotal selected-cart-contents))
              qty (apply + (map :quantity selected-cart-contents))
              discount (cond (= :percent reward-type)
                             (* (/ reward-amount 100.0) total)
                             (= :fixed reward-type)
                             (min total (* reward-amount qty)))]
          [(format "%.4f" (float discount)) context nil])

        ;; Discount applies to everything in cart-contents
        (= :cart reward-applied-to)
        (let [cart-total (apply + (map :line-subtotal cart-contents))
              discount (cond (= :percent reward-type)
                             (* (/ reward-amount 100.0) cart-total)
                             (= :fixed reward-type)
                             (min cart-total reward-amount))]
          [(format "%.4f" (float discount)) context nil]))))))
