(ns api.models.promo
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [api.db :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher underscore-to-dash-keys]]
            [api.lib.schema :refer :all]
            [api.models.condition :as c]
            [api.models.redemption :as rd]
            [api.models.site :as site]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [korma.db :as kdb]
            [schema.core :as s]
            [schema.macros :as sm]
            [schema.coerce :as sc]))

(defn- jdbc-array->seq
  [^org.postgresql.jdbc4.Jdbc4Array jdbc-array]
  (when-not (nil? jdbc-array)
    (seq (.getArray jdbc-array))))

(defn db-to-promo
  "Convert a database result to a promo that obeys the PromoSchema"
  [r]
  (let [hyphenified-params (underscore-to-dash-keys r)]
    ((sc/coercer OutboundPromo
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     hyphenified-params)))

(defn exists?
  [site-id code]
  (seq (select promos
               (where {:site_id site-id :code code}))))

(sm/defn new-promo!
  "Creates a new promo in the database"
  [{:keys [site-id name code conditions] :as params}]
  (if (seq (exists? site-id code))
    {:success false
     :error :already-exists
     :message (format "A promo with code %s already exists" code)}
    (kdb/transaction
     (let [the-promo
           (db-to-promo
            (insert promos
                    (values {:site_id site-id
                             :name name
                             :code code
                             :created_at (sqlfn now)
                             :updated_at (sqlfn now)
                             :uuid (java.util.UUID/randomUUID)})))]
       (c/create-conditions! (map (fn [c] (assoc c :promo-id (:id the-promo))) conditions)))
     {:success (not (kdb/is-rollback?))})))

(sm/defn find-by-site-uuid
  "Finds all promos for a given site id. Returns a collection (empty
  array if no results found)"
  [site-uuid :- s/Uuid]
  (let [results (select promos (with conditions)
                        (join sites (= :sites.id :site_id))
                        (where {:sites.uuid site-uuid}))]
    (map db-to-promo results)))


(sm/defn ^:always-validate find-by-site-uuid-and-code
  "Finds a promo with the given site id and code combination.
  Returns nil if no results found"
  [site-uuid :- s/Uuid
   promo-code :- s/Str]
  (let [row (first (select promos
                           (with conditions)
                           (join sites (= :sites.id :site_id))
                           (where {:sites.uuid site-uuid
                                   :promos.code (clojure.string/upper-case
                                                 promo-code)})))]
    (when row (db-to-promo row))))

(defn valid?
  [{:keys [active conditions] :as promo}
   {:keys [cart-contents] :as context}]
  (if-not active
    {:valid false :messages ["That promo is currently inactive"]}
    (let [condition-validations (map #(c/validate % context)
                                     conditions)]
      (if (not-every? true? (map #(:valid %) condition-validations))
        {:valid false :messages (vec (map #(:message %)
                                          (filter #(false? (:valid %)) condition-validations)))}
        {:valid true}))))
