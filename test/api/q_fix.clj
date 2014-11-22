(ns api.q-fix
  (:refer-clojure :exclude [set load])
  (:require
   [com.stuartsierra.dependency :as dep]))

(defmacro table
  "docstring"
  [table-name & fixtures]
  `(reduce
    #(assoc %1 (first %2) (assoc (second %2) ::table-name ~table-name))
    {}
    (apply merge (vector ~@fixtures))))

(defmacro fixture
  "docstring"
  [fixture-name & fields]
  `{~fixture-name ~(apply hash-map fields)})

(defn compute-dep-graph
  "docstring"
  [merged-set]
  (reduce
   (fn [g1 [f-key f-vals]]
     (if-let [the-deps (seq (filter
                             keyword?
                             (vals
                              (dissoc f-vals ::table-name))))]
       (do
         (reduce
          #(dep/depend %1 f-key %2)
          g1
          the-deps))
       (dep/depend g1 f-key ::root)))
   (dep/graph)
   merged-set))

(defmacro set
  "docstring"
  [& tables]
  `(merge ~@tables))

(defn load
  "docstring"
  [fixture-set insert-fn]
  (let [dep-graph (compute-dep-graph fixture-set)
        ordered-fixture-ids (dep/topo-sort dep-graph)]
    (reduce
     (fn [insert-id-map fid]
       (let [tdeps (seq (rest (dep/transitive-dependencies dep-graph fid)))
             fmap (reduce
                   (fn [m [k v]]
                     (assoc m k (if (and (not (= ::table-name k))
                                         (keyword? v))
                                  (v insert-id-map)
                                  v)))
                   {}
                   (fid fixture-set))
             insert-id (insert-fn (::table-name fmap) (dissoc fmap ::table-name))]
         (assoc insert-id-map fid insert-id)))
     {}
     (rest ordered-fixture-ids))))
