(ns api.views.accounts)

(def default-response
  {:headers {"Content-Type" "application/edn; charset=UTF-8"}})

(defn shape-lookup-account
  [lookup-response]
  (let [sc (if (nil? (:user lookup-response)) 404 200)]
    (merge default-response
           {:status sc
            :body (pr-str (-> lookup-response
                              (assoc-in [:user]
                                        (dissoc (:user lookup-response) :id :account-id))
                              (assoc-in [:account]
                                        (dissoc (:account lookup-response) :id))))})))

(defn shape-create
  [create-response]
  (let [sc (cond
            (:success create-response) 201
            (and (not (:success create-response))
                 (= (:error create-response) :email-already-exists)) 409)]
    (merge default-response
           {:status sc
            :body (pr-str create-response)})))

(defn shape-update
  [update-response]
  (let [sc (condp = (:status update-response)
             :updated 200
             :does-not-exist 404)]
    (merge default-response
           {:status sc
            :body (pr-str (-> update-response
                              (assoc-in [:account]
                                        (dissoc (:account update-response) :id))))})))

(defn shape-get-user
  [result]
  (let [sc (if (:user result) 200 404)]
    (merge default-response
           {:status sc
            :body (pr-str (-> result
                              (assoc-in [:user] (dissoc (:user result) :id :account-id))
                              (assoc-in [:account] (dissoc (:account result) :id))))})))

(defn shape-create-user
  [result]
  (let [sc (if (= (:status result) :success)
             201
             (if (= (:error result) :email-exists)
               400
               500))]
    (merge default-response
           {:status sc
            :body (pr-str result)})))

(defn shape-update-user
  [result]
  (let [sc (if result
             204
             404)]
    (merge default-response
           {:status sc})))
