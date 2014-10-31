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

(defn new-account!
  "Creates a new account in the database."
  [{:keys [email first-name last-name user-social-id
           site-code site-url api-secret company-name] :as params}]
  (if-not (user/find-by-email email)
    (let [a (insert accounts
                    (values {:company_name company-name
                             :created_at (sqlfn now)
                             :updated_at (sqlfn now)}))
          user (insert users
                       (values {:account_id (:id a)
                                :email email
                                :first_name first-name
                                :last_name last-name
                                :user_social_id user-social-id
                                :created_at (sqlfn now)}))
          site (insert sites
                       (values {:account_id (:id a)
                                :site_code site-code
                                :site_url site-url
                                :name company-name
                                :api_secret api-secret
                                :created_at (sqlfn now)
                                :updated_at (sqlfn now)}))]
      {:status :created
       :user (dissoc user :id)
       :account (dissoc a :id)
       :site site})
    {:status :error :error :email-already-exists}))


(sm/defn ^:always-validate find-by-id :- (s/maybe AccountSchema)
  [id :- s/Int]
  (db-to-account
   (first
    (select accounts
            (where {:id id})))))


(sm/defn ^:always-validate find-by-account-id :- (s/maybe AccountSchema)
  [account-id :- s/Uuid]
  (db-to-account
   (first
    (select accounts
            (where {:account_id account-id})))))

(defn update!
  [{:keys [account-id company-name first-name last-name
           user-social-id site-name site-code api-secret site-url] :as params}]
  (let [the-account (find-by-account-id account-id)
        site-update (assoc* {}
                            :site_code site-code
                            :name company-name
                            :api_secret api-secret
                            :site_url site-url)
        user-update (assoc* {} :first_name first-name
                               :last_name last-name
                               :user_social_id user-social-id)]
    (update accounts
            (set-fields {:company_name company-name})
            (where {:account_id account-id}))
    (if-not (empty? site-update)
      (update sites
              (set-fields site-update)
              (where {:account_id (:id the-account)})))
    (if-not (empty? user-update)
      (update users
              (set-fields user-update)
              (where {:account_id (:id the-account)})))
    (if the-account
      (let [a (find-by-account-id account-id)
            s (first (site/find-by-account-uuid account-id))
            u (user/find-by-account-uuid account-id)]
        {:status :updated
         :account a
         :site s
         :user u})
      {:status :does-not-exist :account nil :site nil :user nil})))
