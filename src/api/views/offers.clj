(ns api.views.offers
  (:require [api.views.helper :refer [view-value-helper]]
            [clojure.data.json :refer [write-str]]
            [clj-time.core :as t]
            [clj-time.format :as tf]))

(def the-chars "ABCDEFGHIJKLMNOPQRSTUVWXYZ")

(defn shape-one
  [offer]
  (let [coupon (select-keys offer [:code :description :reward-amount :reward-type :reward-applied-to :reward-tax])
        rco (select-keys offer [:presentation :reward])
        coupon* (assoc coupon :conditions (map (fn [c] (dissoc c :id :offer-id))
                                               (:conditions offer)))
        rco* {:display-text (get-in rco [:presentation :display-text])
              :presentation-type (get-in rco [:presentation :type])
              :presentation-page (get-in rco [:presentation :page])
              :expires (tf/unparse (tf/formatters :date-time-no-ms)
                                   (t/plus (t/now) (t/minutes (get-in rco [:reward :expiry-in-minutes]))))
              :dynamic-coupon-code (reduce
                                    #(let [c (str (nth the-chars (rand (count the-chars))))
                                           _ %2]
                                       (str %1 c))
                                    ""
                                    (range 6))}]
    (-> {:coupon coupon*
         :rco rco*}
        (dissoc :id :uuid :site-id))))

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
