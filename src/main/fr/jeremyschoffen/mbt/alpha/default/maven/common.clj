(ns ^{:author "Jeremy Schoffen"
      :doc "
Common maven utilities used in the default apis.
      "}
  fr.jeremyschoffen.mbt.alpha.default.maven.common
  (:require
    [fr.jeremyschoffen.java.nio.alpha.file :as fs]

    [fr.jeremyschoffen.mbt.alpha.core :as mbt-core]
    [fr.jeremyschoffen.mbt.alpha.default.specs]
    [fr.jeremyschoffen.mbt.alpha.utils :as u]))


(u/pseudo-nss
  build.jar
  gpg
  jar
  maven.deploy
  maven.deploy.artefact
  maven.pom
  project)


(defn make-usual-artefacts
  "Makes a sequence of maps representing maven artefacts following the `:maven.deploy/artefact` spec.

  Here representations for a pom.xml and a jar are made."
  [{pom-path ::maven.pom/path
    jar-path ::build.jar/path}]
  [{::maven.deploy.artefact/path      pom-path
    ::maven.deploy.artefact/extension "pom"}

   {::maven.deploy.artefact/path jar-path
    ::maven.deploy.artefact/extension "jar"}])

(u/spec-op make-usual-artefacts
           :param {:req [::maven.pom/path
                         ::build.jar/path]}
           :ret ::maven.deploy/artefacts)


(defn make-usual-artefacts+signatures!
  "Make the usual artefacts using [[fr.jeremyschoffen.mbt.alpha.default.maven.common/make-usual-artefacts]] and
  their gpg signatures."
  [ctxt]
  (let [artefacts (make-usual-artefacts ctxt)
        signatures (mbt-core/maven-sign-artefacts!
                     (assoc ctxt ::maven.deploy/artefacts artefacts))]
    (into artefacts signatures)))

(u/spec-op make-usual-artefacts+signatures!
           :deps [make-usual-artefacts mbt-core/maven-sign-artefacts!]
           :param {:req [::build.jar/path
                         ::maven.pom/path]
                   :opt [::gpg/command
                         ::gpg/home-dir
                         ::gpg/key-id
                         ::gpg/pass-phrase
                         ::gpg/version
                         ::project/working-dir]}
           :ret ::maven.deploy/artefacts)
