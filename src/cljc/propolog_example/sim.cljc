(ns propolog-example.sim
  (:require [taoensso.timbre :as log]
            [propolog-example.onyx :as onyx]
            [propolog-example.flui :as flui]
            [propolog-example.event :as event]
            [propolog-example.svg :as svg]
            [propolog-example.utils :refer [deref-or-value ppr-str cat-into educe]]
            [datascript.core :as d]
            #?(:cljs [posh.reagent :as posh])
            #?(:cljs [reagent.core :as r :refer [atom]])))
;;
;; UTILS
;;
(defn- option-control [sim option]
  ;; ???: should options be system wide or per env
  (let [{:keys [:onyx.sim/pull]} (deref-or-value sim)
        {:keys [:onyx.sim.view/options]} (pull '[{:onyx.sim.view/options [*]}])]
;;     (log/debug "option-selected?" option?)
    (first
      (sequence
        (filter #(= option (:onyx/name %))
        options)))))

(defn- option-selected? [sim option]
  (:onyx.sim.control/toggled? (option-control sim option)))

(def default-view-options
  [{:onyx.sim.view/order 1
    :onyx/type :onyx.sim.control/toggle
    :onyx/name :onyx.sim.control/next-action?
    :onyx.sim.control/toggled? true
    :onyx.sim.control/label "Next Action"}
   {:onyx.sim.view/order 2
    :onyx/type :onyx.sim.control/toggle
    :onyx/name :onyx.sim.control/description?
    :onyx.sim.control/toggled? false
    :onyx.sim.control/label "Description"}
   {:onyx.sim.view/order 4
    :onyx/type :onyx.sim.control/toggle
    :onyx/name :onyx.sim.control/raw-env?
    :onyx.sim.control/toggled? false
    :onyx.sim.control/label "Raw Environment"}
   {:onyx.sim.view/order 5
    :onyx/type :toggle
    :onyx/name :onyx.sim.control/only-summary?
    :onyx.sim.control/depends [:onyx.sim.control/raw-env?]
    :onyx.sim.control/toggled? true
    :onyx.sim.control/label " * (Only Summary)"}
   {:onyx.sim.view/order 6
    :onyx/type :onyx.sim.control/toggle
    :onyx/name :onyx.sim.control/pretty-env?
    :onyx.sim.control/toggled? true
    :onyx.sim.control/label "Pretty Environment"}
   {:onyx.sim.view/order 7
    :onyx/type :onyx.sim.control/toggle
    :onyx/name :onyx.sim.control/render-segments?
    :onyx.sim.control/depends [:onyx.sim.control/pretty-env?]
    :onyx.sim.control/toggled? true
    :onyx.sim.control/label " * (Render Segments)"}])

(def default-sim
  {:onyx/type :onyx.sim/sim
   :onyx.sim/import-uris ["example.edn"]
   :onyx.sim/speed 1.0
   :onyx.sim.view/options default-view-options
   :onyx.sim/running? false
   :onyx.sim/hidden-tasks #{}})


(def schema
  {:onyx.core/catalog {:db/type :db.type/ref
                       :db/cardinality :db.cardinality/many}
   :onyx.sim.view/options {:db/type :db.type/ref
                           :db/cardinality :db.cardinality/many}
   :onyx/name {:db/unique :db.unique/identity}
   :onyx.core/job {:db/type :db.type/ref}
   :onyx.sim/env {:db/type :db.type/ref}})

(defn ds->onyx [sim-job]
  (-> sim-job
      (clojure.set/rename-keys {:onyx.core/catalog :catalog
                                :onyx.core/workflow :workflow
                                :onyx.core/lifecycles :lifecycles
                                :onyx.core/flow-conditions :flow-conditions})))

(defn db-create-sim [db {:as sim-spec
                         {:as job :keys [:onyx.core/catalog]} :onyx.core/job}]
  (let [sim (into (assoc default-sim :db/id -1) sim-spec)
        job (into {:db/id -2} job)
        option-ids (map (comp - (partial + 3)) (range))
        catalog-ids (map (comp - (partial + 3 (count default-view-options))) (range))
        options (map #(assoc % :db/id %2) default-view-options option-ids)
        catalog (map #(assoc % :db/id %2) catalog catalog-ids)
        job (assoc job :onyx.core/catalog catalog)
        sim (into sim {:onyx.sim.view/options options
                       :onyx.core/job job
                       :onyx.sim/env (onyx/init (ds->onyx job))})]
    (cat-into
      [sim
       job]
      options
      catalog)))

;;
;; VIEWS
;;
(defn pretty-outbox [sim task-name]
  (let [{:keys [:onyx.sim/pull :onyx.sim/render]} (deref-or-value sim)
        {{tasks :tasks} :onyx.sim/env}
        (pull
          '[{:onyx.sim/env [*]}])
        outputs (get-in tasks [task-name :outputs])
        render-segments? (option-selected? sim :onyx.sim.control/render-segments?)]
    ;; TODO: dump segments button
    ;; ???: feedback segments
    (when outputs
      (flui/v-box
        :class "onyx-outbox"
        :children
        [(flui/title
           :label "Outbox"
           :level :level3)
         (if (and render-segments? render)
;;            (transduce render container outputs)
           (render (reduce render (render) outputs))
           (flui/code :code outputs))]))))

(defn pretty-inbox [sim task-name]
  (let [{:keys [:onyx.sim/dispatch :onyx.sim/raw-dispatch :onyx.sim/pull :onyx.sim/render :db/id]} (deref-or-value sim)
         {import-uri :onyx.sim/import-uri
          {tasks :tasks} :onyx.sim/env}
        (pull '[:onyx.sim/import-uri
                {:onyx.sim/env [*]}])
        inbox (get-in tasks [task-name :inbox])
        render-segments? (option-selected? sim :onyx.sim.control/render-segments?)]
    (flui/v-box
      :class "onyx-inbox"
      :children
      [(flui/h-box
       :children
         [(flui/title
            :label "Inbox"
            :level :level3)
          (flui/input-text
            :model (str import-uri)
            :on-change #(dispatch {:onyx/type :onyx.sim.event/import-uri
                                   :onyx.sim/sim id
                                   :onyx.sim/task-name task-name
                                   :uri %}))
          (flui/button
            :label "Import Segments"
            :on-click #(raw-dispatch {:onyx/type :onyx.sim.event/import-segments
                                      :onyx.sim/sim id
                                      :onyx.sim/task-name task-name}))])
       (flui/gap :size ".5rem")
       (if (and render-segments? render)
         (render (reduce render (render) inbox))
         (flui/code :code inbox))])))

(defn pretty-task-box [sim task-name]
  (let [{:keys [:onyx.sim/dispatch onyx.sim/pull :db/id]} (deref-or-value sim)]
    (flui/v-box
      :class "onyx-task onyx-panel"
      :children
      [(flui/h-box
         :children
         [(flui/title :label task-name :level :level2)
          (flui/button :label "Hide" :on-click #(dispatch {:onyx/type :onyx.sim.event/hide-task
                                                           :onyx.sim/sim id
                                                           :onyx.sim/task-name task-name}))])
       (flui/call pretty-inbox sim task-name)
       (flui/call pretty-outbox sim task-name)])))

(defn pretty-env [sim]
  (let [{:keys [:onyx.sim/pull]} (deref-or-value sim)
         {hidden                :onyx.sim/hidden-tasks
         {sorted-tasks :sorted-tasks} :onyx.sim/env}
        (pull '[:onyx.sim/hidden-tasks
                {:onyx.sim/env
                 [:sorted-tasks]}])]
    (if-not (option-selected? sim :onyx.sim.control/pretty-env?)
      flui/none
      (flui/v-box
        :class "onyx-env onyx-panel"
        :children
        (cat-into
          []
          (for [task-name (remove (or hidden #{}) sorted-tasks)]
            ^{:key (:onyx/name task-name)}
            (flui/call pretty-task-box sim task-name)))))))

(defn summary [sim]
  (let [{:keys [:onyx.sim/pull :onyx.sim/summary-fn]
         :or {summary-fn onyx/env-summary}} (deref-or-value sim)
        {:keys [:onyx.sim/env]} (pull '[{:onyx.sim/env [*]}])]
  (flui/code :class "onyx-env-summary" :code (summary-fn env))))

(defn raw-env [sim]
  (let [raw-env? (option-selected? sim :onyx.sim.control/raw-env?)
        only-summary? (option-selected? sim :onyx.sim.control/only-summary?)]
       (if-not raw-env?
         flui/none
         (flui/v-box
           :class "onyx-env onyx-panel"
           :children
           [(flui/title :label "Raw Environment" :level :level3)
            (flui/call
              summary
              (if-not only-summary?
                (assoc (deref-or-value sim) :onyx.sim/summary-fn identity)
                sim))
            ]))))

(defn task-filter [sim]
  (let [{:keys [:onyx.sim/dispatch :onyx.sim/pull :db/id]} (deref-or-value sim)
        {hidden-tasks                           :onyx.sim/hidden-tasks
         {catalog          :onyx.core/catalog}  :onyx.core/job
         {sorted-tasks     :sorted-tasks}        :onyx.sim/env}
        (pull '[:onyx.sim/hidden-tasks
                {:onyx.sim/env
                 [:next-action :sorted-tasks]
                 :onyx.core/job
                 [{:onyx.core/catalog
                   [*]}]}])
        task-selection (into {} (map (juxt :onyx/name identity) catalog))
        task-choices (map task-selection sorted-tasks)]
    (flui/v-box
      :class "onyx-panel"
      :children
      [(flui/title :level :level4 :label "Hidden Tasks")
       (flui/selection-list :choices task-choices
                            :model hidden-tasks
                            :id-fn :onyx/name
                            :max-height "8.5em"
                            :width "20ch"
                            :label-fn :onyx/name
                            :on-change #(dispatch
                                          {:onyx/type :onyx.sim.event/hide-tasks
                                           :onyx.sim/sim id
                                           :onyx.sim/task-names %})
                            )])))

(defn env-presentation-controls [sim]
  (let [{:keys [:onyx.sim/dispatch :db/id]} (deref-or-value sim)
        pretty-env? (option-selected? sim :onyx.sim.control/pretty-env?)
        env-style (if pretty-env? :onyx.sim.control/pretty-env? :onyx.sim.control/raw-env?)
        summary-control (option-control sim :onyx.sim.control/only-summary?)
        segments-control (option-control sim :onyx.sim.control/render-segments?)
        action-control (option-control sim :onyx.sim.control/next-action?)
        description-control (option-control sim :onyx.sim.control/description?)
        ]
    (flui/v-box
      :class "onyx-panel"
      :children
      [(flui/title :level :level4 :label "Env Style")
       (flui/checkbox
         :model (:onyx.sim.control/toggled? action-control)
         :on-change #(dispatch {:onyx/type :onyx.sim.control/toggled?
                                :onyx.sim/sim id
                                :onyx.sim/control (:db/id action-control)
                                :onyx.sim.control/toggled? %})
         :label (:onyx.sim.control/label action-control))
       (flui/checkbox
         :model (:onyx.sim.control/toggled? description-control)
         :on-change #(dispatch {:onyx/type :onyx.sim.control/toggled?
                                :onyx.sim/sim id
                                :onyx.sim/control (:db/id description-control)
                                :onyx.sim.control/toggled? %})
         :label (:onyx.sim.control/label description-control))
       (flui/radio-button
         :model env-style
         :value :onyx.sim.control/pretty-env?
         :label "Pretty"
         :on-change #(dispatch {:onyx/type :onyx.sim.control/env-style
                                :onyx.sim/sim id
                                :onyx.sim.control/selected :onyx.sim.control/pretty-env?}))
       (flui/checkbox
         :model (:onyx.sim.control/toggled? segments-control)
         :on-change #(dispatch {:onyx/type :onyx.sim.control/toggled?
                                :onyx.sim/sim id
                                :onyx.sim/control (:db/id segments-control)
                                :onyx.sim.control/toggled? %})
         :disabled? (not pretty-env?)
         :label (:onyx.sim.control/label segments-control))
       (flui/radio-button
         :model env-style
         :value :onyx.sim.control/raw-env?
         :label "Raw"
         :on-change #(dispatch {:onyx/type :onyx.sim.control/env-style
                                :onyx.sim/sim id
                                :onyx.sim.control/selected :onyx.sim.control/raw-env?}))
       (flui/checkbox
         :model (:onyx.sim.control/toggled? summary-control)
         :on-change #(dispatch {:onyx/type :onyx.sim.control/toggled?
                                :onyx.sim/sim id
                                :onyx.sim/control (:db/id summary-control)
                                :onyx.sim.control/toggled? %})
         :disabled? pretty-env?
         :label (:onyx.sim.control/label summary-control))
       ])))

(defn view-controls [sim]
    (flui/v-box
      :class "onyx-controls onyx-panel"
      :children
      [(flui/title :level :level3 :label "View Options")
       (flui/h-box
         :children
         [(flui/call env-presentation-controls sim)
          (flui/call task-filter sim)])]))

(defn main-controls [sim]
  (let [{:keys [:onyx.sim/dispatch :onyx.sim/pull :db/id]} (deref-or-value sim)
        {:keys [:onyx.sim/running?]} (pull [:onyx.sim/running?])]
    (flui/h-box
      :class "onyx-controls onyx-panel"
      :children
      [(flui/button
         :class "onyx-button"
         :label "Tick"
         :disabled? running?
         :on-click #(dispatch {:onyx/type :onyx.api/tick
                               :onyx.sim/sim id}))
       (flui/button
         :class "onyx-button"
         :label "Step"
         :disabled? running?
         :on-click #(dispatch {:onyx/type :onyx.api/step
                               :onyx.sim/sim id}))
       (flui/button
         :class "onyx-button"
         :label "Drain"
         :disabled? running?
         :on-click #(dispatch {:onyx/type  :onyx.api/drain
                               :onyx.sim/sim id}))
       (flui/button
         :class "onyx-button"
         :label "Start"
         :disabled? running?
         :on-click #(dispatch {:onyx/type :onyx.api/start
                               :onyx.sim/sim id}))
       (flui/button
         :class "onyx-button"
         :label "Stop"
         :disabled? (not running?)
         :on-click #(dispatch {:onyx/type :onyx.api/stop
                               :onyx.sim/sim id}))])))

(defn next-action [sim]
  (let [{:keys [:onyx.sim/pull]} (deref-or-value sim)
        {{next-action :next-action} :onyx.sim/env} (pull '[{:onyx.sim/env [:next-action]}])]
    (if-not (option-selected? sim :onyx.sim.control/next-action?)
      flui/none
      (flui/h-box
        :class "onyx-pc onyx-panel"
        :children
        [(flui/label :class "onyx-field-label" :label "Next Action")
         (flui/label :label (pr-str next-action))]))))

(defn view [sim]
  (let [{:keys [:onyx.sim/pull]} (deref-or-value sim)
        {:keys [:onyx.sim/title :onyx.sim/description]} (pull '[:onyx.sim/title :onyx.sim/description])
        description? (option-selected? sim :onyx.sim.control/description?)]
    (flui/v-box
      :class "onyx-sim"
      :children
      [(flui/title :class "onyx-panel" :label (str "Onyx-Sim: " title) :level :level1)
       (flui/call view-controls sim)
       (when (and description? description)
         (flui/box :class "onyx-panel" :child [:p description]))
       (flui/call main-controls sim)
       (flui/call next-action sim)
       (flui/call raw-env sim)
       (flui/call pretty-env sim)
       ])))

;; (defn tick-sims* [db]
;;   (let [sims (d/q '[:find (d/pull [:onyx.sim/running? :onyx.sim/speed] ?sim)
;;                     :where
;;                     [?sim :onyx/type :onyx.sim/sim]] db)]
;;     [:onyx/name :main-env] ;; FIXME: magic :main-env. ???: use q.
;;     (for [{:keys [:db/id :onyx.sim/running? :onyx.sim/speed]} sims]
;;       (when running?
;;         ;; TODO: speed
;;         (pull-and-transition-env db id onyx/tick)))))

#?(:cljs
(defn tick-sims [conn]
  ;; FIXME: upgrade performance by doing everything from intent inline
  (event/dispatch! conn {:onyx/type :reagent/next-tick});;tick-sims*]])
  ;; TODO: create and check for STOP signal
  (r/next-tick #(tick-sims conn))))

(defn sim-selector [conn]
#?(:cljs
  (let [selected (atom (:db/id (d/entity @conn [:onyx/name :main-env])))]
    (tick-sims conn)
    (fn [conn]
      (let [sims (posh/q '[:find ?sim-name ?sim
                           :where
                           [?sim :onyx/name ?sim-name]
                           [?sim :onyx/type :onyx.sim/sim]] conn)
            sims (for [[nam id] @sims]
                   {:db/id id
                    :onyx/name nam})]
        (flui/v-box
          :children
          [(flui/horizontal-tabs
             :tabs sims
             :model @selected
             :id-fn :db/id
             :label-fn (comp name :onyx/name)
             :on-change #(reset! selected %))
           (flui/call view
                      {:db/id @selected
                       :onyx.sim/dispatch #(event/dispatch! conn %)
                       :onyx.sim/raw-dispatch #(event/raw-dispatch! conn %)
                       :onyx.sim/render svg/render-match
                       :onyx.sim/pull #(deref (posh/pull conn % @selected))
                       ;; :onyx.sim/q #(apply q %1 conn selected %&)
                       })]))))
    :clj
    [:div "Standard HTML not yet supported."]))
