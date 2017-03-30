(ns propolog-example.init
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [datascript.core :as d]
            [propolog-example.utils :as utils :refer [cat-into]]
            #?(:cljs [posh.reagent :as posh])
            #?(:cljs [cljs.core.async :refer [<! chan]])
            #?(:cljs [cljs-http.client :as http]) ;; TODO: switch to sente
            ))

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

(defn gen-datascript-edn []
  (let [catalog (gen-catalog)
        job-id (gen-temp-id)]
    (cat-into
      ;; Example Data:
      [{:db/id (gen-temp-id)
        :propolog-example/type :propolog-example/shape
        :propolog-example/shape :triangle
        :propolog-example/sides 3}

       {:db/id (gen-temp-id)
        :propolog-example/type :propolog-example/shape
        :propolog-example/shape :square
        :propolog-example/sides 4}

       {:db/id (gen-temp-id)
        :propolog/type :propolog/shape
        :propolog-example/shape :rect
        :propolog-example/sides 4}

       {:db/id job-id
        :propolog/type :onyx.core/job
        :onyx.core/catalog (map :db/id catalog)
        :onyx.core/workflow [[:in :datoms]
                             [:datoms :rule1] [:rule1 :rule3]
                             [:datoms :rule2]
                             [:rule3 :q1]
                             [:rule2 :q2]
                             [:q1 :render]
                             [:q2 :render]]
        :onyx.core/lifecycles []
        :onyx.core/flow-conditions []
        }
       {:db/id (gen-temp-id)
        :propolog/title "Propolog Basic Example"
        :propolog/description "Some shapes."
        :propolog/type :propolog/env
        :propolog/name :main-env
        :onyx.sim/import-uri "example.edn"
        :onyx.sim/speed 1.0
        :onyx.sim/running false
        :onyx.sim/hide-tasks #{}
        :onyx.core/job job-id
        }
       ]
      catalog)))

(defonce schema {:onyx.core/catalog {:db/cardinality :db.cardinality/many
                                     :db/type :db.type/ref}
                 :propolog/name {:db/unique :db.unique/identity}
                 :onyx.core/job {:db/type :db.type/ref}})

(defn init []
  (let [conn (d/create-conn schema)]
    #?(:cljs (posh/posh! conn))
    (d/transact! conn (gen-datascript-edn))
;;     #?(:cljs
;;         (go (let [response (<! (http/get "propolog-example.edn"))]
;;               ;; FIXME: Transacting directly in an event handler is bad. Change to the other kind of fx handler.
;;               ;; ???: go block probably also bad.
;;               (d/transact! conn (:body response))))
;;         :clj (first (utils/edn-read-file "resources/public/propolog-example.edn")))
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
                  (log/info "retrieving edn from" uri)
                  (log/debug "transacting..." (txf @conn (:body response)))
                  (d/transact! conn [[:db.fn/call txf (:body response)]])
                  (log/debug "post" (-> (d/entity @conn [:propolog/name :main-env])
                                        :onyx.sim/env
                                        :tasks
                                        :in
                                        :inbox
                                        )))))))
    (rf/dispatch [:onyx.api/init [:propolog/name :main-env]])
;;     (rf/dispatch [:onyx.api/new-segment [:propolog/name :main-env]
;;                   :in {:transactions #{[42 :shape :triangle]
;;                                        [42 :sides 3]
;;                                        [43 :shape :square]
;;                                        [43 :sides 4]
;;                                        [44 :shape :rect]
;;                                        [44 :sides 4]}}])
    ))
