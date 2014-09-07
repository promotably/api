(ns api.models.promo
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [api.db :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer :all]
            [api.models.condition :as c]
            [api.models.redemption :as rd]
            [api.models.site :as site]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
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
  (let [ks (keys r)
        hyphenified-params (rename-keys r (zipmap ks (map hyphenify-key ks)))
        safe-params (merge {:conditions []} hyphenified-params)]
    ((sc/coercer OutboundPromo
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     safe-params)))

(sm/defn new-promo!
  "Creates a new promo in the database"
  [{:keys [site-id name code conditions] :as params}]
  (let [the-promo
        (db-to-promo
         (insert promos
                 (values {:site_id site-id
                          :name name
                          :code code
                          :created_at (sqlfn now)
                          :updated_at (sqlfn now)
                          :uuid (java.util.UUID/randomUUID)})))]
    (assoc the-promo :conditions
           (c/create-conditions (map (fn [c] (assoc c :promo-id (:id the-promo))))))))

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

(defn validate-promo
  [promo context]
  {:valid false :message "Not implemented yet"})
