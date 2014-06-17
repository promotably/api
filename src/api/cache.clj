(ns api.cache
  (:require [clj-time.core :as t]
            [joda-time :as jt]
            [clojure.core.reducers :as r]
            [clojure.tools.logging :as log]
            [api.lib.protocols :refer (EventCache)])
  (:import (java.util.concurrent Executors TimeUnit
                                 ScheduledExecutorService)))

;;(defn- export-cache-events [events storage-conn])

(defn- clean-cache [cache window]
  (swap! cache (fn [c]
                 (remove (fn [item]
                           (let [last-event-time (:event-time item)
                                 expiry-predicate-time (jt/minus (jt/date-time) (jt/minutes window))]
                             (jt/after? last-event-time expiry-predicate-time))))
                 c)))

(defn- init-scheduler [cache ^ScheduledExecutorService scheduler]
  (.scheduleWithFixedDelay
   scheduler
   #(clean-cache cache 30)
   5 5 TimeUnit/MINUTES))

(defrecord ExportingEventCache [cache scheduler]
  EventCache
  (init [this]
    (log/info :INITIALIZING "Cache is starting...")
    (init-scheduler cache scheduler)
    (doto (promise) (deliver nil)))
  (shutdown [this]
    (log/info :SHUTTINGDOWN "Cache is shutting down...")
    (.shutdownNow ^ScheduledExecutorService scheduler)
    (reset! cache nil)
    (doto (promise) (deliver nil)))
  (query [this filter-fn]
    (r/filter filter-fn cache))
  (insert [this event]
    (swap! cache (fn [c]
                   (conj c (conj event {:event-time (jt/date-time)}))))))


(defn exporting-event-cache []
  (let [event-cache (atom [])
        scheduler (Executors/newSingleThreadScheduledExecutor)]
    (->ExportingEventCache event-cache scheduler)))
