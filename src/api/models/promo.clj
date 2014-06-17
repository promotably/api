(ns api.models.promo
  (:require [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys]]
            [api.db :refer :all]
            [api.entities :refer :all]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]))

(def PromoSchema {(s/optional-key :id) s/Int
                  (s/required-key :site-id) s/Int
                  (s/required-key :name) s/Str
                  (s/required-key :code) s/Str
                  (s/optional-key :created-at) s/Inst
                  (s/optional-key :updated-at) s/Inst})

(defn- db-to-promo
  "Convert a database result to a promo that obeys the PromoSchema"
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(sm/defn new-promo! :- PromoSchema
  "Creates a new promo in the database"
  [params :- PromoSchema]
  (let [{:keys [site-id name code]} params]
    (db-to-promo
     (insert promos
             (values {:site_id site-id
                      :name name
                      :code code
                      :created_at (sqlfn now)
                      :updated_at (sqlfn now)
                      :uuid (java.util.UUID/randomUUID)})))))

(sm/defn find-by-site-uuid :- [PromoSchema]
  "Finds all promos for a given site id. Returns a collection (empty
  array if no results found)"
  [site-uuid :- s/Uuid]
  (let [results (select promos
                        (join sites (= :sites.id :site_id))
                        (where {:sites.uuid site-uuid}))]
    (map db-to-promo results)))


(sm/defn ^:always-validate find-by-site-uuid-and-code
  "Finds a promo with the given site id and code combination.
  Returns nil if no results found"
  [site-uuid :- s/Uuid
   promo-code :- s/Str]
  (db-to-promo (first (select promos
                              (join sites (= :sites.id :site_id))
                              (where {:sites.uuid site-uuid
                                      :promos.code (clojure.string/upper-case
                                                    promo-code)})))))

(defn- before-incept?
  [the-promo]
  (if-not (nil? (:incept-date the-promo))
    (before? (now) (from-sql-date (:incept-date the-promo)))
    false))

(defn- after-expiry?
  [the-promo]
  (if-not (nil? (:expiry-date the-promo))
    (after? (now) (from-sql-date (:expiry-date the-promo)))
    false))

(defn- past-max-usage?
  [the-promo]
  (if-not (nil? (:max-usage-count the-promo))
    (> (:current-usage-count the-promo) (:max-usage-count the-promo))
    false))

(defn valid?
  "Validates whether a promo can be used, based on the rules
   of the promo, and the context passed in"
  [the-promo & [context]]
  (cond (not (:active the-promo)) {:valid false
                                   :message "That promo is currently inactive"}
        (before-incept? the-promo) {:valid false
                                    :message "That promo hasn't started yet"}
        (after-expiry? the-promo) {:valid false
                                   :message "That promo has expired"}
        (past-max-usage? the-promo) {:valid false
                                     :message "That promo is no longer available"}
        :else {:valid true}))
