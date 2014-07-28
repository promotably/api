(ns api.views.accounts)

(defn shape-create
  [create-response]
  (let [sc (cond
            (:success create-response) 201
            (and (not (:success create-response))
                 (= (:error create-response) :email-already-exists)) 409)]
    {:status sc
     :body (pr-str create-response)
     :headers {"Content-Type" "application/edn; charset=UTF-8"}}))

(defn shape-get-user
  [result]
  (let [sc (if (:user result) 200 404)]
    {:status sc
     :body (pr-str result)
     :headers {"Content-Type" "application/edn; charset=UTF-8"}}))
