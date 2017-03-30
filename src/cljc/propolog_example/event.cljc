(ns propolog-example.event
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [datascript.core :as d]
            [propolog-example.onyx :as onyx]
            [propolog-example.utils :as utils])
  #?(:cljs (:require-macros [propolog-example.event :refer [reg-event-ds]]))
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
         (fn [_# [_# & event#]]
           ;; NOTE: ::datascript fx is registered in init.cljc
           {::datascript {::tx [(into [:db.fn/call ~f] event#)]}})
           )))

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
  (fn [db env-id]
    (let [job (-> (d/pull db '[{:onyx.core/job [{:onyx.core/catalog [*]} *]}] env-id)
                  :onyx.core/job
                  ds->onyx)
          ]
      (log/debug "jobob" job)
      [[:db/add env-id :onyx.core/env (onyx/init job)]]
       )))

(reg-event-ds
  :onyx.api/new-segment
  (fn [db env-id task segment]
    (let [env (-> (d/entity db env-id)
                  :onyx.core/env
                  ds->onyx)]
      (log/debug "seg" segment)
      [[:db/add env-id :onyx.core/env (onyx/new-segment env task segment)]]
    )))


(reg-event-ds
  :onyx.api/tick
  (fn [db env-id]
    (let [env (-> (d/entity db env-id)
                  :onyx.core/env
                  ds->onyx)]
      [[:db/add env-id :onyx.core/env (onyx/tick env)]]
    )))

(reg-event-ds
  :onyx.api/step
  (fn [db env-id]
    (let [env (-> (d/entity db env-id)
                  :onyx.core/env
                  ds->onyx)]
      [[:db/add env-id :onyx.core/env (onyx/step env)]])))


(reg-event-ds
  :onyx.api/drain
  (fn [db env-id]
    (let [env (-> (d/entity db env-id)
                  :onyx.core/env
                  ds->onyx)]
      [[:db/add env-id :onyx.core/env (onyx/drain env)]])))

