(ns api.models.account
  (:require [clojure.set :refer [rename-keys]]
            [korma.core :refer :all]
            [api.entities :refer [accounts users sites]]
            [api.models.site :as site]
            [api.models.user :as user]
            [api.util :refer [hyphenify-key assoc*]]
            [schema.macros :as sm]
            [schema.core :as s]))

(def AccountSchema {(s/optional-key :id) s/Int
                    (s/optional-key :company-name) (s/maybe s/Str)
                    (s/optional-key :created-at) s/Inst
                    (s/optional-key :updated-at) s/Inst
                    (s/optional-key :account-id) s/Uuid})

(defn- db-to-account
  "Translates a database result to a map that obeys AccountSchema"
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(sm/defn ^:always-validate find-by-id :- (s/maybe AccountSchema)
  [id :- s/Int]
  (db-to-account
   (first
    (select accounts
            (with sites)
            (where {:id id})))))

(sm/defn ^:always-validate find-by-account-id :- (s/maybe AccountSchema)
  [account-id :- s/Uuid]
  (db-to-account
   (first
    (select accounts
            (with sites)
            (where {:account_id account-id})))))

(defn new-account!
  "Creates a new account in the database."
  [{:keys [user-id company-name] :as params}]
  (let [a (insert accounts
                  (values {:company_name company-name
                           :created_at (sqlfn now)
                           :updated_at (sqlfn now)}))
        user (update users
                     (set-fields {:account_id (:id a)})
                     (where {:user_id user-id}))]
    (when (and a user)
      (find-by-id (:ia a)))))

(defn update!
  [{:keys [account-id company-name] :as params}]
  (when-let [the-account (find-by-account-id account-id)]
    (when-let [a (update accounts
                         (set-fields {:company_name company-name})
                         (where {:account_id account-id}))]
      (find-by-account-id account-id))))
