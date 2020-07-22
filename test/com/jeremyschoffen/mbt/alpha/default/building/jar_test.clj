(ns com.jeremyschoffen.mbt.alpha.default.building.jar-test
  (:require
    [clojure.test :refer [deftest testing]]
    [clojure.edn :as edn]
    [clojure.spec.test.alpha :as st]
    [testit.core :refer :all]
    [com.jeremyschoffen.java.nio.alpha.file :as fs]
    [com.jeremyschoffen.mbt.alpha.test.repos :as test-repos]

    [com.jeremyschoffen.mbt.alpha.core :as mbt-core]
    [com.jeremyschoffen.mbt.alpha.core.specs]
    [com.jeremyschoffen.mbt.alpha.default.building :as building]
    [com.jeremyschoffen.mbt.alpha.utils :as u]
    [com.jeremyschoffen.mbt.alpha.default.defaults :as defaults]))


(st/instrument [building/jar! building/uberjar!])

;;----------------------------------------------------------------------------------------------------------------------
;; helpers
;;----------------------------------------------------------------------------------------------------------------------
(defn jar-content [jar-path]
  (with-open [zfs (mbt-core/jar-open-fs jar-path)]
    (->> zfs
         fs/walk
         fs/realize
         (into {}
               (comp
                 (remove fs/directory?)
                 (map #(vector (str %) (slurp %))))))))
;;----------------------------------------------------------------------------------------------------------------------
;; Common values
;;----------------------------------------------------------------------------------------------------------------------


(def version "1.0")
(def group-id 'group)
(def author "Tester")
(def services-props-rpath (fs/path "resources" "META-INF" "services" "services.properties"))

(def project1-path test-repos/monorepo-p1)
(def project2-path test-repos/monorepo-p2)


(defn intruder? [{src :jar.entry/src}]
  (when-not src
    (throw (ex-info ":jar.entry/src can't be nil" {})))
  (->> src
       str
       (re-matches #".*intruder.txt")))


;;----------------------------------------------------------------------------------------------------------------------
;; Test skinny jar using project 2
;;----------------------------------------------------------------------------------------------------------------------
(def ctxt2 (-> (sorted-map
                 :project/working-dir project2-path
                 :project/version version
                 :project/author author
                 :maven/group-id group-id
                 :maven/artefact-name 'project-2
                 :build/jar-name "project2.jar"
                 :jar/exclude? intruder?)
               defaults/make-context
               building/ensure-jar-defaults))


(def ctxt2-i (-> ctxt2
                 (dissoc :jar/exclude?)
                 (assoc :build/jar-name "project2-i.jar")))


(deftest simple-jar
  (let [_ (building/jar! ctxt2)
        content (-> ctxt2
                    building/jar-out
                    jar-content)

        _ (building/jar! ctxt2-i)
        content+i (-> ctxt2-i
                      building/jar-out
                      jar-content)]

    (testing "The content that should be there is."
      (facts
        (get content "/project2/core.clj")
        => (slurp (u/safer-path project2-path "src" "project2" "core.clj"))

        (edn/read-string (get content "/META-INF/deps/group/project-2/deps.edn"))
        => (edn/read-string (slurp (u/safer-path project2-path "deps.edn")))

        (edn/read-string (get content "/data_readers.cljc"))
        => (edn/read-string (slurp (u/safer-path project2-path "src" "data_readers.cljc")))

        (get content "/META-INF/services/services.properties")
        => (slurp (u/safer-path project2-path services-props-rpath))))

    (testing "Filtered content isn't there"
      (contains? content "/project2/intruder.txt") => false)

    (testing "When not filtered intruder is here"
      (get content+i "/project2/intruder.txt")
      => (slurp (u/safer-path project2-path "src" "project2" "intruder.txt")))

    (mbt-core/clean! ctxt2)))

;;----------------------------------------------------------------------------------------------------------------------
;; Testing uber jar using project 1
;;----------------------------------------------------------------------------------------------------------------------
(defn- clojure-entry? [{src :jar.entry/src
                        :as param}]
  (if-not src
    (throw (ex-info ":jar.entry/src can't be nil" {}))
    (->> src
         str
         (re-matches #"/clojure/.*"))))


(def uberjar-exclude? (some-fn clojure-entry? intruder?))


(defn get-project1-deps [ctxt]
  (-> ctxt
      (mbt-core/deps-get)
      (assoc-in [:deps 'project2/project2 :local/root] (str project2-path))))


(def ctxt1 (-> {:project/working-dir project1-path
                :project/version version
                :project/author author
                :maven/group-id group-id
                :maven/artefact-name 'project-1
                :build/uberjar-name "project1-standalone.jar"
                :jar/exclude? uberjar-exclude?}
               defaults/make-context
               (u/assoc-computed :project/deps get-project1-deps)
               building/ensure-jar-defaults))

(deftest uberjar
  (try
    (let [_ (building/uberjar! ctxt1)
          content (-> ctxt1
                      building/uberjar-out
                      jar-content)
          services-1 (slurp (u/safer-path project1-path services-props-rpath))
          services-2 (slurp (u/safer-path project2-path services-props-rpath))]

      (facts
        (get content "/project1/core.clj")
        => (slurp (u/safer-path project1-path "src" "project1" "core.clj"))

        (get content "/project2/core.clj")
        => (slurp (u/safer-path project2-path "src" "project2" "core.clj"))


        (edn/read-string (get content "/data_readers.cljc"))
        => (merge (-> (u/safer-path project1-path "src" "data_readers.cljc")
                      slurp
                      edn/read-string)
                  (-> (u/safer-path project2-path "src" "data_readers.cljc")
                      slurp
                      edn/read-string))

        (or (= (get content "/META-INF/services/services.properties")
               (str services-1 "\n" services-2 "\n"))
            (= (get content "/META-INF/services/services.properties")
               (str services-2 "\n" services-1 "\n")))
        => true))
    (catch Exception e
      (throw e))
    (finally
      (mbt-core/clean! ctxt1))))
