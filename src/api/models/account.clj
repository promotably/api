(ns api.models.account
  (:require [clojure.set :refer [rename-keys]]
            [korma.core :refer :all]
            [api.entities :refer [accounts users]]
            [api.models.user :refer [assign-user-to-account!]]
            [api.util :refer [hyphenify-key]]
            [schema.macros :as sm]
            [schema.core :as s]))


(def AccountSchema {(s/optional-key :id) s/Int
                    (s/optional-key :company-name) s/Str
                    (s/optional-key :created-at) s/Inst
                    (s/optional-key :updated-at) s/Inst})

(defn- db-to-account
  "Translates a database result to a map that obeys AccountSchema"
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(sm/defn ^:always-validate new-account! :- AccountSchema
  "Creates a new account in the database. The user-id passed in is
  assigned to the account"
  [user-id :- s/Int
   params :- AccountSchema]
  (let [{:keys [company-name]} params
        a (insert accounts
                  (values {:company_name company-name
                           :created_at (sqlfn now)
                           :updated_at (sqlfn now)}))]
    (assign-user-to-account! user-id (:id a))
    (db-to-account a)))

(sm/defn ^:always-validate find-by-id :- (s/maybe AccountSchema)
  [id :- s/Int]
  (db-to-account
   (first
    (select accounts
            (where {:id id})))))
