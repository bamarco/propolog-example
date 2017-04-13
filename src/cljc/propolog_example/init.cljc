(ns propolog-example.init
  (:require [taoensso.timbre :as log]
            [datascript.core :as d]
            [propolog-example.sim :as sim]
            [propolog-example.utils :as utils :refer [cat-into ppr-str]]
            [propolog-example.event :as event]

            ;; LOAD externs
            [propolog-example.catalog]
            [propolog-example.svg]

            #?(:cljs [posh.reagent :as posh])
            #?(:cljs [cljs.core.async :refer [<! chan]])
            #?(:cljs [cljs-http.client :as http])) ;; TODO: switch to sente)
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

(def onyx-batch-size 20)

(def catalog
  [{:onyx/type :input
    :onyx/batch-size onyx-batch-size
    :onyx/name :in}

   {:onyx/type :input
    :onyx/batch-size onyx-batch-size
    :onyx/name :onyx.sim/event}

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
    :onyx/doc "where [?s :shape ?shape]"}

   {:onyx/type :function
    :onyx/name :match
    :onyx/batch-size onyx-batch-size
    :onyx/fn :propolog-example.svg/render-match2}])


(defn flow-not-nil [[source sinks]]
  {:flow/from source
   :flow/to sinks
   :flow/predicate :propolog-example.catalog/not-nil?})

(def main-sim
  {:onyx/name :main-env
   :onyx.sim/title "Propolog Basic Example"
   :onyx.sim/description "Some shapes."
   :onyx.sim/import-uri "example.edn"
   :onyx.core/job
   {:onyx/type :onyx.core/job
    :onyx.core/catalog catalog
    :onyx.core/workflow [;;[:onyx.sim/event :render]
                         [:in :datoms]
                         [:datoms :rule1] [:rule1 :rule3]
                         [:datoms :rule2]
                         [:rule3 :q1]
                         [:rule2 :q2]
                         [:q1 :render]
                         [:q2 :render]]
    :onyx.core/lifecycles []
    :onyx.core/flow-conditions (mapv flow-not-nil {:in [:datoms]
                                                   :datoms [:rule1 :rule2]
                                                   :rule1 [:rule3]
                                                   :rule2 [:q2]
                                                   :rule3 [:q1]
                                                   :q1 [:render]
                                                   :q2 [:render]})}})

(def render-sim
  {:onyx/name :render-env
   :onyx.sim/title "Render Network"
   :onyx.sim/description "Right now it has one task which is a giant match statement. We can break this match task up into tasks. For dat.view I think we'll end up using selectors (either spector or kiio) to determine what containers things render into, but maybe not."
   :onyx.sim/import-uri "example.edn"
   :onyx.core/job
   {:onyx/type :onyx.core/job
    :onyx.core/catalog catalog
    :onyx.core/workflow [[:in :datoms]
                         [:datoms :rule1] [:rule1 :rule3]
                         [:datoms :rule2]
                         [:rule3 :q1]
                         [:rule2 :q2]
                         [:q1 :match]
                         [:q2 :match]
                         [:match :render]]
    :onyx.core/lifecycles []
    :onyx.core/flow-conditions (mapv flow-not-nil {:in [:datoms]
                                                   :datoms [:rule1 :rule2]
                                                   :rule1 [:rule3]
                                                   :rule2 [:q2]
                                                   :rule3 [:q1]
                                                   :q1 [:match]
                                                   :q2 [:match]
                                                   :match [:render]})}})

(defn create-conn []
  (let [conn (d/create-conn sim/schema)]
    #?(:cljs (posh/posh! conn))
    ;; ???: temp-id's in two db.fn/call conflict each other. Is this a bug or a feature? We either need a warning or the bug fixed in datascript.
;;     (d/transact! conn [[:db.fn/call sim/db-create-ui]])
    (d/transact! conn [[:db.fn/call sim/db-create-sim main-sim]])
    (d/transact! conn [[:db.fn/call sim/db-create-sim render-sim]])
    #?(:cljs
        (event/raw-dispatch! conn {:onyx/type :onyx.sim.event/import-segments
                                   :onyx.sim/task-name :in
                                   :onyx.sim/sim [:onyx/name :main-env]}))
    #?(:cljs
        (event/raw-dispatch! conn {:onyx/type :onyx.sim.event/import-segments
                                   :onyx.sim/task-name :in
                                   :onyx.sim/sim [:onyx/name :render-env]}))
    conn))
