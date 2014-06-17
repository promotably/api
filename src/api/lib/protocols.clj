(ns api.lib.protocols)

(defprotocol EventCache
  "Contract for the implementation of a cache of events."
  (init [this]
    "Initialize the cache")
  (shutdown [this]
    "Shutdown the cache")
  (query [this filter-fn]
    "Returns items from the cache that pass the provided filter function.")
  (insert [this event]))
