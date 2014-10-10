(ns api.controllers.promos
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer :all]
            [api.models.promo :as promo]
            [api.models.site :as site]
            [api.views.promos :refer [shape-promo
                                      shape-lookup
                                      shape-new-promo
                                      shape-validate
                                      shape-calculate]]
            [clojure.data.json :refer [read-str]]
            [clojure.tools.logging :as log]
            [schema.coerce :as c]
            [schema.core :as s]))

(def query-schema {:site-id s/Uuid
                   (s/optional-key :promotably-auth) s/Str
                   :promo-code s/Str})

(def inbound-schema
  {(s/required-key :site-id) s/Uuid
   (s/required-key :code) s/Str
   (s/optional-key :promotably-auth) [s/Str]
   (s/required-key :shopper-id) (s/maybe s/Str)
   (s/required-key :shopper-email) s/Str
   (s/optional-key :applied-coupons) [s/Str]
   (s/optional-key :cart-contents) [{(s/required-key :product-id) s/Str
                                     (s/optional-key :product-title) s/Str
                                     (s/optional-key :product-type) s/Str
                                     (s/optional-key :product-categories) [s/Str]
                                     (s/optional-key :variation-id) s/Str
                                     (s/optional-key :variation) s/Str
                                     (s/optional-key :quantity) s/Int
                                     (s/optional-key :line-total) s/Num
                                     (s/optional-key :line-subtotal) s/Num
                                     (s/optional-key :line-tax) s/Num
                                     (s/optional-key :line-subtotal-tax) s/Num}]
   (s/optional-key :product-ids-on-sale) [s/Str]})

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
        found (promo/find-by-uuid promo-uuid)]
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
  (let [promos (promo/by-promo-id promo-id)]
    (shape-new-promo (first promos))))

;; TODO: Check auth
(defn query-promo
  [{:keys [params] :as request}]
  (let [matcher (c/first-matcher [custom-matcher c/string-coercion-matcher])
        coercer (c/coercer query-schema matcher)
        {:keys [site-id promo-code] :as coerced-params} (coercer params)
        the-promo (promo/find-by-site-uuid-and-code site-id
                                                    promo-code)]
    (prn the-promo)
    (if-not the-promo
      {:status 404 :body "Can't find that promo"}
      (do
        {:body (shape-promo the-promo)}))))

(defn validate-promo
  [{:keys [params body] :as request}]
  (let [input-json (read-str (slurp body) :key-fn keyword)
        {:keys [site-id code] :as coerced-params}
        ((c/coercer inbound-schema
                    (c/first-matcher [custom-matcher
                                      c/string-coercion-matcher]))
         input-json)
        the-promo (promo/find-by-site-uuid-and-code site-id code)]
    (if-not the-promo
      {:status 404 :body "Can't find that promo"}
      (let [v (promo/valid? the-promo coerced-params)
            resp (merge v {:uuid (:uuid the-promo)
                           :code code})]
        {:status 201
         :headers {"Content-Type" "application/json; charset=UTF-8"}
         :body (shape-validate resp)}))))

(defn calculate-promo
  [{:keys [params body] :as request}]
  (let [input-json (read-str (slurp body) :key-fn keyword)
        {:keys [site-id code] :as coerced-params}
        ((c/coercer inbound-schema
                    (c/first-matcher [custom-matcher
                                      c/string-coercion-matcher]))
         input-json)
        the-promo (promo/find-by-site-uuid-and-code site-id code)]
    (if-not the-promo
      {:status 404 :body "Can't find that promo"}
      (let [v false]
        (println (:type the-promo))
        (println (class (:type the-promo)))
        {:status 201
         :headers {"Content-Type" "application/json; charset=UTF-8"}
         :body (shape-calculate (merge v
                                       {:discount 0}))}))))
