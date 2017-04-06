(ns propolog-example.event
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [datascript.core :as d]
            [propolog-example.onyx :as onyx]
            [propolog-example.utils :as utils :refer [ppr-str cat-into]]
            [com.rpl.specter :as specter]

            #?(:cljs [cljs.reader :as reader]
               :clj [clojure.edn :as reader])
            #?(:cljs [reagent.core :as r])
            )
  #?(:cljs (:require-macros [propolog-example.event :refer [reg-event-ds reg-event-ds-async]])))

;; (def map-event
;;   (re-frame.core/->interceptor
;;     :id      ::map-event
;;     :before  (fn [context]
;;                (let [as-map-fn #(let [_ event] event)]
;;                  (update-in context [:coeffects :event] as-map-fn)
;;                  ))))


;; ???: Can we do this as an interceptor?
;; ???: Can we work off of the signal graph if we are doing crdt instead of using a transactor?
#?(:clj
    (defmacro reg-event-ds [k f]
      `(re-frame.core/reg-event-fx
         ~k
         (fn [_# event#]
           ;; NOTE: ::datascript fx is registered in init.cljc
           {::datascript {::tx [[:db.fn/call ~f event#]]}}))))

#?(:clj
    (defmacro reg-event-ds-async [k uri-fn txf]
      `(re-frame.core/reg-event-fx
         ~k
         (fn [_# event#]
           ;; NOTE: ::datascript fx is registered in init.cljc
           {::datascript-async {::uri-fn ~uri-fn
                                ::event event#
                                ::txf (fn [db# resp#] (~txf db# event# resp#))}}))))

(defn re-trigger-timer []
  #?(:cljs (r/next-tick (fn [] (rf/dispatch [:reagent/next-tick])))
     :clj (throw "Timer not implemented for plain :clj")))

(defn pull-and-transition-env [db sim-id & transitions]
  (let [env (:onyx.sim/env (d/pull db '[{:onyx.sim/env [*]}] sim-id))]
    (reduce (fn [env tr]
              (tr env)) env transitions)))

(reg-event-ds
  :reagent/next-tick
  (fn [db _]
    (let [sim-id [:onyx/name :main-env] ;; FIXME: magic :main-env
          {running :onyx.sim/running speed :onyx.sim/speed}
           (d/pull db [:onyx.sim/running :onyx.sim/speed] sim-id)]
      ;; TODO: speed
      (when running
        (re-trigger-timer)
        [(pull-and-transition-env db sim-id onyx/tick)]))))

(defn ds->onyx [datascript-map]
  (-> datascript-map
;;       (dissoc :db/id)
;;       (dissoc :onyx.sim/type)
      (clojure.set/rename-keys {:onyx.core/catalog :catalog
                                :onyx.core/workflow :workflow
                                :onyx.core/lifecycles :lifecycles
                                :onyx.core/flow-conditions :flow-conditions})))

(reg-event-ds
  :onyx.api/init
  (fn [db [_ sim-id]]
    (let [job (-> (d/pull db '[{:onyx.core/job [{:onyx.core/catalog [*]} *]}] sim-id)
                  :onyx.core/job
                  ds->onyx)]
      [(assoc (onyx/init job) :db/id -1)
       [:db/add sim-id :onyx.sim/env -1]])))

(reg-event-ds
  :onyx.api/new-segment
  (fn [db [_ sim-id task segment]]
    (log/debug "dispatching :onyx.api/new-segment")
;;     (let [env (:onyx.sim/env (d/pull db '[{:onyx.sim/env [*]}] sim-id))]
;;       (log/debug ":onyx.api/new-segment" segment)
;;       [;;[:db/add sim-id :onyx.sim/env

;;         (onyx/new-segment env task segment)
;;         ]
    ));;)

(reg-event-ds
  :onyx.api/tick
  (fn [db [_ sim-id]]
    [(pull-and-transition-env db sim-id onyx/tick)]))

(reg-event-ds
  :onyx.api/step
  (fn [db [_ sim-id]]
    [(pull-and-transition-env db sim-id onyx/step)]))

(reg-event-ds
  :onyx.api/drain
  (fn [db [_ sim-id]]
    [(pull-and-transition-env db sim-id onyx/drain)]))

(reg-event-ds
  :onyx.api/start
  (fn [db [_ sim-id]]
      (re-trigger-timer)
    [[:db/add sim-id :onyx.sim/running true]]))

(reg-event-ds
  :onyx.api/stop
  (fn [db [_ sim-id]]
    [[:db/add sim-id :onyx.sim/running false]]))

(reg-event-ds
  :onyx.sim/hide-task
  (fn [db [_ sim-id task-name]]
    (log/debug "hiding" task-name)
    (let [hidden (get (d/entity db sim-id) :onyx.sim/hidden-tasks)]
      [[:db/add sim-id :onyx.sim/hidden-tasks (conj hidden task-name)]])))

(reg-event-ds
  :onyx.sim/hide-tasks
  (fn [db [_ sim-id tasks]]
    [[:db/add sim-id :onyx.sim/hidden-tasks tasks]]))

(reg-event-ds
  :onyx.sim/import-uri
  (fn [db [_ sim-id task-name uri]]
    (let [;;task-id (-> (d/entity db sim-id))
          ]
    [[:db/add sim-id :onyx.sym/import-uri uri]]
    )))

(reg-event-ds-async
  :onyx.sim/import-segments
  (fn [db [_ sim-id task-name]]
    (log/debug "dispatching-uri :onyx.sim/import-segments")
    (let [uri (:onyx.sim/import-uri (d/pull db '[:onyx.sim/import-uri] sim-id))]
      uri))
  (fn [db [_ sim-id task-name] [& segments]]
    (log/debug "dispatching :onyx.sim/import-segments")
    [(apply
       pull-and-transition-env
       db
       sim-id
       (for [seg segments]
         (fn [env]
           (onyx/new-segment
             env task-name seg))))]))

