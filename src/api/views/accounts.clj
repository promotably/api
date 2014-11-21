(ns api.views.accounts)

(defn shape-lookup-account
  [lookup-response]
  (let [sc (if (nil? (:user lookup-response)) 404 200)]
    {:status sc
     :body (-> lookup-response
               (assoc-in [:user]
                         (dissoc (:user lookup-response) :id :account-id))
               (assoc-in [:account]
                         (dissoc (:account lookup-response) :id)))}))

(defn shape-create
  [create-response]
  (let [sc (cond
            (:success create-response) 201
            (and (not (:success create-response))
                 (= (:error create-response) :email-already-exists)) 409)]
    {:status sc
     :body (-> create-response
               (assoc-in [:site]
                         (assoc (dissoc (:site create-response)
                                        :id :account-id :uuid)
                           :site-id (:uuid (:site create-response)))))}))

(defn shape-update
  [update-response]
  (let [sc (condp = (:status update-response)
             :updated 200
             :does-not-exist 404)]
    {:status sc
     :body (-> update-response
               (assoc-in [:account]
                         (dissoc (:account update-response) :id)))}))

(defn shape-get-user
  [result]
  (let [sc (if (:user result) 200 404)]
    {:status sc
     :body (-> result
               (assoc-in [:user] (dissoc (:user result) :id :account-id))
               (assoc-in [:account] (dissoc (:account result) :id)))}))

(defn shape-create-user
  [result]
  (let [sc (if (= (:status result) :success)
             201
             (if (= (:error result) :email-exists)
               400
               500))]
    {:status sc
     :body result}))

(defn shape-update-user
  [result]
  (let [sc (if result
             204
             404)]
    {:status sc}))
