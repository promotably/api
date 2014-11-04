(ns api.controllers.promos
  (:require [api.lib.schema :refer :all]
            [api.models.promo :as promo]
            [api.models.site :as site]
            [api.lib.coercion-helper :refer [transform-map
                                             remove-nils
                                             make-trans
                                             custom-matcher
                                             underscore-to-dash-keys]]
            [api.models.site :as site]
            [api.views.promos :refer [shape-promo
                                      shape-lookup
                                      shape-new-promo
                                      shape-validate
                                      shape-calculate]]
            [api.lib.auth :refer [parse-auth-string auth-valid? transform-auth]]
            [clojure.data.json :refer [read-str]]
            [clojure.tools.logging :as log]
            [schema.coerce :as c]
            [schema.core :as s]))

(def query-schema {:site-id s/Uuid
                   (s/optional-key :promotably-auth) s/Str
                   :code s/Str})

(defn lookup-promos
  [{:keys [params] :as request}]
  (let [{:keys [site-id] :as coerced-params}
        ((c/coercer PromoLookup
                    (c/first-matcher [custom-matcher
                                      c/string-coercion-matcher]))
         params)
        found (promo/find-by-site-uuid site-id)]
    (shape-lookup found)))

;; TODO: enforce ownership
(defn delete-promo!
  [{:keys [params body] :as request}]
  (let [{:keys [promo-id]} params
        promo-uuid (java.util.UUID/fromString promo-id)
        found (promo/find-raw :uuid promo-uuid)]
    (if found
      (do
        (promo/delete-by-uuid (:site-id found) promo-uuid)
        {:status 200})
      {:status 404})))

(defn create-new-promo!
  [{:keys [params body] :as request}]
  (let [input-edn (clojure.edn/read-string (slurp body))
        site-id (:site-id input-edn)
        ;; TODO: Handle the site not being found
        id (site/get-id-by-site-uuid site-id)
        coerced-params ((c/coercer NewPromo
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        input-edn)]
    (shape-new-promo
     (promo/new-promo! (assoc coerced-params :site-id id)))))

(defn update-promo!
  [{:keys [params body] :as request}]
  (let [{:keys [promo-id]} params
        input-edn (clojure.edn/read-string (slurp body))
        coerced-params ((c/coercer NewPromo
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        (dissoc input-edn :promo-id))
        ;; TODO: Handle the site not being found
        id (site/get-id-by-site-uuid (:site-id coerced-params))]
    (shape-new-promo
     (promo/update-promo! promo-id (assoc coerced-params :site-id id)))))

(defn show-promo
  [{:keys [promo-id params body] :as request}]
  (let [promos (promo/find-by-site-and-uuid (:site-id params) promo-id)]
    (shape-new-promo (first promos))))

;; TODO: Check auth
(defn query-promo
  [{:keys [params] :as request}]
  (let [matcher (c/first-matcher [custom-matcher c/string-coercion-matcher])
        coercer (c/coercer query-schema matcher)
        {:keys [site-id code] :as coerced-params} (coercer params)
        the-promo (promo/find-by-site-uuid-and-code
                   site-id
                   (clojure.string/upper-case code))]
    (if-not the-promo
      {:status 404 :body "Can't find that promo"}
      (do
        {:body (shape-promo the-promo)}))))

(def coerce-site-id
  (make-trans #{:site-id}
              #(vector :site (if (string? %2)
                               (-> %2 java.util.UUID/fromString site/find-by-site-uuid)
                               nil))))

(defn prep-incoming
  [params]
  (let [dbg (partial prn "---")]
    (-> params
        underscore-to-dash-keys
        remove-nils
        coerce-site-id
        ;; (doto dbg)
        transform-auth)))

(defn validate-promo
  [{:keys [params body] :as request}]
  (let [slurped (slurp body)
        input-json (read-str slurped :key-fn keyword)
        matcher (c/first-matcher [custom-matcher c/string-coercion-matcher])
        coercer (c/coercer PromoValidionRequest matcher)
        coerced-params (-> input-json
                           (assoc :promotably-auth
                             (:promotably-auth params))
                           prep-incoming
                           coercer)
        site-id (-> coerced-params :site :site-id)
        code (-> coerced-params :code clojure.string/upper-case)
        the-promo (promo/find-by-site-uuid-and-code site-id code)]

    (cond
     (not the-promo)
     {:status 404 :body "Can't find that promo"}

     (not (auth-valid? site-id
                       (-> coerced-params :site :api-secret)
                       (:auth coerced-params)
                       (assoc request :body slurped)))
     {:status 403}

     :else
     (let [[v errors] (promo/valid? the-promo coerced-params)
           resp (merge {:uuid (:uuid the-promo) :code code}
                       (if errors
                         {:valid false :messages errors}
                         {:valid true :messages []}))]
       {:status 201
        :headers {"Content-Type" "application/json; charset=UTF-8"}
        :body (shape-validate resp)}))))

(defn calculate-promo
  [{:keys [params body] :as request}]
  (let [slurped (slurp body)
        input-json (read-str slurped :key-fn keyword)
        matcher (c/first-matcher [custom-matcher c/string-coercion-matcher])
        coercer (c/coercer PromoValidionRequest matcher)
        coerced-params (-> input-json
                           (assoc :promotably-auth (:promotably-auth params))
                           prep-incoming
                           coercer)
        site-id (-> coerced-params :site :site-id)
        code (-> coerced-params :code clojure.string/upper-case)
        the-promo (promo/find-by-site-uuid-and-code site-id code)
        [context errors] (promo/valid? the-promo coerced-params)]
    (cond
     (not the-promo)
     {:status 404 :body "Can't find that promo"}

     errors
     {:status 400}

     (not (auth-valid? site-id
                       (-> coerced-params :site :api-secret)
                       (:auth coerced-params)
                       (assoc request :body slurped)))
     {:status 403}

     :else
      (let [amt (promo/discount-amount the-promo context errors)]
        {:status 201
         :headers {"Content-Type" "application/json; charset=UTF-8"}
         :body (shape-calculate {:valid true :discount amt})}))))
