(ns lacinia2datomic.core
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]))

(defn read-schema
  [path]
  (-> (slurp path)
      (edn/read-string)))

;; lacinia-type: datomic-type
(def types
  {'String      "string"
   'Boolean     "boolean"
   'Int         "int"
   'Long        "long"
   'Float       "float"
   'DateTime    "instant"
   'UUID        "uuid"
   'URI         "uri"})

(defn ->datomic-type
  [type]
  (cond
    (and (coll? type) (= 'non-null (first type)))
    (->datomic-type (second type))

    :else
    (if-let [type (get types type)]
      (keyword "db.type" type)
      :db.type/ref)))

(defrecord DatomicId [])
(defn read-id
  [args]
  (->DatomicId))

(defmethod print-method DatomicId [x ^java.io.Writer writer]
  (print-method "#db/id [:db.part/db]" writer))

(defmethod print-dup DatomicId [x ^java.io.Writer writer]
  (print-dup "#db/id [:db.part/db]" writer))

(defn pprint-datomic-id
  [datomic-id]
  (clojure.pprint/with-pprint-dispatch print  ;;Make the dispatch to your print function
    (clojure.pprint/pprint datomic-id)))

(. clojure.pprint/simple-dispatch addMethod DatomicId pprint-datomic-id)

(defn build-schema
  [e a {:keys [type unique index doc fulltext isComponent noHistory]
        :or   {index false
               doc "No docs provided"
               fulltext false
               unique false
               isComponent false
               noHistory false}
        :as spec}]
  (let [ns-ident (keyword (name e) (name a))
        one? (if (and (sequential? type)
                      (= 'list (first type)))
               false true)]
    (cond->
      {:db/id                 #db/id [:db.part/db]
       :db/ident              ns-ident
       :db/valueType          (->datomic-type type)
       :db/cardinality        (if one? :db.cardinality/one :db.cardinality/many)
       :db/doc                doc
       :db/noHistory          noHistory
       :db.install/_attribute :db.part/db}
      one?
      (merge {:db/unique             :db.unique/identity
              :db/index              index
              :db/fulltext           fulltext
              :db/isComponent        isComponent}))))

(defn lacinia->datomic
  [{:keys [enums objects] :as schema}
   {:keys [exclude index unique noHistory isComponent fulltext] :as options}]
  (let [exists? (fn [col e a]
                  (contains? (get col e) a))
        objects (-> (for [[e {:keys [fields]}] (remove (fn [[e _]] (contains? (set exclude) e)) objects)]
                      (for [[a spec] fields]
                        (build-schema e a
                                      (assoc spec
                                             :index (exists? index e a)
                                             :unique (exists? unique e a)
                                             :noHistory (exists? noHistory e a)
                                             :isComponent (exists? isComponent e a)
                                             :fulltext (exists? fulltext e a)
                                             ))))
                    (flatten))
        enums (-> (for [[enum {:keys [values]}] enums]
                    (for [value (set values)]
                      {:db/ident (keyword (name enum) (name value))}))
                  (flatten))]
    (vec (concat objects enums))))

(defn save-datomic-schema
  [read-path write-path options]
  (let [schema (-> read-path
                   (read-schema)
                   (lacinia->datomic options)
                   (pprint)
                   (with-out-str)
                   )]
    (spit write-path schema)))

(defn save-conformity
  [read-path write-path options]
  (let [schema (-> read-path
                   (read-schema)
                   (lacinia->datomic options))
        schema (-> {:project/schema {:txes [schema]}}
                   (pprint)
                   (with-out-str))]
    (spit write-path schema)))
