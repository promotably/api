(ns api.views.users
  (:require [api.views.helpers.specs :refer [shape-to-spec user-spec]]))

(defn shape-response-body
  [user-model]
  (shape-to-spec user-model user-spec))
