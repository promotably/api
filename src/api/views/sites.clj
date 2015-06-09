(ns api.views.sites
  (:require [api.lib.schema :refer [shape-to-spec site-spec]]))

(defn shape-response-body
  [site]
  (shape-to-spec site site-spec))
