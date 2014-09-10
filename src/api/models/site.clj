(ns api.models.site
  (:require [clojure.set :refer [rename-keys]]
            [korma.core :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [underscore-to-dash-keys]]
            [schema.core :as s]
            [schema.macros :as sm]))

(def SiteSchema {(s/optional-key :id) s/Int
                 (s/optional-key :account-id) s/Int
                 (s/required-key :created-at) s/Inst
                 (s/required-key :updated-at) s/Inst
                 (s/optional-key :name) (s/maybe s/Str)
                 (s/required-key :site-id) s/Uuid
                 (s/optional-key :site-code) (s/maybe s/Str)
                 (s/optional-key :site-url) (s/maybe s/Str)
                 (s/optional-key :api-secret) (s/maybe s/Uuid)})

(defn- db-to-site
  "Translates a database result to a map that obeys SiteSchema"
  [r]
  (let [hyphenated (underscore-to-dash-keys r)]
    (dissoc (assoc hyphenated :site-id (:uuid hyphenated)) :uuid :id :account-id)))

(sm/defn ^:always-validate find-by-account-id :- [SiteSchema]
  [account-id :- s/Int]
  (map db-to-site
       (select sites
               (where {:account_id account-id}))))

(defn find-by-site-uuid
  [site-uuid]
  (let [u (condp = (class site-uuid)
            java.lang.String (java.util.UUID/fromString site-uuid)
            java.util.UUID site-uuid)]
    (db-to-site (first (select sites
                               (where {:uuid u}))))))

(defn get-id-by-site-uuid
  [site-uuid]
  (:id (first (select sites
                      (fields [:id])
                      (where {:uuid site-uuid})))))
