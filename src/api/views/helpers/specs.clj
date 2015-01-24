(ns api.views.helpers.specs)

(defn shape-to-spec
  "Given a model (a map) and a spec map, returns a map that consists of
  the keys from the spec map and the values produced by calling the
  associated functions of the spec with the model as their only
  argument."
  [model spec]
  (reduce-kv (fn [a k v]
               (assoc a k (v model))) {} spec))

;;;;;;;;;
;;
;; Here be "specs." Specs are maps of {:response-key model-value-fn}
;; They are passed to the shape-to-spec function along with the model (a map).
;; THe model-value-fn takes the model as it's argument and produces
;; the value for the corresponding response key.
;;
;;;;;;;;

(def site-spec
  {:site-id (fn [site] ; uuid coerced to string
              (str (:site-id site)))
   :site-code :site-code
   :name :name
   :site-url :site-url
   :api-secret (fn [site] ; uuid coerced to string
                 (str (:api-secret site)))
   :country :country
   :timezone :timezone
   :currency :currency
   :language :language})

(def account-spec
  {:account-id (fn [account] ; uuid coerced to string
                 (str (:account-id account)))
   :company-name :company-name
   :sites (fn [account] ; hard-coding a vector response here
            (if-let [sites (:sites account)]
              (if (and (not (map? sites))
                       (coll? sites))
                (mapv #(shape-to-spec % site-spec) sites)
                [(shape-to-spec sites site-spec)])
              []))})

(def user-spec
  {:user-id (fn [user] ; uuid coerced to string
              (str (:user-id user)))
   :first-name :first-name
   :last-name :last-name
   :email :email
   :has-password (fn [user] ; boolean
                   (not (nil? (:password user))))
   :phone :phone
   :job-title :job-title
   :accounts (fn [user] ; hard-cording a vector response here
               (if-let [account (:account user)]
                 (if-not (empty? account)
                   (if (and (not (map? account))
                            (coll? account))
                     (mapv #(shape-to-spec % account-spec) account)
                     [(shape-to-spec account account-spec)])
                   [])
                 []))})
