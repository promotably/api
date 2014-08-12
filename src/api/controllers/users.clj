(ns api.controllers.users
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.models.user :as user]
            [api.models.account :as account]
            [api.views.accounts :refer [shape-get-user shape-create-user shape-update-user]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema.coerce :as c]))

(let [inbound-schema {(s/required-key :email) s/Str
                      (s/required-key :user-social-id) s/Str
                      (s/required-key :account-id) s/Uuid
                      (s/optional-key :username) s/Str
                      (s/optional-key :phone) s/Str
                      (s/optional-key :job-title) s/Str
                      (s/optional-key :first-name) s/Str
                      (s/optional-key :last-name) s/Str}]
  (defn create-new-user!
    [{:keys [body] :as request}]
    (let [input-edn (clojure.edn/read-string (slurp body))
          coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          input-edn)
          result (user/new-user! coerced-params)]
      (println result)
      (shape-create-user result))))


(let [inbound-schema {(s/required-key :user-id) s/Uuid
                      (s/optional-key :email) s/Str
                      (s/optional-key :username) s/Str
                      (s/optional-key :phone) s/Str
                      (s/optional-key :job-title) s/Str
                      (s/optional-key :first-name) s/Str
                      (s/optional-key :last-name) s/Str
                      (s/optional-key :user-social-id) s/Str}]
  (defn update-user!
    [{body :body {:keys [user-id]} :params :as request}]
    (let [input-edn (clojure.edn/read-string (slurp body))
          coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          (assoc input-edn :user-id user-id))]
      (shape-update-user (user/update-user! coerced-params)))))

(defn get-user
  [{:keys [params] :as request}]
  (let [u (user/find-by-user-social-id (:user-social-id params))]
    (shape-get-user
     {:user u
      :account (when u (account/find-by-id (:account-id u)))})))
