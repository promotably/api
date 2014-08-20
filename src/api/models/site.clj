(ns api.models.site
  (:require [clojure.set :refer [rename-keys]]
            [korma.core :refer :all]
            [api.entities :refer :all]
            [api.util :refer [hyphenify-key]]
            [schema.core :as s]
            [schema.macros :as sm]))

(def SiteSchema {(s/required-key :id) s/Int
                 (s/required-key :account-id) s/Int
                 (s/required-key :created-at) s/Inst
                 (s/required-key :updated-at) s/Inst})

(defn- db-to-site
  "Translates a database result to a map that obeys SiteSchema"
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(sm/defn ^:always-validate find-by-account-id :- [SiteSchema]
  [account-id :- s/Int]
  (map db-to-site
       (select sites
               (where {:account_id account-id}))))

(defn find-by-site-uuid
  [site-uuid]
  (db-to-site (first (select sites
                             (where {:uuid site-uuid})))))

