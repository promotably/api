(ns api.views.offers
  (:require [api.views.helper :refer [view-value-helper]]
            [clojure.data.json :refer [write-str]]))

(defn shape-one
  [offer]
  (prn offer)
  (-> offer
      (assoc :conditions
        (map (fn [c] (dissoc c :id :offer-id))
             (:conditions offer))
        :offer-id (:uuid offer))
      (dissoc :id :uuid :site-id)))

(defn shape-offer
  [offer]
  {:status 200
   :body (pr-str (shape-one offer))})

(defn shape-lookup
  [results]
  {:status 200
   :body (pr-str (vec (map shape-one results)))})

(defn shape-new-offer
  [{:keys [success error message] :as response}]
  (cond
   (true? success) {:status 201}
   (and (false? success) (= error :already-exists)) {:status 409 :body message}
   :else {:status 500}))
