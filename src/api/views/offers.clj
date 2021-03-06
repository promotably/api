(ns api.views.offers
  (:require [api.views.helper :refer [view-value-helper]]
            [clojure.data.json :refer [write-str]]
            [clj-time.coerce :as t-coerce]
            [clj-time.core :as t]))

(defn shape-one
  [offer]
  (-> offer
      (assoc :conditions
        (map (fn [c] (dissoc c :id :offer-id))
             (:conditions offer))
        :offer-id (:uuid offer))
      (dissoc :id :uuid :site-id)))

(defn shape-offer
  [offer]
  {:status 200
   :headers {"Cache-Control" "max-age=0, no-cache"}
   :body (shape-one offer)})

(defn shape-lookup
  [results]
  {:status 200
   :headers {"Cache-Control" "max-age=0, no-cache"}
   :body (vec (map shape-one results))})

(defn shape-new-offer
  [site-uuid {:keys [success error message offer] :as response}]
  (cond
   (true? success) {:status 201
                    :headers {"Cache-Control" "max-age=0, no-cache"}
                    :body (write-str (-> offer
                                         (assoc :offer-id (:uuid offer))
                                         (assoc :site-id site-uuid)
                                         (dissoc :uuid)
                                         (dissoc :id))
                                     :value-fn (fn [k v] (view-value-helper v)))}
   (and (false? success) (= error :already-exists)) {:status 409 :body message}
   :else {:status 500}))

(defn shape-update-offer
  [site-uuid {:keys [success error message offer] :as response}]
  (cond
   (true? success) {:status 200
                    :headers {"Cache-Control" "max-age=0, no-cache"}
                    :body (write-str (-> offer
                                         (assoc :offer-id (:uuid offer))
                                         (assoc :site-id site-uuid)
                                         (dissoc :uuid)
                                         (dissoc :id))
                                     :value-fn (fn [k v] (view-value-helper v)))}
   :else {:status 500}))

(defn shape-rcos
  [session offers]
  (let [s (cond-> session
                  (seq offers) (assoc :last-offer-at (t-coerce/to-string (t/now))))
        resp {:headers {"Content-Type" "text/javascript"
                        "Cache-Control" "max-age=0, no-cache"}
              :session s
              :body (write-str
                     {:offers offers}
                     :value-fn (fn [k v] (view-value-helper v)))}]
    resp))
