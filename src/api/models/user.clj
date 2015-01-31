(ns api.models.user
  (:require [clojure.set :refer [rename-keys]]
            [clojure.tools.logging :as log]
            [api.entities :refer [users accounts users-accounts sites]]
            [api.lib.coercion-helper :refer [dash-to-underscore-keys
                                             underscore-to-dash-keys]]
            [api.lib.crypto :as crypto]
            [api.lib.user :refer [parse-sql-exception]]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]))

(defn- lookup-single-by
  "Lookup a single row by where map passed in"
  [m]
  (underscore-to-dash-keys (first (select users
                                          (with accounts
                                                (with sites))
                                          (where m)))))

(defn- lookup-many-by
  [m]
  (mapv underscore-to-dash-keys (select users
                                        (where m))))

(defn find-by-user-id
  "Lookup a user by user id"
  [user-id]
  (lookup-single-by {:user_id user-id}))

(defn find-by-username
  "Lookup a user by username"
  [username]
  (lookup-single-by {:username username}))

(defn find-by-email
  "Lookup a user by email"
  [email]
  (lookup-single-by {:email email}))

(defn find-by-user-social-id
  "Lookup a user by their user social id"
  [social-id]
  (lookup-single-by {:user_social_id social-id}))

(defn find-by-account-id
  [account-id]
  (mapv underscore-to-dash-keys (select users
                                        (with accounts
                                              (where {:id account-id})))))

(defn find-by-account-uuid
  [account-uuid]
  (mapv underscore-to-dash-keys (select users
                                        (with accounts
                                              (where {:account_id account-uuid})))))

(defn add-user-to-account!
  [account-id user-id]
  (insert users-accounts
          (values {:users_id user-id
                   :accounts_id account-id})))

(defn new-user!
  "Creates a new user in the database"
  [params]
  (let [{:keys [username email password company-name phone job-title
                user-social-id account-id first-name last-name]} params
        password-and-salt (when password
                            (crypto/encrypt-password password))
        a (first (select accounts
                         (fields :id)
                         (where {:account_id account-id})))
        user (insert users
                     ;;TODO: There's got to be a better way than spelling out
                     ;;every single field? What happens when I want to add
                     ;;more fields?
                     (values {:username (or username email)
                              :email email
                              :password (when password-and-salt
                                          (first password-and-salt))
                              :password_salt (when password-and-salt
                                               (last password-and-salt))
                              :user_social_id user-social-id
                              :phone phone
                              :first_name first-name
                              :last_name last-name
                              :job_title job-title
                              :created_at (sqlfn now)}))]
    (when (:id a)
      (add-user-to-account! (:id a) (:id user)))
    (find-by-user-id (:user_id user))))

(let [allowed-keys [:first-name :last-name
                    :email :username :phone :job-title
                    :user-social-id :password]]
  (defn update-user!
    [{:keys [user-id] :as params}]
    (let [update-params (dash-to-underscore-keys
                         (select-keys params allowed-keys))
          params-for-update (if (:password update-params)
                              (let [[encrypted-pw salt] (crypto/encrypt-password (:password update-params))]
                                (assoc update-params :password encrypted-pw :password_salt salt))
                              update-params)]
      (update users
              (set-fields params-for-update)
              (where {:user_id user-id})))))
