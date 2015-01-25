(ns api.controllers.helper
  (:import [java.util UUID]))

(defn shape-inbound
  [params inbound-spec]
  (reduce-kv (fn [a k v]
               (assoc a k (v params))) {} params))

(def inbound-site-spec
  {:site-id (fn [site]
              (when-let [site-id (:site-id site)]
                (if (string? site-id)
                  (UUID/fromString site-id)
                  site-id)))
   :account-id (fn [site]
                 (when-let [account-id (:account-id site)]
                   (if (string? account-id)
                     (UUID/fromString account-id)
                     account-id)))
   :site-code :site-code
   :name :name
   :site-url :site-url
   :api-secret :api-secret
   :country :country
   :timezone :timezone
   :currency :currency
   :language :language})

(def inbound-account-spec
  {:account-id (fn [account]
                 (when-let [account-id (:account-id account)]
                   (if (string? account-id)
                     (UUID/fromString account-id)
                     account-id)))
   :company-name :company-name
   :user-id (fn [account]
              (when-let [user-id (:user-id account)]
                (if (string? user-id)
                  (UUID/fromString user-id)
                  user-id)))})

(def inbound-user-spec
  {:username :username
   :user-id (fn [user]
              (when-let [user-id (:user-id user)]
                (if (string? user-id)
                  (UUID/fromString user-id)
                  user-id)))
   :email :email
   :password :password
   :user-social-id :user-social-id
   :phone :phone
   :first-name :first-name
   :last-name :last-name
   :job-title :job-title
   :account-id (fn [user]
                 (when-let [account-id (:account-id user)]
                   (if (string? account-id)
                     (UUID/fromString account-id)
                     account-id)))})
