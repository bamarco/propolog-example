(ns propolog-example.event
  (:require [taoensso.timbre :as log]
            [datascript.core :as d :refer [pull q]]
            [propolog-example.onyx :as onyx]
            [propolog-example.utils :as utils :refer [ppr-str cat-into]]
            [com.rpl.specter :as specter]
            #?(:cljs [cljs.core.async :refer [<! chan]])
            #?(:cljs [cljs-http.client :as http]) ;; TODO: switch to sente)
            #?(:cljs [cljs.reader :as reader]
               :clj [clojure.edn :as reader])
            #?(:cljs [reagent.core :as r])
            )
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

;; TODO: catch and display simulator errors.

(defmulti intent (fn [_ seg]
;;                    (log/debug seg "Intenting" (:onyx/type seg))
                   (:onyx/type seg)))

(defn dispatch [conn seg]
  (d/transact! conn [[:db.fn/call intent seg]]))

(defn raw-dispatch [conn seg]
  ;; TODO: make a nice error for when you didn't use raw-dispatch! and you should have
  (intent conn seg))

(defn pull-and-transition-env [db sim-id & transitions]
  (let [env (:onyx.sim/env (pull db '[{:onyx.sim/env [*]}] sim-id))]
;;     (log/debug "transition" sim-id transitions)
    [(reduce (fn [env tr]
              (tr env)) env transitions)]))

(defmethod intent
  :reagent/next-tick
  [db _]
  (let [sims (d/q '[:find ?sim ?running ?speed
                    :where
                    [?sim :onyx/type :onyx.sim/sim]
                    [?sim :onyx.sim/running? ?running]
                    [?sim :onyx.sim/speed ?speed]] db)]
    [:onyx/name :main-env] ;; FIXME: magic :main-env. ???: use q.
    (reduce concat
    (for [[id running? speed] sims]
      (when running?
        ;; TODO: speed
        (pull-and-transition-env db id onyx/tick))))))

(defmethod intent
  :onyx.sim.control/env-style
  [db {:keys [:onyx.sim.control/selected]}]
  (let [pretty? (= selected :onyx.sim.control/pretty-env?)
        raw? (= selected :onyx.sim.control/raw-env?)]
    ;; ???: REPORT-BUG: There is a bug where radio buttons pass the value false instead of the selected value. We've hacked our way around it in this function, but this does not agree with the re-com documentation.
    ;; TODO: make a generic radio control handler and data type
;;     (log/debug selected "pretty? " pretty? " raw? " raw?)
    [[:db/add [:onyx/name :onyx.sim.control/raw-env?] :onyx.sim.control/toggled? raw?]
     [:db/add [:onyx/name :onyx.sim.control/pretty-env?] :onyx.sim.control/toggled? pretty?]]))

(defmethod intent
  :onyx.sim.control/toggled?
  [db {:keys [:onyx.sim/control :onyx.sim.control/toggled?]}]
  [[:db/add control :onyx.sim.control/toggled? toggled?]])

(defmethod intent
  :onyx.sim.event/hide-tasks
  [db {:keys [:onyx.sim/sim :onyx.sim/task-names]}]
  [[:db/add sim :onyx.sim/hidden-tasks task-names]])

(defmethod intent
  :onyx.sim.event/hide-task
  [db {:keys [:onyx.sim/sim :onyx.sim/task-name]}]
  (let [hidden-tasks (:onyx.sim/hidden-tasks (d/entity db sim))]
    [[:db/add sim :onyx.sim/hidden-tasks (conj hidden-tasks task-name)]]))

(defmethod intent
  :onyx.sim.event/import-uri
  [db {:keys [:onyx.sim/sim :onyx.sim/task-name uri]}]
  [[:db/add sim :onyx.sim/import-uri uri]])

(defmethod intent
  :onyx.api/new-segment
  [db {:keys [:onyx.sim/sim :onyx.sim/task-name :onyx.sim/segment]}]
  (pull-and-transition-env db sim #(onyx/new-segment % task-name segment)))

(defmethod intent
  :onyx.api/tick
  [db {:keys [:onyx.sim/sim]}]
  (pull-and-transition-env db sim onyx/tick))

(defmethod intent
  :onyx.api/step
  [db {:keys [:onyx.sim/sim]}]
  (pull-and-transition-env db sim onyx/step))

(defmethod intent
  :onyx.api/drain
  [db {:keys [:onyx.sim/sim]}]
  (pull-and-transition-env db sim onyx/drain))

#?(:cljs
(defn run-sim [conn sim]
  (let [running? (:onyx.sim/running? (d/entity @conn sim))]
    (when running?
      ;; FIXME: upgrade performance by doing everything inline
      (dispatch! conn {:onyx/type :reagent/next-tick})
      (r/next-tick #(run-sim conn sim))))))

(defmethod intent
  :onyx.api/start
  [conn {:keys [:onyx.sim/sim]}]
  #?(:cljs
      (do
        (d/transact! conn [[:db/add sim :onyx.sim/running? true]])
        (run-sim conn sim))
      :clj (throw "Cannot start simulator in clojure. Only implemented in cljs.")))

(defmethod intent
  :onyx.api/stop
  [db {:keys [:onyx.sim/sim]}]
  [[:db/add sim :onyx.sim/running? false]])

#?(:cljs
(defmethod intent
  :onyx.sim.event/import-segments
  [conn {:keys [:onyx.sim/sim :onyx.sim/task-name]}]
  (let [uri (:onyx.sim/import-uri (d/entity @conn sim))]
    (go (let [response (<! (http/get uri))]
          (log/info (str "retrieving edn from <" uri ">"))
          (log/debug "edn is...\n" (ppr-str (:body response)))
          (d/transact! conn [(into
                               [:db.fn/call pull-and-transition-env sim]
                               (for [seg (:body response)]
                                 #(onyx/new-segment % task-name seg)))]))))))

