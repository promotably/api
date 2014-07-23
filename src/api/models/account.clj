(ns api.models.account
  (:require [clojure.set :refer [rename-keys]]
            [korma.core :refer :all]
            [api.entities :refer [accounts users]]
            [api.models.user :as u]
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

(defn new-account!
  "Creates a new account in the database."
  [{:keys [email first-name last-name] :as params}]
  (if-not (u/find-by-email email)
    (let [a (insert accounts
                    (values {:created_at (sqlfn now)
                             :updated_at (sqlfn now)}))
          user (insert users
                       (values {:account_id (:id a)
                                :email email
                                :first_name first-name
                                :last_name last-name}))]
      {:success true
       :user (dissoc user :id)
       :account (dissoc a :id)})
    {:success false :error :email-already-exists}))

(sm/defn ^:always-validate find-by-id :- (s/maybe AccountSchema)
  [id :- s/Int]
  (db-to-account
   (first
    (select accounts
            (where {:id id})))))
