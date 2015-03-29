(ns tasks.test-uuid
  (:require [api.vbucket :refer [pick-vbucket]]))

(defn -main []
  (loop [uuid (java.util.UUID/randomUUID)]
    (if (> (pick-vbucket uuid) 50)
      (println  "Test UUID " uuid " Bucket " (pick-vbucket uuid))
      (recur (java.util.UUID/randomUUID)))))
