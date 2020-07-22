(ns com.jeremyschoffen.mbt.alpha.default.maven.common
  (:require
    [com.jeremyschoffen.java.nio.alpha.file :as fs]
    [com.jeremyschoffen.mbt.alpha.core :as mbt-core]
    [com.jeremyschoffen.mbt.alpha.utils :as u]))


(defn make-usual-artefacts
  "Makes a sequence of maps representing maven artefacts following the `:maven.deploy/artefact` spec.

  Here representations for a pom.xml and a jar are made."
  [{pom-dir :maven.pom/dir
    jar-path :jar/output}]
  [{:maven.deploy.artefact/path (fs/path pom-dir "pom.xml")
    :maven.deploy.artefact/extension "pom"}

   {:maven.deploy.artefact/path jar-path
    :maven.deploy.artefact/extension "jar"}])

(u/spec-op make-usual-artefacts
           :param {:req [:maven.pom/dir
                         :jar/output]}
           :ret :maven.deploy/artefacts)


(defn make-usual-artefacts+signatures!
  "Makes a sequence of maps representing maven artefacts `:maven.deploy/artefact` spec.

  Here representations for a pom.xml, a jar and their signatures (using gnupg) are returned."
  [ctxt]
  (let [artefacts (make-usual-artefacts ctxt)
        signatures (mbt-core/maven-sign-artefacts!
                     (assoc ctxt :maven.deploy/artefacts artefacts))]
    (into artefacts signatures)))

(u/spec-op make-usual-artefacts+signatures!
           :deps [make-usual-artefacts mbt-core/maven-sign-artefacts!]
           :param {:req [:jar/output :maven.pom/dir]
                   :opt [:gpg/command
                         :gpg/home-dir
                         :gpg/key-id
                         :gpg/pass-phrase
                         :gpg/version
                         :project/working-dir]}
           :ret :maven.deploy/artefacts)
