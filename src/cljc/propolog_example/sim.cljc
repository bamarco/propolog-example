(ns propolog-example.sim
  (:require [taoensso.timbre :as log]
            [propolog-example.onyx :as onyx]
            [propolog-example.flui :as flui]
            #?(:cljs [reagent.core :as r])
            [propolog-example.utils :refer [deref-or-value ppr-str cat-into educe]]))
;;
;; UTILS
;;
(defn- option-selected? [sim option?]
  (let [{:keys [:datascript/pull]} (deref-or-value sim)
        {:keys [:onyx.sim.view/options]} (pull '[{:onyx.sim.view/options [*]}])]
;;     (log/debug "option-selected?" option?)
    (first
      (sequence
        (comp
          (filter #(= option? (:onyx/name %)))
          (map :onyx.sim.control/toggled?))
        options))))

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
   :onyx.sim/running false
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
    (log/debug "opts" (count options) (count catalog))
    (cat-into
      [sim
       job]
      options
      catalog)))

;;
;; VIEWS
;;
(defn pretty-outbox [sim task-name]
  (let [{:keys [:datascript/pull :onyx.sim/render]} (deref-or-value sim)
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
           (render (reduce render (render) outputs))
           (flui/code :code outputs))]))))

(defn pretty-inbox [sim task-name]
  (let [{:keys [:re-frame/dispatch :datascript/pull :onyx.sim/render :db/id]} (deref-or-value sim)
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
            :model import-uri
            :on-change #(dispatch [:onyx.sim/import-uri id task-name %]))
          (flui/button
            :label "Import Segments"
            :on-click #(dispatch [:onyx.sim/import-segments id task-name]))])
       (flui/gap :size ".5rem")
       (if (and render-segments? render)
         (render (reduce render (render) inbox))
         (flui/code :code inbox))])))

(defn pretty-task-box [sim task-name]
  (let [{:keys [:re-frame/dispatch :db/id]} (deref-or-value sim)]
    (flui/v-box
      :class "onyx-task onyx-panel"
      :children
      [(flui/h-box
         :children
         [(flui/title :label task-name :level :level2)
          (flui/button :label "Hide" :on-click #(dispatch [:onyx.sim/hide-task task-name]))])
       (flui/call pretty-inbox sim task-name)
       (flui/call pretty-outbox sim task-name)])))

(defn pretty-env [sim]
  (let [{:keys [:datascript/pull]} (deref-or-value sim)
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
  (let [{:keys [:datascript/pull :onyx.sim/summary-fn]
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
  (let [{:keys [:re-frame/dispatch :datascript/pull :db/id]} (deref-or-value sim)
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
  (flui/h-box
         :class "onyx-panel"
         :children
         [(flui/label :class "onyx-field-label" :label "Hidden Tasks")
          (flui/selection-list :choices task-choices
                               :model hidden-tasks
                               :id-fn :onyx/name
                               :max-height "8.5em"
                               :width "20ch"
                               :label-fn :onyx/name
                               :on-change #(dispatch [:onyx.sim/hide-tasks id %]))])))

(defn view-filter [sim]
  (let [{:keys [:re-frame/dispatch :datascript/pull :db/id]} (deref-or-value sim)
        {:keys [:onyx.sim.view/options]} (pull '[{:onyx.sim.view/options [*]}])
        task-selection (into {} (map (juxt :onyx/name identity) options))
        choices (sort-by :onyx.sim.view/order options)
        selections (into #{} (comp
                               (filter :onyx.sim.control/toggled?)
                               (map :db/id))
                         options)
        disabled (into #{} (comp
                             (filter :onyx.sim.control/depends)
                             ;; FIXME: only when depends are not activated do you disable
                             (map :db/id))
                       options)]
    ;; TODO: gray out disabled
    ;; ???: should depends make a tree
;;     (log/debug "view-filter" disabled)
  (flui/h-box
         :class "onyx-panel"
         :children
         [;;(flui/label :class "onyx-field-label" :label "View Options")
          (flui/selection-list :choices choices
                               :model selections
                               :id-fn :db/id
                               :max-height "8.5em"
                               :width "25ch"
                               :label-fn :onyx.sim.control/label
                               :on-change #(dispatch [:onyx.sim.view/options id %]))])))

(defn env-presentation-controls [sim]
  (let [{:keys [:re-frame/dispatch :db/id]} (deref-or-value sim)
        pretty-env? (option-selected? sim :onyx.sim.control/pretty-env?)
        selected (if pretty-env? :onyx.sim.control/pretty-env? :onyx.sim.control/raw-env?)]
    (flui/v-box
      :children
      [(flui/title :level :level4 :label "Env Style")
       (flui/radio-button
         :model selected
         :value :onyx.sim.control/pretty-env?
         :label "Pretty"
         :on-change #(dispatch [:onyx.sim.control/env-style id :onyx.sim.control/pretty-env?]))
       (flui/radio-button
         :model selected
         :value :onyx.sim.control/raw-env?
         :label "Raw"
         :on-change #(dispatch [:onyx.sim.control/env-style id :onyx.sim.control/raw-env?]))])))

(defn view-controls [sim]
    (flui/v-box
      :class "onyx-controls onyx-panel"
      :children
      [(flui/title :level :level3 :label "View Options")
       (flui/h-box
         :children
         [(flui/call view-filter sim)
          (flui/call env-presentation-controls sim)
          (flui/call task-filter sim)])]))

(defn main-controls [sim]
  (let [{:keys [:re-frame/dispatch :db/id]} (deref-or-value sim)]
    (flui/h-box
      :class "onyx-controls onyx-panel"
      :children
      [(flui/button
         :class "onyx-button"
         :label "Tick"
         :on-click #(dispatch [:onyx.api/tick id]))
       (flui/button
         :class "onyx-button"
         :label "Step"
         :on-click #(dispatch [:onyx.api/step id]))
       (flui/button
         :class "onyx-button"
         :label "Drain"
         :on-click #(dispatch [:onyx.api/drain id]))
       (flui/button
         :class "onyx-button"
         :label "Start"
         :on-click #(dispatch [:onyx.api/start id]))
       (flui/button
         :class "onyx-button"
         :label "Stop"
         :on-click #(dispatch [:onyx.api/stop id]))])))

(defn next-action [sim]
  (let [{:keys [:datascript/pull]} (deref-or-value sim)
        {{next-action :next-action} :onyx.sim/env} (pull '[{:onyx.sim/env [:next-action]}])]
    (if-not (option-selected? sim :onyx.sim.control/next-action?)
      flui/none
      (flui/h-box
        :class "onyx-pc onyx-panel"
        :children
        [(flui/label :class "onyx-field-label" :label "Next Action")
         (flui/label :label (pr-str next-action))]))))

(defn view [sim]
  (let [{:keys [:re-frame/dispatch :datascript/pull]} (deref-or-value sim)
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
