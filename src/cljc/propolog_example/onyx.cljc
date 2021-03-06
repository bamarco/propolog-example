(ns propolog-example.onyx
  (:require [onyx-local-rt.api :as onyx]
            [propolog-example.utils #?@(:clj (:refer [xfn])
                                        :cljs (:refer-macros [xfn]))]))


(defonce onyx-batch-size 20)

(def env-summary onyx/env-summary)
(def init        onyx/init)
(def tick        onyx/tick)
(def drain       onyx/drain)
(def new-segment onyx/new-segment)

(defn out
  "Returns outputs of onyx job presumably after draining."
  [env]
  (into
    {}
    (comp
      (filter (fn [[_ task]] (= (get-in task [:event :onyx.core/task-map :onyx/type]) :output)))
      (xfn [[task-name task]]
           [task-name (:outputs task)]))
    (:tasks env)))

(defn run
  "Drains and stops an onyx environment with the given segments fed in."
  [env in]
  (-> (reduce (fn [env [task segment]]
                 (onyx/new-segment env task segment)) env in)
      (onyx/drain)
      (onyx/stop)))

(defn step
  "Ticks until the start of the next task."
  [env]
  (reduce
    (fn [env _]
      (if (= (:next-action env) :lifecycle/after-batch)
        (reduced (onyx/tick env))
        (onyx/tick env)))
    (onyx/tick env)
    (range 1000)))

(defn remove-segment [env output-task segment]
  (onyx/transition-env env {:event :remove-segment
                            :task output-task
                            :segment segment}))

(defmethod onyx/transition-env :remove-segment
  [env {:keys [task segment]}]
  (update-in env [:tasks task :outputs] (partial into [] (remove #(= segment %)))))

(defn onyx-feed-loop [env & selections]
  ;; TODO: :in and :render need to be genericized
  (reduce
    (fn [env selection]
      (-> (onyx/new-segment env :in selection)
          (remove-segment :render selection)))
    env
    selections))



