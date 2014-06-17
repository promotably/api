(ns api.state)

(def ^:dynamic *global-state*
  nil)

(defn events-cache []
  (:events-cache *global-state*))
