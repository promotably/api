(ns api.views.email-subscribers)

(defn render-create
  [create-result]
  (let [status-code
        (cond
         (:success create-result) 201
         :else 500)]
    {:status status-code
     :body (pr-str create-result)
     :headers {"Content-Type" "application/edn; charset=UTF-8"}}))
