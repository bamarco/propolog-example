(ns propolog-example.event
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [datascript.core :as d]
            [propolog-example.onyx :as onyx]
            [propolog-example.utils :as utils]))

(defonce onyx-batch-size 20)

(rf/reg-event-fx
  ::onyx-tick
  (fn [{:keys [db]} _]
    (log/debug "env: " (onyx/env-summary (:env db)))
    {:db (update-in db [:env] onyx/tick)}
    ))

(rf/reg-event-fx
  ::onyx-step
  (fn [{:keys [db]} _]
;;     (log/debug "env: " (onyx/env-summary (:env db)))
    {:db (update-in db [:env] onyx/step)}
    ))


(rf/reg-event-fx
  ::onyx-drain
  (fn [{:keys [db]}  _]
;;     (log/debug "env: " (onyx/env-summary (:env db)))
    {:db (update-in db [:env] onyx/drain)}
    ))

(rf/reg-event-fx
  ::init
  (fn [_ _]
    (let [job {:catalog [{:onyx/type :input
                          :onyx/batch-size onyx-batch-size
                          :onyx/name :in}

                         {:onyx/type :output
                          :onyx/batch-size onyx-batch-size
                          :onyx/name :render}

                         {:onyx/type :function
                          :onyx/name :datoms
                          :onyx/batch-size onyx-batch-size
                          :onyx/fn :propolog-example.catalog/datoms-task}

                         {:onyx/type :function
                          :onyx/name :rule1
                          :onyx/batch-size onyx-batch-size
                          :onyx/fn :propolog-example.catalog/rule1-task
                          :onyx/doc "[(rule1? ?s ?sides) [?s :sides ?sides]]"}

                         {:onyx/type :function
                          :onyx/name :rule2
                          :onyx/batch-size onyx-batch-size
                          :onyx/fn :propolog-example.catalog/rule2-task
                          :onyx/doc "[(rule2? ?s ?shape) [?s :shape ?shape]]"}

                         {:onyx/type :function
                          :onyx/name :rule3
                          :onyx/batch-size onyx-batch-size
                          :onyx/fn :propolog-example.catalog/rule3-task
                          :onyx/doc "[(rule3? ?s) [?s :sides 4]]"}

                         {:onyx/type :function
                          :onyx/name :q1
                          :onyx/batch-size onyx-batch-size
                          :onyx/fn :propolog-example.catalog/q1-task
                          :onyx/doc "where [?s :sides 4] [?s :shape ?shape]"}

                         {:onyx/type :function
                          :onyx/name :q2
                          :onyx/batch-size onyx-batch-size
                          :onyx/fn :propolog-example.catalog/q2-task
                          :onyx/doc "where [?s :shape ?shape]" }]
               :workflow [[:in :datoms]
                          [:datoms :rule1]
                          [:datoms :rule2] [:rule2 :rule3]
                          [:rule3 :q1]
                          [:rule2 :q2]
                          [:q1 :render]
                          [:q2 :render]]
               :lifecycles []
               :flow-conditions []}]
      {:db {;;:job job
            :env (-> (onyx/init job)
                     (onyx/new-segment :in {:transactions #{[42 :shape :triangle]
                                                            [42 :sides 3]
                                                            [43 :shape :square]
                                                            [43 :sides 3]
                                                            [44 :shape :rect]
                                                            [44 :sides 3]}}))}})))

