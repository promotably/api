(ns api.lib.auth
  (:require clojure.string
            [clojure.data.json :as json]
            [api.lib.coercion-helper :refer [make-trans]]
            [api.lib.seal :refer [hmac-sha1 url-encode]]))

(defn parse-auth-string
  [auth-string]
  (if auth-string
    (let [parts (clojure.string/split (or auth-string "") #"/" 5)
          [scheme headers qs-fields ts sig] parts
          headers* (filter #(not (or (nil? %) (= "" %)))
                          (clojure.string/split (or headers "") #","))
          qs-fields* (filter #(not (or (nil? %) (= "" %)))
                          (clojure.string/split (or qs-fields "") #","))]
      {:scheme scheme
       :qs-fields qs-fields*
       :timestamp ts
       :signature sig
       :headers headers*})))

(defn auth-valid?
  [site-id
   api-secret
   {:keys [scheme qs-fields timestamp signature headers] :as auth-map}
   {:keys [body query-string params] :as request}]
  (if auth-map
    (let [request-headers (:headers request)
          slurped (cond
                   (string? (:raw-body request)) (:raw-body request)
                   (nil? (:raw-body request)) nil
                   :else (-> request :raw-body slurp))
          body-hmac (if-not (or (= "" slurped) (nil? slurped))
                      (hmac-sha1 (.getBytes ^String (str api-secret))
                                 (.getBytes ^String slurped)))
          header-values (mapcat #(vector (str % ":" (get request-headers %)))
                                headers)
          header-str (apply str (interpose "\n" header-values))
          qs-fields (filter #(not (or (nil? %) (= "" %))) qs-fields)
          qs-vals (mapcat #(vector (str % "=" (get params (keyword %))))
                          qs-fields)
          qs-str (apply str (interpose "&" qs-vals))
          uri (:uri request)
          sign-me (apply str
                         site-id "\n"
                         api-secret "\n"
                         (:server-name request) "\n"
                         (-> request
                             :request-method name clojure.string/upper-case) "\n"
                             (url-encode uri) "\n"
                             timestamp "\n"
                             body-hmac "\n"
                             header-str "\n"
                             qs-str "\n")
          computed-sig (hmac-sha1 (.getBytes ^String (str api-secret))
                                  (.getBytes ^String sign-me))]
      (= computed-sig signature))))

(def transform-auth
  (make-trans #{:promotably-auth}
              (fn [k v]
                [:auth (parse-auth-string v)])))
