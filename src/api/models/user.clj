(ns api.models.user
  (:require [clojure.set :refer [rename-keys]]
            [clojure.tools.logging :as log]
            [api.db :refer :all]
            [api.entities :refer [users accounts]]
            [api.lib.user :refer [salted-pass parse-sql-exception]]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]))

(def BaseUserSchema {(s/required-key :email) s/Str
                     (s/optional-key :username) (s/maybe s/Str)
                     (s/optional-key :phone) (s/maybe s/Str)
                     (s/optional-key :job-title) (s/maybe s/Str)
                     (s/optional-key :first-name) (s/maybe s/Str)
                     (s/optional-key :last-name) (s/maybe s/Str)})

(def InboundUserSchema (merge BaseUserSchema
                              {(s/required-key :user-social-id) s/Str
                               (s/optional-key :account-id) s/Uuid}))

(def OutboundUserSchema (merge BaseUserSchema
                               {(s/required-key :created-at) s/Inst
                                (s/required-key :account-id) (s/maybe s/Int)
                                (s/required-key :user-id) s/Uuid
                                (s/required-key :user-social-id) s/Str
                                (s/required-key :id) s/Int}))

(defn- safe-db-to-user
  "Translates a database result to a map that obeys UserSchema."
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(sm/defn new-user!
  "Creates a new user in the database"
  [params :- InboundUserSchema]
  (let [{:keys [username email company-name phone job-title
                user-social-id account-id]} params]
    (try
      {:status :success
       :user (safe-db-to-user
              (let [a (first (select accounts
                                     (fields :id)
                                     (where {:account_id account-id})))]
                (insert users
                        ;;TODO: There's got to be a better way than spelling out
                        ;;every single field? What happens when I want to add
                        ;;more fields?
                        (values {:username username
                                 :email email
                                 :user_social_id user-social-id
                                 :phone phone
                                 :job_title job-title
                                 :created_at (sqlfn now)
                                 :account_id (:id a)}))))}
      (catch org.postgresql.util.PSQLException ex
        {:status :error
         :error (or (parse-sql-exception ex) (.getMessage ex))}))))

(defn- lookup-single-by
  "Lookup a single row by where map passed in"
  [m]
  (first (select users
                 (with accounts)
                 (where m))))

(sm/defn find-by-username :- OutboundUserSchema
  "Lookup a user by username"
  [username :- s/Str]
  (safe-db-to-user (lookup-single-by {:username username})))

(sm/defn find-by-email :- OutboundUserSchema
  "Lookup a user by email"
  [email :- s/Str]
  (safe-db-to-user (lookup-single-by {:email email})))

(sm/defn find-by-user-social-id :- OutboundUserSchema
  "Lookup a user by their user social id"
  [uid :- s/Str]
  (safe-db-to-user (first (select users
                                  (where {:user_social_id uid})))))

(defn assign-user-to-account!
  "Assign a user to an account"
  [user-id account-id]
  (update users
          (set-fields {:account_id account-id})
          (where {:id user-id})))
