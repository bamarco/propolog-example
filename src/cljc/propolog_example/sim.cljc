(ns propolog-example.sim
  (:require [taoensso.timbre :as log]
            [propolog-example.onyx :as onyx]
            [propolog-example.flui :as flui]
            [propolog-example.utils :as utils :refer [cat-into]]))

(defn basic-outbox [sim task-name]
  (let [{env :onyx.sim/env} (utils/deref-or-value sim)
        {:keys [outputs]} (get-in env [:tasks task-name])]
    (when outputs
      (flui/v-box
        :class "outbox"
        :children
        [(flui/title :label "Outbox" :level :level3)
         (flui/code :code outputs)]))))


(defn basic-inbox [sim task-name]
  (let [{env :onyx.sim/env
         dispatch :onyx.sim/dispatch
         sim-name :onyx/name
         import-uri :onyx.sim/import-uri} (utils/deref-or-value sim)
        {:keys [inbox]} (get-in env [:tasks task-name])]
    (flui/v-box
      :class "inbox"
      :children
      [(flui/title :label "Inbox" :level :level3)
       (flui/input-text :model import-uri :on-change #(dispatch [:onyx.sim/import-uri [:onyx/name sim-name] task-name %]))
       (flui/button :label "Import Segments" :on-click #(dispatch [:onyx.sim/import-segments [:onyx/name sim-name] task-name]))
       (flui/code :code inbox)])))

(defn basic-task-box [sim task-name]
  (let [{env :onyx.sim/env
         sim-name :onyx/name
         dispatch :onyx.sim/dispatch} (utils/deref-or-value sim)]
    (flui/v-box
      :class "onyx-task onyx-panel"
      :children
      [(flui/title :label task-name :level :level2)
       (flui/button :label "Hide" :on-click #(dispatch [:onyx.sim/hide-task [:onyx/name sim-name] task-name]))
       (flui/component basic-inbox sim task-name)
       (flui/component basic-outbox sim task-name)])))

(defn pretty-outbox [sim task-name]
  (let [{env :onyx.sim/env
         render :onyx.sim/render} (utils/deref-or-value sim)
        {:keys [outputs]} (get-in env [:tasks task-name])]
    (when outputs
      (flui/v-box
        :class "onyx-outbox"
        :children
          [(flui/title :label "Outbox" :level :level3)
           (flui/h-box :children (map (comp (partial flui/box :child) render) outputs))]))))

(defn pretty-inbox [sim task-name]
  (let [{env :onyx.sim/env
         dispatch :onyx.sim/dispatch
         render :onyx.sim/render
         sim-name :onyx/name
         import-uri :onyx.sim/import-uri} (utils/deref-or-value sim)
        {:keys [inbox]} (get-in env [:tasks task-name])]
    (flui/v-box
      :class "onyx-inbox"
      :children
      (into
        [(flui/title :label "Inbox" :level :level3)
         (flui/input-text :model import-uri :on-change #(dispatch [:onyx.sim/import-uri [:onyx/name sim-name] task-name %]))
         (flui/button :label "Import Segments" :on-click #(dispatch [:onyx.sim/import-segments [:onyx/name sim-name] task-name]))
         (flui/h-box :children (map (comp (partial flui/box :child) render) inbox))]))))

(defn pretty-task-box [sim task-name]
  (let [{env :onyx.sim/env
         sim-name :onyx/name
         dispatch :onyx.sim/dispatch} (utils/deref-or-value sim)]
    (flui/v-box
      :class "onyx-task onyx-panel"
      :children
      [(flui/title :label task-name :level :level2)
       (flui/button :label "Hide" :on-click #(dispatch [:onyx.sim/hide-task [:onyx/name sim-name] task-name]))
       (flui/component pretty-inbox sim task-name)
       (flui/component pretty-outbox sim task-name)])))



(defn pretty-env [sim]
  (let [{env :onyx.sim/env
         hidden :onyx.sim/hidden-tasks} (utils/deref-or-value sim)]
  (flui/v-box
    :class "onyx-env onyx-panel"
    :children
    (cat-into
      []
    (for [task-name
          ;;               (keys (:tasks env))
          (remove (or hidden #{}) (:sorted-tasks env))
          ]
      ^{:key (:onyx/name task-name)}
      (flui/component pretty-task-box sim task-name))))))

(defn raw-env [sim]
  (let [{env :onyx.sim/env
         summary-fn :onyx.sim/summary-fn
         :or {summary-fn onyx/env-summary}} (utils/deref-or-value sim)]
  (flui/code :class "onyx-env-summary" :code (summary-fn env))))

(defn view [sim]
  (let [{dispatch :onyx.sim/dispatch
         listen :onyx.sim/listen
         sim-name :onyx/name} (utils/deref-or-value sim)
        env (:onyx.sim/env sim)
        hidden-tasks (:onyx.sim/hidden-tasks sim)
        job (listen [:onyx.core/job [:onyx/name sim-name]]);;(utils/deref-or-value job)
        catalog (into {} (map (juxt :onyx/name identity) (:onyx.core/catalog job)))
        sorted-tasks (map catalog (:sorted-tasks env))
        visible-tasks (into #{} (remove (or hidden-tasks #{}) (:sorted-tasks env)))
        show-summary false show-full false]
    (flui/v-box
      :class "onyx-sim"
      :children
      [(flui/title :class "onyx-panel" :label (str "Onyx-Sim: " (:onyx.sim/title sim)) :level :level1)
       (flui/box :class "onyx-panel" :child [:p (:onyx.sim/description sim)])
       (flui/h-box
         :class "onyx-controls onyx-panel"
         :children
         [(flui/button :class "onyx-button" :label "Tick" :on-click #(dispatch [:onyx.api/tick [:onyx/name sim-name]]))
          (flui/button :class "onyx-button" :label "Step" :on-click #(dispatch [:onyx.api/step [:onyx/name sim-name]]))
          (flui/button :class "onyx-button" :label "Drain" :on-click #(dispatch [:onyx.api/drain [:onyx/name sim-name]]))
          (flui/button :class "onyx-button" :label "Start" :on-click #(dispatch [:onyx.api/start [:onyx/name sim-name]]))
          (flui/button :class "onyx-button" :label "Stop" :on-click #(dispatch [:onyx.api/stop [:onyx/name sim-name]]))])
       (flui/h-box
         :class "onyx-panel"
         :children
         [(flui/label :label "Hidden Tasks:")
          (flui/selection-list :choices sorted-tasks
                               :model hidden-tasks
                               :id-fn :onyx/name
                               :max-height "8em"
                               :width "20ch"
                               :label-fn :onyx/name
                               :on-change #(dispatch [:onyx.sim/hide-tasks [:onyx/name sim-name] %]))])
       (flui/v-box
         :class "onyx-pc onyx-panel"
         :children
         [(flui/title :label "Next Action" :level :level4)
          (flui/label :label (pr-str (:next-action env)))])
       (when show-summary
         (flui/v-box
           :class "onyx-env onyx-panel"
           :children
           [(flui/title :label "env-summary" :level :level3)
            (flui/component raw-env
                            (if show-full
                              (assoc sim :onyx.sim/summary-fn identity)
                              sim))]))
       (flui/component pretty-env sim)])))
