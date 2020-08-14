(ns ^{:author "Jeremy Schoffen"
      :doc "
Api providing utilities when manipulating classpaths generated using `clojure.tools.deps`.
      "}
  fr.jeremyschoffen.mbt.alpha.core.building.classpath
  (:require
    [clojure.string :as string]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.maven :as maven]
    [fr.jeremyschoffen.java.nio.alpha.file :as fs]
    [fr.jeremyschoffen.mbt.alpha.core.specs]
    [fr.jeremyschoffen.mbt.alpha.utils :as u]))


;; adapted from https://github.com/EwenG/badigeon/blob/master/src/badigeon/classpath.clj#L6
(defn raw-classpath
  "Returns the raw string classpath generated by tools.deps."
  [{project-deps :project/deps
    aliases      :project.deps/aliases}]
  (let [deps-map (update project-deps :mvn/repos #(merge maven/standard-repos %))
        args-map (deps/combine-aliases deps-map aliases)]

    (-> (deps/resolve-deps deps-map args-map)
        (deps/make-classpath (:paths deps-map) args-map))))

(u/spec-op raw-classpath
           :param {:req [:project/deps]
                   :opt [:project.deps/aliases]}
           :ret :classpath/raw)


(defn- jar? [path]
  (string/ends-with? path ".jar"))


(defn- project-path? [wd path]
  (fs/ancestor? wd path))


(defn- classify
  "Classifies the different entries of a classpath. Categories are:
  - :classpath/jar: path to a jar
  - :classpath/dir: path to a directory that is in the working dir
  - :classpath/ext-dep: path to a directory outside the working dir
  - :classpath/nonexisting: path in the classpath that leads to nothing
  - :classpath/file: individual file on the classpath
  "
  [wd path]
  (cond
    (not (fs/exists? path)) :classpath/nonexisting
    (jar? path) :classpath/jar
    (fs/directory? path) (if (project-path? wd path)
                           :classpath/dir
                           :classpath/ext-dep)
    :else :classpath/file))


(defn- index-classpath
  [cp wd]
  (-> cp
      (string/split (re-pattern (System/getProperty "path.separator")))
      (->> (into [] (comp
                      (map (partial fs/resolve wd))
                      (map str)))
           sort
           (group-by (partial classify wd)))))


(defn indexed-classpath
  "Construct a classpath using [[fr.jeremyschoffen.mbt.alpha.core.specs/raw-classpath]] then group the different entries inside a map.
  The keys of this map are defined in [[fr.jeremyschoffen.mbt.alpha.core.specs/classpath-index-categories]], the values
  are a seq of classpath entries corresponding to the categories.
  "
  [{wd :project/working-dir
    :as param}]
  (-> param
      raw-classpath
      (index-classpath wd)))

(u/spec-op indexed-classpath
           :deps [raw-classpath]
           :param {:req [:project/working-dir
                         :project/deps]
                   :opt [:project.deps/aliases]}
           :ret :classpath/index)


(comment
  (require '[clojure.tools.deps.alpha.reader :as deps-reader])
  (def state {:project/working-dir (u/safer-path)
              :project/deps (deps-reader/slurp-deps "deps.edn")})

  (raw-classpath {:project/deps (deps-reader/slurp-deps "deps.edn")})
  (indexed-classpath state))
