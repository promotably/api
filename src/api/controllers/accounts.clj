(ns api.controllers.accounts
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.models.account :as account]
            [api.views.accounts :refer [shape-create]]
            [schema.core :as s]
            [schema.coerce :as c]))


(let [inbound-schema {(s/required-key :email) s/Str
                      (s/required-key :first-name) s/Str
                      (s/required-key :last-name) s/Str}]
  (defn create-new-account!
    "Creates a new account in the database. Also creates a user"
    [{:keys [params body] :as request}]
    (let [input-edn (clojure.edn/read-string (slurp body))
          coerced-params
          ((c/coercer inbound-schema
                      (c/first-matcher [custom-matcher
                                        c/string-coercion-matcher]))
           input-edn)]
      (shape-create (account/new-account! coerced-params)))))
