(ns ^{:author "Jeremy Schoffen"
      :doc "
Utilities used in the whole project.
      "}
  fr.jeremyschoffen.mbt.alpha.utils
  (:require
    [clojure.spec.alpha :as s]
    [medley.core :as medley]
    [fr.jeremyschoffen.dolly.core :as dolly]
    [fr.jeremyschoffen.java.nio.alpha.file :as fs]
    [fr.jeremyschoffen.mapiform.alpha.core :as mc]
    [fr.jeremyschoffen.mapiform.alpha.clj.spec :as ms]
    [fr.jeremyschoffen.mapiform.alpha.specs.db :as spec-db]
    [fr.jeremyschoffen.mbt.alpha.utils.threading-utils :as tu]))


;;----------------------------------------------------------------------------------------------------------------------
;; Some fs utils
;;----------------------------------------------------------------------------------------------------------------------
(defn safer-path
  ([]
   (safer-path "."))
  ([& args]
   (->> args
        (apply fs/path)
        fs/canonical-path)))


(defn ensure-dir! [d]
  (when (fs/not-exists? d)
    (fs/create-directories! d))
  d)


(defn ensure-parent! [f]
  (some-> f fs/parent ensure-dir!)
  f)

;;----------------------------------------------------------------------------------------------------------------------
;; Additional map utils
;;----------------------------------------------------------------------------------------------------------------------
(dolly/def-clone assoc-computed mc/assoc-computed)
(dolly/def-clone ensure-computed mc/ensure-computed)
(dolly/def-clone augment-computed mc/augment-computed)


(defn strip-keys-nss [m]
  (medley/map-keys #(-> % name keyword) m))
;;----------------------------------------------------------------------------------------------------------------------
;; Threading context utils
;;----------------------------------------------------------------------------------------------------------------------

(dolly/def-clone side-effect! mc/side-effect!)
(dolly/def-clone wrapped-side-effect! mc/wrapped-side-effect!)
(dolly/def-clone check side-effect!)
(dolly/def-clone thread-fns mc/thread-fns)

(dolly/def-clone branch-named! tu/branch-named!)
(dolly/def-clone branch! tu/branch!)

(dolly/def-clone do-side-effect-named! tu/do-side-effect-named!)
(dolly/def-clone do-side-effect! tu/do-side-effect!)

(dolly/def-clone record-build tu/record)
(dolly/def-clone mark-dry-run tu/make-dry-run)

;;----------------------------------------------------------------------------------------------------------------------
;; Specs utils
;;----------------------------------------------------------------------------------------------------------------------
(defmacro simple-fdef
  "Macro to spec 1-arg functions. "
  ([n param-spec]
   (list `simple-fdef n param-spec nil))
  ([n param-spec ret]
   (let [spec (cond-> [:args (list `s/cat :param param-spec)]
                      ret (conj :ret ret))]
     `(s/fdef ~n ~@spec))))


(dolly/def-clone spec-op ms/spec-op)
(dolly/def-clone spec ms/spec)
(dolly/def-clone param-suggestions ms/param-suggestions)
(dolly/def-clone param-users ms/param-users)


;;----------------------------------------------------------------------------------------------------------------------
;; Enhanced cloning
;;----------------------------------------------------------------------------------------------------------------------

;; taken from clojure.spec.alpha/ns-qualify
(defn- ns-qualify
  "Qualify symbol s by resolving it or using the current *ns*."
  [s]
  (if-let [ns-sym (some-> s namespace symbol)]
    (or (some-> (get (ns-aliases *ns*) ns-sym)
                str
                (symbol (name s)))
        s)
    (symbol (str (.name *ns*)) (str s))))


(defmacro def-clone [new-name cloned]
  (let [{:keys [type cloned-sym]} (dolly/cloned-info cloned)]
    `(do
       (dolly/def-clone ~new-name ~cloned-sym)
       ~@(when (= type :function)
           `[(s/def ~new-name (clojure.spec.alpha/get-spec '~cloned-sym))
             (spec-db/add-spec! '~(ns-qualify new-name) (spec ~cloned-sym))])
       (var ~new-name))))
