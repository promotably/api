(ns api.models.linked-products
  (:require [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher dash-to-underscore-keys]]
            [api.lib.schema :refer :all]
            [api.models.redemption :as redemption]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [korma.db :refer [transaction]]
            [clojure.set :refer [rename-keys intersection]]
            [clj-time.core :refer [before? after? now]]
            [schema.core :as s]
            [schema.coerce :as sc]))

(defn- db-to-linked
  [r]
  (let [ks (keys r)
        hyphenified-params (rename-keys r (zipmap ks (map hyphenify-key ks)))]
    ((sc/coercer Linked
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     hyphenified-params)))


(def DatabaseLinked
  {(s/optional-key :id) s/Int
   (s/optional-key :promo-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :url) s/Str
   (s/required-key :photo-url) s/Str
   (s/required-key :name) s/Str
   (s/required-key :original-price) s/Num
   (s/required-key :seo-copy) s/Str})

(defn- linked-to-db
  [{:keys [type] :as linked}]
  (dash-to-underscore-keys
   ((sc/coercer DatabaseLinked
                (sc/first-matcher [custom-matcher
                                   sc/string-coercion-matcher]))
    linked)))

(defn create!
  [c]
  (let [coerced (seq (map linked-to-db c))]
    (doall (map
            #(insert linked-products (values %))
            coerced))))

(defn delete!
  [promo-id]
  (delete linked-products (where {:promo_id promo-id})))

(defn update!
  [promo-id c]
  (transaction
   (delete! promo-id)
   (create! c)))
