(ns propolog-example.event
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [datascript.core :as d]
            [propolog-example.onyx :as onyx]
            [propolog-example.utils :as utils :refer [cat-into]]
            #?(:cljs [cljs.reader :as reader]
               :clj [clojure.edn :as reader])
            )
  #?(:cljs (:require-macros [propolog-example.event :refer [reg-event-ds reg-event-ds-async]]))
           )

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

(defn ds->onyx [datascript-map]
  (-> datascript-map
;;       (dissoc :db/id)
;;       (dissoc :propolog/type)
      (clojure.set/rename-keys {:onyx.core/catalog :catalog
                                :onyx.core/workflow :workflow
                                :onyx.core/lifecycles :lifecycles
                                :onyx.core/flow-conditions :flow-conditions})))

(reg-event-ds
  :onyx.api/init
  (fn [db [_ env-id]]
    (let [job (-> (d/pull db '[{:onyx.core/job [{:onyx.core/catalog [*]} *]}] env-id)
                  :onyx.core/job
                  ds->onyx)
          ]
;;       (log/debug "jobob" job)
      [[:db/add env-id :onyx.sim/env (onyx/init job)]]
       )))

(reg-event-ds
  :onyx.api/new-segment
  (fn [db [_ env-id task segment]]
    (let [env (-> (d/entity db env-id)
                  :onyx.sim/env)]
;;       (log/debug "seg" segment)
      [[:db/add env-id :onyx.sim/env (onyx/new-segment env task segment)]]
    )))


(reg-event-ds
  :onyx.api/tick
  (fn [db [_ env-id]]
    (let [env (-> (d/entity db env-id)
                  :onyx.sim/env)]
      [[:db/add env-id :onyx.sim/env (onyx/tick env)]]
    )))

(reg-event-ds
  :onyx.api/step
  (fn [db [_ env-id]]
    (let [env (-> (d/entity db env-id)
                  :onyx.sim/env)]
      [[:db/add env-id :onyx.sim/env (onyx/step env)]])))

(reg-event-ds
  :onyx.api/drain
  (fn [db [_ env-id]]
    (let [env (-> (d/entity db env-id)
                  :onyx.sim/env
                  ds->onyx)]
      [[:db/add env-id :onyx.sim/env (onyx/drain env)]])))


(reg-event-ds
  :onyx.sim/hide-task
  (fn [db [_ env-id task-name]]
    (let [hidden (-> (d/entity db env-id)
                     :onyx.sim/hide-tasks)]
    [[:db/add env-id :onyx.sim/hide-tasks (conj hidden task-name)]])))

(reg-event-ds
  :onyx.sim/hide-tasks
  (fn [db [_ env-id tasks]]
    [[:db/add env-id :onyx.sim/hide-tasks tasks]]))

(reg-event-ds
  :onyx.sim/import-uri
  (fn [db [_ env-id task-name uri]]
    (let [;;task-id (-> (d/entity db env-id))
          ]
    [[:db/add env-id :onyx.sym/import-uri uri]]
    )))

(reg-event-ds-async
  :onyx.sim/import-segments
  (fn [db [_ env-id task-name]]
    (let [uri (:onyx.sim/import-uri (d/pull db '[:onyx.sim/import-uri] env-id))]
      uri))
  (fn [db [_ env-id task-name] [& segments]]
    (let [env (-> (d/entity db env-id)
                  :onyx.sim/env)]
      (log/debug "event-post" (-> (reduce #(onyx/new-segment %1 task-name %2) env segments)
                                 :tasks
                                 task-name
                                 :inbox))
    [[:db/add env-id :onyx.sim/env (reduce #(onyx/new-segment %1 task-name %2) env segments)]])))

