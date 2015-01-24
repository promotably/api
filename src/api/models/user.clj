(ns api.models.user
  (:require [clojure.set :refer [rename-keys]]
            [clojure.tools.logging :as log]
            [api.entities :refer [users accounts]]
            [api.lib.coercion-helper :refer [dash-to-underscore-keys]]
            [api.lib.crypto :as crypto]
            [api.lib.user :refer [parse-sql-exception]]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]))

(defn- safe-db-to-user
  "Translates a database result to a map that obeys UserSchema."
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))


(defn- lookup-single-by
  "Lookup a single row by where map passed in"
  [m]
  (first (select users
                 (with accounts)
                 (where m))))

(defn find-by-user-id
  "Lookup a user by user id"
  [user-id]
  (safe-db-to-user (lookup-single-by {:user_id (if (= (class user-id) java.util.UUID)
                                                 user-id
                                                 (java.util.UUID/fromString user-id))})))

(defn find-by-username
  "Lookup a user by username"
  [username]
  (safe-db-to-user (lookup-single-by {:username username})))

(defn find-by-email
  "Lookup a user by email"
  [email]
  (safe-db-to-user (lookup-single-by {:email email})))

(defn find-by-user-social-id
  "Lookup a user by their user social id"
  [uid]
  (safe-db-to-user (first (select users
                                  (where {:user_social_id uid})))))

(defn find-by-account-id
  [account-id]
  (safe-db-to-user (select users
                           (where {:account_id account-id}))))

(defn find-by-account-uuid
  [account-uuid]
  (safe-db-to-user (first (select users
                                  (join accounts (= :accounts.id :account_id))
                                  (where {:accounts.account_id account-uuid})))))

(defn new-user!
  "Creates a new user in the database"
  [params]
  (let [{:keys [username email password company-name phone job-title
                user-social-id account-id first-name last-name]} params
                [encrypted-pw salt] (crypto/encrypt-password (or password user-social-id))]
    (safe-db-to-user
     (let [a (first (select accounts
                            (fields :id)
                            (where {:account_id account-id})))]
       (insert users
               ;;TODO: There's got to be a better way than spelling out
               ;;every single field? What happens when I want to add
               ;;more fields?
               (values {:username (or username email)
                        :email email
                        :password encrypted-pw
                        :password_salt salt
                        :user_social_id user-social-id
                        :phone phone
                        :first_name first-name
                        :last_name last-name
                        :job_title job-title
                        :created_at (sqlfn now)
                        :account_id (:id a)}))))))

(let [allowed-keys #{:first-name :last-name
                     :email :username :phone :job-title
                     :user-social-id :account-id}]
  (defn update-user!
    [{:keys [user-id] :as params}]
    (let [params-for-update (dash-to-underscore-keys
                             (into {} (filter #(contains? allowed-keys (first %)) params)))
          result (update users
                         (set-fields params-for-update)
                         (where {:user_id user-id}))])))
