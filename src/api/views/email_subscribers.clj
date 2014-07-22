(ns api.views.email-subscribers
  (:require [api.views.helper :refer [view-value-helper]]
            [clojure.data.json :refer [write-str]]))

(defn render-create
  [create-result]
  (let [status-code
        (cond
         (and (not (:success create-result))
              (= (:error create-result) :email-already-exists)) 409
         (:success create-result) 201
         :else 500)]
    {:status status-code :body (write-str create-result
                                          :value-fn view-value-helper)}))
