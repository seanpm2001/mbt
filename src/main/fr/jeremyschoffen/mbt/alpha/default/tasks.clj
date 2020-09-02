(ns ^{:author "Jeremy Schoffen"
      :doc "
Higher level apis.
      "}
  fr.jeremyschoffen.mbt.alpha.default.tasks
  (:require
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]

    [fr.jeremyschoffen.mbt.alpha.core :as mbt-core]
    [fr.jeremyschoffen.mbt.alpha.default.jar :as default-jar]
    [fr.jeremyschoffen.mbt.alpha.default.versioning :as v]
    [fr.jeremyschoffen.mbt.alpha.default.specs]
    [fr.jeremyschoffen.mbt.alpha.utils :as u]))


(u/pseudo-nss
  build
  build.jar
  build.uberjar
  git
  git.commit
  jar
  jar.manifest
  maven
  maven.pom
  maven.scm
  project
  project.deps
  versioning)

;;----------------------------------------------------------------------------------------------------------------------
;; Scm info
;;----------------------------------------------------------------------------------------------------------------------

;;----------------------------------------------------------------------------------------------------------------------
;; from https://github.com/technomancy/leiningen/blob/master/src/leiningen/pom.clj#L83
(defn- parse-github-url
  "Parses a GitHub URL returning a [username repo] pair."
  [url]
  (if url
    (next
      (or (re-matches #"(?:[A-Za-z-]{2,}@)?github.com:([^/]+)/([^/]+).git" url)
          (re-matches #"[^:]+://(?:[A-Za-z-]{2,}@)?github.com/([^/]+)/([^/]+?)(?:.git)?" url)))))



(defn- github-urls [url]
  (if-let [[user repo] (parse-github-url url)]
    {:public-clone (str "scm:git:git://github.com/" user "/" repo ".git")
     :dev-clone (str "scm:git:ssh://git@github.com/" user "/" repo ".git")
     :browse (str "https://github.com/" user "/" repo)}))
;;----------------------------------------------------------------------------------------------------------------------

(defn make-github-scm [{scm ::maven/scm :as param}]
  (let [{url ::maven.scm/url} scm
        {:keys [public-clone dev-clone browse]} (github-urls url)
        {commit-name ::git.commit/name} (mbt-core/git-last-commit param)]
    (if url
      (-> scm
          (cond-> browse (u/ensure-v ::maven.scm/url browse)
                  public-clone (u/ensure-v ::maven.scm/connection public-clone)
                  dev-clone (u/ensure-v ::maven.scm/developer-connection dev-clone))
          (assoc ::maven.scm/tag commit-name))
      scm)))

(u/spec-op make-github-scm
           :deps [mbt-core/git-last-commit]
           :param {:req [::git/repo]}
           :ret ::maven/scm)


;;----------------------------------------------------------------------------------------------------------------------
;; Pre bump generation
;;----------------------------------------------------------------------------------------------------------------------
(defn- commit-generated! [conf]
  (-> conf
      (u/augment-v ::git/commit! {::git.commit/message "Added generated files."})
      mbt-core/git-commit!))

(u/spec-op commit-generated!
           :deps [mbt-core/git-commit!]
           :param {:req [::git/repo
                         ::git/commit!]})


(defn generate-before-bump!
  "Helper function intended to be used just before tagging a new version. The idea here is that when we want to release
  a new version, we want to generate some docs or a version file for instance. These files will need to be generated,
  added and committed to the repo. Also, adding this commit may influence the version number of the realease.

  This function attemps to provide a way to encapsulate this logic. It is performed in several steps:

  1) Checks the repo using [[fr.jeremyschoffen.mbt.alpha.default.versioning/check-repo-in-order]].
  2) Thread `conf` through `fns` using [[fr.jeremyschoffen.mbt.alpha.utils//thread-fns]].
  3) Add all the new files to git using [[fr.jeremyschoffen.mbt.alpha.core/git-add-all!]]
  4) Commit all the generated files.


  Args:
  - `conf`: a map, the build's configuration
  - `fns`: functions, presumably functions generating docs or a version file."
  [conf & fns]
  (-> conf
      (u/check v/check-repo-in-order)
      (as-> conf (apply u/thread-fns conf fns))
      (u/do-side-effect! mbt-core/git-add-all!)
      (u/do-side-effect! commit-generated!)))

(s/fdef generate-before-bump!
        :args (s/cat :param (s/keys :req [::git/repo
                                          ::project/version
                                          ::versioning/scheme]
                                    :opt [::versioning/tag-base-name
                                          ::versioning/bump-level])
                     :fns (s/* fn?)))


;;----------------------------------------------------------------------------------------------------------------------
;; Building jars
;;----------------------------------------------------------------------------------------------------------------------
(defn check-incompatible-deps
  "Checks wheter or not the jar being built uses deps incompatible with maven (git libs, local deps)."
  [{allowed? ::build.jar/allow-non-maven-deps
    :as conf}]
  (let [non-maven-deps (mbt-core/deps-non-maven conf)]
    (when (and (seq non-maven-deps)
               (not allowed?))
      (throw (ex-info "Can't build a skinny jar while having non maven deps."
                      {::anom/category ::anom/forbidden
                       :mbt/error :invalid-deps
                       :faulty-deps non-maven-deps})))))

(defn jar!
  "Build a skinny jar for the project.
  Depending on the value of `:fr...mbt.alpha.build.jar/allow-non-maven-deps` this function will throw
  an exception if non maven deps are found.

  Also ensures several config keys are present using
  [[fr.jeremyschoffen.mbt.alpha.default.jar/ensure-jar-defaults]].
  "
  [param]
  (-> param
      (u/check check-incompatible-deps)
      default-jar/ensure-jar-defaults
      default-jar/jar!))

(u/spec-op jar!
           :deps [default-jar/ensure-jar-defaults default-jar/jar!]
           :param {:req [::build.jar/path
                         ::build.jar/allow-non-maven-deps
                         ::maven/artefact-name
                         ::maven/group-id
                         ::maven.pom/path
                         ::project/deps
                         ::project/version
                         ::project/working-dir]
                   :opt [::jar/exclude?
                         ::jar/main-ns
                         ::jar.manifest/overrides
                         ::maven/scm
                         ::project/author
                         ::project/licenses
                         ::project.deps/aliases]})


(defn uberjar!
  "Build an uberjar for the project. Ensure that the `:fr...mbt.alpha.project/version` is present int the config with
  [[fr.jeremyschoffen.mbt.alpha.default.versioning/current-project-version]].
  Also ensure other keys using [[fr.jeremyschoffen.mbt.alpha.default.building/ensure-jar-defaults]]."
  [param]
  (-> param
      default-jar/ensure-jar-defaults
      default-jar/uberjar!))

(u/spec-op uberjar!
           :deps [default-jar/ensure-jar-defaults
                  default-jar/uberjar!]
           :param {:req [::build.uberjar/path
                         ::maven/artefact-name
                         ::maven/group-id
                         ::maven.pom/path
                         ::project/deps
                         ::project/version
                         ::project/working-dir]
                   :opt [::jar/exclude?
                         ::jar/main-ns
                         ::jar.manifest/overrides
                         ::maven/scm
                         ::project/author
                         ::project/licenses
                         ::project.deps/aliases]})