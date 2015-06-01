(ns api.controllers.phone-home
  (:import [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
           [com.amazonaws.auth.profile ProfileCredentialsProvider])
  (:require
   [clojure.string :refer [lower-case] :as str]
   [clojure.tools.logging :as log]
   [clojure.data.json :as json]
   [amazonica.aws.s3]
   [api.lib.util :as util]
   [api.models.static :refer [fetch-static]]
   [org.ozias.cljlibs.semver.semver :refer :all]))

(defonce cached-plugins (atom {:cached-at nil :content nil}))
(defonce cached-metadata (atom nil))

(defn normalize-metadata
  [meta]
  (-> {}
      ;; last-updated upgrade-notice?
      (assoc :slug "promotably")
      (assoc :name (:plugin-name meta))
      (assoc :author (:author meta))
      (assoc :version (:version meta))
      (assoc :tested (:tested-up-to meta))
      (assoc :requires (:requires-at-least meta))
      (assoc :download_url (:plugin-uri meta))
      (assoc :sections {;; :changelog "foo bar"
                        :description (:description meta)})))

;; TODO: record installed version to kinesis
;; params will look like {:checking-for-updates "1",
;;                        :site-id "...",
;;                        :promotably-auth "...",
;;                        :installed-version "1.2.1"}
(defn update
  [request]
  {:status 200
   :headers {"Content-Type" "application/json; charset=UTF-8"}
   :body (json/write-str (normalize-metadata @cached-metadata))})

(defn extract-entry
  [zis entry]
  (let [size (.getSize entry)
        buffer (byte-array size)
        isr (java.io.InputStreamReader. zis)
        rdr (java.io.BufferedReader. isr)
        s (line-seq rdr)
        meta-data (reduce
                   #(do
                      (if %2
                        (if-let [match (re-find #"\s+\*\s+([A-Za-z ]+):\s+(.*)" %2)]
                          (assoc %1 (-> match
                                        second
                                        lower-case
                                        (str/replace " " "-")
                                        keyword)
                                 (nth match 2))
                          %1)
                        %1))
                   {}
                   (line-seq rdr))]
    (reset! cached-metadata meta-data)))

(defn process-zipfile
  []
  (let [z (java.util.zip.ZipInputStream. (:content @cached-plugins))]
    (loop [e (.getNextEntry z)]
      (when e
        (when (= "promotably/promotably.php" (.getName e))
          (extract-entry z e))
        (.closeEntry z)
        (recur (.getNextEntry z))))
    (.close z)))

(defn list-plugins
  [cloudwatch-recorder aws bucket cached]
  (try
    (cloudwatch-recorder "plugins-list" 1 :Count)
    (let [_ (log/logf :info "Listing s3://%s using credentials '%s'."
                      bucket
                      (:credential-profile aws))
          resp (amazonica.aws.s3/list-objects (:credential-provider aws)
                                              :bucket-name bucket
                                              :prefix "woocommerce")
          latest-v (->> resp
                        :object-summaries
                        (map :key)
                        (map #(re-find #"\d+\.\d+\.\d+" %))
                        (sort compare-versions)
                        last
                        re-pattern)
          latest (->> resp
                      :object-summaries
                      (filter #(re-find latest-v (:key %)))
                      first)]
      (fetch-static cloudwatch-recorder
                    aws
                    bucket
                    (:key latest)
                    cached
                    identity)
      (process-zipfile))
    (catch Throwable t
      (cloudwatch-recorder "plugins-missing" 1 :Count)
      (log/logf :error
                "Can't list plugins. Bucket %s exception %s."
                bucket
                t))))


