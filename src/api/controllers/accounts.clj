(ns api.controllers.accounts
  (:use clojure.walk)
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.models.account :as account]
            [api.views.accounts :refer [shape-create shape-update]]
            [schema.core :as s]
            [schema.coerce :as c]))

(defn underscore-to-dash-keys
  [form]
  (postwalk (fn [x] (if (keyword? x)
                      (keyword (clojure.string/replace (name x) "_" "-"))
                      x)) form))

;; (underscore-to-dash-keys {:team_fortress {:bad_thing 1}})

(let [inbound-schema {(s/required-key :email) s/Str
                      (s/optional-key :browser-id) s/Uuid
                      (s/required-key :first-name) s/Str
                      (s/required-key :last-name) s/Str
                      (s/required-key :user-social-id) s/Str}]
  (defn create-new-account!
    "Creates a new account in the database. Also creates a user"
    [{:keys [params body] :as request}]
    (let [input-edn (clojure.edn/read-string (slurp body))
          coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          input-edn)
          results (account/new-account! coerced-params)]
      (shape-create (underscore-to-dash-keys results)))))

(let [inbound-schema {(s/required-key :account-id) s/Uuid
                      (s/optional-key :company-name) s/Str}]
  (defn update-account!
    [{body :body {:keys [account-id]} :params}]
    (let [input-edn (clojure.edn/read-string (slurp body))
          coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          (merge input-edn {:account-id account-id}))
          result (account/update! coerced-params)]
      (println result)
      (shape-update (underscore-to-dash-keys result)))))
