(ns fr.jeremyschoffen.mbt.alpha.default.compilation.java-test
  (:require
    [clojure.test :refer :all]
    [clojure.spec.test.alpha :as st]
    [testit.core :refer :all]
    [fr.jeremyschoffen.mbt.alpha.core :as mbt-core]
    [fr.jeremyschoffen.mbt.alpha.core.building.deps :as deps]
    [fr.jeremyschoffen.mbt.alpha.core.building.classpath :as classpath]
    [fr.jeremyschoffen.mbt.alpha.default.compilation.java :as compilation]
    [fr.jeremyschoffen.mbt.alpha.utils :as u]
    [fr.jeremyschoffen.java.nio.alpha.file :as fs]))


(st/instrument `[compilation/compile!])


;;----------------------------------------------------------------------------------------------------------------------
;; Setup
;;----------------------------------------------------------------------------------------------------------------------
(def wd (u/safer-path "resources-test" "test-repos" "java-compilation"))
(def target (u/safer-path wd "target"))
(def java-out-dir (u/safer-path wd "target" "classes"))

(def compiled-classes [(u/safer-path java-out-dir "example" "Example.class")
                       (u/safer-path java-out-dir "example" "Messenger.class")])

(def conf (-> (sorted-map
                :project/working-dir wd
                :cleaning/target target
                :compilation.java/output-dir java-out-dir)
              (u/assoc-computed :project/deps deps/get-deps
                                :classpath/raw classpath/raw-classpath
                                :classpath/index classpath/indexed-classpath)))



;;----------------------------------------------------------------------------------------------------------------------
;; fixtures
;;----------------------------------------------------------------------------------------------------------------------
(defn compile-examples! []
  (compilation/compile! conf))


(defn clean! []
  (mbt-core/clean! conf))

;;----------------------------------------------------------------------------------------------------------------------
;; Tests
;;----------------------------------------------------------------------------------------------------------------------
(defn run-program! []
  (-> {:project/working-dir (u/safer-path wd "target" "classes")
       :shell/command ["java" "example/Example"]}
      mbt-core/sh
      :out))

(deftest compilation
  (compile-examples!)

  (testing "Making sure the results are there."
    (fact (every? fs/exists? compiled-classes) => true))

  (testing "The program runs."
    (fact (run-program!) => "Message form the messenger.\n"))

  (clean!))
