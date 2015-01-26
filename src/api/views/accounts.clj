(ns api.views.accounts
  (:require [api.lib.schema :refer [shape-to-spec account-spec]]))

(defn shape-response-body
  [account]
  (shape-to-spec account account-spec))
