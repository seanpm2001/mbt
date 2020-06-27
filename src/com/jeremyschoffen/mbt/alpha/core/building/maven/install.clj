(ns com.jeremyschoffen.mbt.alpha.core.building.maven.install
  (:require
    [clojure.tools.deps.alpha.util.maven :as maven]
    [com.jeremyschoffen.mbt.alpha.core.building.maven.common :as common]
    [com.jeremyschoffen.mbt.alpha.core.specs]
    [com.jeremyschoffen.mbt.alpha.utils :as u])
  (:import
    [org.eclipse.aether.installation InstallRequest]))

(defn make-install-request [param]
  (let [request (InstallRequest.)
        artefacts (common/make-maven-artefacts param)]
    (doseq [a artefacts]
      (.addArtifact request a))
    request))

(u/spec-op make-install-request
           :deps [common/make-maven-artefacts]
           :param {:req [:maven/artefact-name
                         :maven/group-id
                         :project/version
                         :maven.deploy/artefacts]
                   :opt [:maven/classifier]})


(defn install! [{install-dir :maven.install/dir
                 :or         {install-dir maven/default-local-repo}
                 :as         param}]
  (let [system (maven/make-system)
        session (maven/make-session system (str install-dir))
        install-request (make-install-request param)]
    (.install system session install-request)))

(u/spec-op install!
           :deps [make-install-request]
           :param {:req [:maven/artefact-name
                         :maven/group-id
                         :project/version
                         :maven.deploy/artefacts]
                   :opt [:maven/classifier
                         :maven.install/dir]})