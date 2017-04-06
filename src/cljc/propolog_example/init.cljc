(ns propolog-example.init
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [datascript.core :as d]
            [propolog-example.utils :as utils :refer [cat-into ppr-str]]
            #?(:cljs [posh.reagent :as posh])
            #?(:cljs [cljs.core.async :refer [<! chan]])
            #?(:cljs [cljs-http.client :as http])) ;; TODO: switch to sente)
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

(defonce temp-id (atom -1))

(defn gen-temp-id []
  (swap! temp-id dec)
  @temp-id)

(defonce onyx-batch-size 20)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; from posh chat @seantempesta
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; re-frame interceptors
;;

;; (defn create-datascript-interceptor
;;   "Injects a local datascript db for use in re-frame handlers"
;;   [conn]
;;   (let [conn (:conn conn)]
;;     (re-frame.core/->interceptor
;;       :id :add-ds-db
;;       :before (fn [context]
;;                 (assoc-in context [:coeffects :ds-db] conn)))))

;; ;
;; ;; Module (Component) Definition
;; ;;

;; (defrecord ReFrame [datascript storage]
;;   component/Lifecycle

;;   (start [component]
;;     (info "Starting re-frame")
;;     (let [ds-db (:conn datascript)
;;           component-ds-db-interceptor (create-datascript-interceptor datascript)]

;;       ; Enable Posh on the datascript db (so re-frame subscriptions can respond to datascript changes)
;;       (posh.reagent/posh! ds-db)

;;       ; Register an effect handler to transact on the datascript db
;;       (reg-fx
;;         :ds-transact!
;;         (fn [[conn transaction]]
;;           (debug "re-frame transacting:" transaction)
;;           (d/transact! conn transaction)))

;;       ;; Register a subscription that will return the datascript db (so other subscriptions can access it)
;;       (reg-sub
;;         :ds-db
;;         (fn [_ _]
;;           ds-db))

;;       (-> component
;;           (assoc :component-ds-db-interceptor component-ds-db-interceptor))))

;;   (stop [component]
;;     (let [conn (:conn datascript)]
;;       (info "Stopping re-frame")

;;       ;; undo posh! (TODO: posh is doing more stuff than I'm undoing!)
;;       (d/unlisten! conn :posh-dispenser)
;;       (d/unlisten! conn :posh-listener)

;;       ;; Clear re-frame state
;;       (reset! re-frame.db/app-db nil)
;;       (clear-fx :ds-transact!)
;;       (clear-sub :ds-db)

;;       (-> component
;;           (assoc :component-ds-db-interceptor nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; End @seantempesta
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn gen-catalog []
  [
  ;; Onyx Catalog
  {:db/id (gen-temp-id)
   :onyx/type :input
   :onyx/batch-size onyx-batch-size
   :onyx/name :in}

  {:db/id (gen-temp-id)
   :onyx/type :output
   :onyx/batch-size onyx-batch-size
   :onyx/name :render}

  {:db/id (gen-temp-id)
   :onyx/type :function
   :onyx/name :datoms
   :onyx/batch-size onyx-batch-size
   :onyx/fn :propolog-example.catalog/datoms-task}

  {:db/id (gen-temp-id)
   :onyx/type :function
   :onyx/name :rule1
   :onyx/batch-size onyx-batch-size
   :onyx/fn :propolog-example.catalog/rule1-task
   :onyx/doc "[(rule1? ?s ?sides) [?s :sides ?sides]]"}

  {:db/id (gen-temp-id)
   :onyx/type :function
   :onyx/name :rule2
   :onyx/batch-size onyx-batch-size
   :onyx/fn :propolog-example.catalog/rule2-task
   :onyx/doc "[(rule2? ?s ?shape) [?s :shape ?shape]]"}

  {:db/id (gen-temp-id)
   :onyx/type :function
   :onyx/name :rule3
   :onyx/batch-size onyx-batch-size
   :onyx/fn :propolog-example.catalog/rule3-task
   :onyx/doc "[(rule3? ?s) [?s :sides 4]]"}

  {:db/id (gen-temp-id)
   :onyx/type :function
   :onyx/name :q1
   :onyx/batch-size onyx-batch-size
   :onyx/fn :propolog-example.catalog/q1-task
   :onyx/doc "where [?s :sides 4] [?s :shape ?shape]"}

  {:db/id (gen-temp-id)
   :onyx/type :function
   :onyx/name :q2
   :onyx/batch-size onyx-batch-size
   :onyx/fn :propolog-example.catalog/q2-task
   :onyx/doc "where [?s :shape ?shape]" }]
  )

(defn flow-not-nil [[source sinks]]
  {:flow/from source
   :flow/to sinks
   :flow/predicate :propolog-example.catalog/not-nil?})

(defn gen-datascript-edn []
  (let [catalog (gen-catalog)
        job-id (gen-temp-id)]
    (cat-into
      ;; Example Data:
      [
;;        {:db/id (gen-temp-id)
;;         :propolog-example/type :propolog-example/shape
;;         :propolog-example/shape :triangle
;;         :propolog-example/sides 3}

;;        {:db/id (gen-temp-id)
;;         :propolog-example/type :propolog-example/shape
;;         :propolog-example/shape :square
;;         :propolog-example/sides 4}

;;        {:db/id (gen-temp-id)
;;         :propolog/type :propolog/shape
;;         :propolog-example/shape :rect
;;         :propolog-example/sides 4}

       {:db/id job-id
        :onyx.sim/type :onyx.core/job
        :onyx.core/catalog (map :db/id catalog)
        :onyx.core/workflow [[:in :datoms]
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
                                                       :q2 [:render]})}
       {:db/id (gen-temp-id)
        :onyx.sim/title "Propolog Basic Example"
        :onyx.sim/description "Some shapes."
        :onyx.sim/type :onyx.sim/sim
        :onyx/name :main-env
        :onyx.sim/import-uri "example.edn"
        :onyx.sim/speed 1.0
        :onyx.sim/running false
        :onyx.sim/hidden-tasks
        ;;#{:in :datoms :rule1 :rule2 :rule3 :q2 :q1}
        #{}
        :onyx.core/job job-id
        }
       ]
      catalog)))

(defn gen-render-catalog []
  [
  ;; Onyx Catalog
  {:db/id (gen-temp-id)
   :onyx/type :input
   :onyx/batch-size onyx-batch-size
   :onyx/name :in}

  {:db/id (gen-temp-id)
   :onyx/type :output
   :onyx/batch-size onyx-batch-size
   :onyx/name :render}

  {:db/id (gen-temp-id)
   :onyx/type :function
   :onyx/name :match
   :onyx/batch-size onyx-batch-size
   :onyx/fn :propolog-example.svg/render-match}
    ])

(defn gen-render-edn []
  (let [catalog (gen-render-catalog)
        job-id (gen-temp-id)]
    (cat-into
      ;; Example Data:
      [
       {:db/id job-id
        :onyx.sim/type :onyx.core/job
        :onyx.core/catalog (map :db/id catalog)
        :onyx.core/workflow [[:in :match] [:match :render]]
        :onyx.core/lifecycles []
        :onyx.core/flow-conditions (mapv flow-not-nil {:in [:match]
                                                       :match [:render]})}
       {:db/id (gen-temp-id)
        :onyx.sim/title "Render Network"
        :onyx.sim/description "Right now it has one task which is a giant match statement. We can break this match task up into tasks. For dat.view I think we'll end up using selectors (either spector or kiio) to determine what containers things render into, but maybe not."
        :onyx.sim/type :onyx.sim/sim
        :onyx/name :render-env
        :onyx.sim/import-uri "example.edn"
        :onyx.sim/speed 1.0
        :onyx.sim/running false
        :onyx.sim/hidden-tasks #{}
        :onyx.core/job job-id
        }
       ]
      catalog)))


(defonce schema {:onyx.core/catalog {:db/type :db.type/ref
                                     :db/cardinality :db.cardinality/many}
                 :onyx/name {:db/unique :db.unique/identity}
                 :onyx.core/job {:db/type :db.type/ref}
                 :onyx.sim/env {:db/type :db.type/ref}})

(defn init []
  (let [conn (d/create-conn schema)]
    #?(:cljs (posh/posh! conn))
    (d/transact! conn (cat-into [] (gen-datascript-edn) (gen-render-edn)))
    (rf/reg-sub :propolog-example.sub/conn (fn [_ _] conn))
    (re-frame.core/reg-fx
      :propolog-example.event/datascript
      (fn [{tx :propolog-example.event/tx}]
        (d/transact! conn tx)))
    #?(:cljs
        (re-frame.core/reg-fx
          :propolog-example.event/datascript-async
          (fn [{txf :propolog-example.event/txf
                uri-fn :propolog-example.event/uri-fn
                event :propolog-example.event/event}]
            (go (let [uri (uri-fn @conn event)
                      response (<! (http/get uri))]
                  (log/info "retrieving edn from <" uri ">")
                  (log/debug "edn is...\n" (ppr-str (:body response)))
                  (d/transact! conn [[:db.fn/call txf (:body response)]]))))))
    (rf/dispatch [:onyx.api/init [:onyx/name :main-env]])
    (rf/dispatch [:onyx.api/init [:onyx/name :render-env]])
    (rf/dispatch [:onyx.sim/import-segments [:onyx/name :main-env] :in])))
