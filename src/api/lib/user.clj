(ns api.lib.user
  (:require [schema.core :as s]
            [schema.macros :as sm]))

(def SqlErrorEnum (s/enum :username-exists :email-exists))

(sm/defn parse-sql-exception :- SqlErrorEnum
  "Attempts to make sense of a user-related SQL error"
  [ex :- org.postgresql.util.PSQLException]
  (let [message (.getMessage ex)]
    (cond (seq? (re-seq #"duplicate key value violates unique constraint \"username_idx\"" message)) :username-exists
          (seq? (re-seq #"duplicate key value violates unique constraint \"email_idx\"" message)) :email-exists
          :else nil)))
