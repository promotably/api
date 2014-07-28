(ns api.controllers.accounts
  (:use clojure.walk)
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.models.account :as account]
            [api.views.accounts :refer [shape-create]]
            [schema.core :as s]
            [schema.coerce :as c]))

(defn underscore-to-dash-keys
  [form]
  (postwalk (fn [x] (if (keyword? x)
                      (keyword (clojure.string/replace (name x) "_" "-"))
                      x)) form))

;; (underscore-to-dash-keys {:team_fortress {:bad_thing 1}})

(let [inbound-schema {(s/required-key :email) s/Str
                      :browser-id s/Uuid
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
