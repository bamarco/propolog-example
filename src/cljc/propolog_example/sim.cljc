(ns propolog-example.sim
  (:require [taoensso.timbre :as log]
            [propolog-example.onyx :as onyx]
            [propolog-example.flui :as flui]
            #?(:cljs [reagent.core :as r])
            [propolog-example.utils :refer [deref-or-value ppr-str cat-into]]))

(defn basic-outbox [sim task-name]
  ;; FIXME: Convert to pull syntax
  (let [{env :onyx.sim/env} (deref-or-value sim)
        {:keys [outputs]} (get-in env [:tasks task-name])]
    (when outputs
      (flui/v-box
        :class "outbox"
        :children
        [(flui/title :label "Outbox" :level :level3)
         (flui/code :code outputs)]))))

(defn basic-inbox [sim task-name]
  ;; FIXME: Convert to pull syntax
  (let [{env :onyx.sim/env
         dispatch :re-frame/dispatch
         sim-name :onyx/name
         import-uri :onyx.sim/import-uri} (deref-or-value sim)
        {:keys [inbox]} (get-in env [:tasks task-name])]
    (flui/v-box
      :class "inbox"
      :children
      [(flui/title :label "Inbox" :level :level3)
       (flui/input-text :model import-uri :on-change #(dispatch [:onyx.sim/import-uri [:onyx/name sim-name] task-name %]))
       (flui/button :label "Import Segments" :on-click #(dispatch [:onyx.sim/import-segments [:onyx/name sim-name] task-name]))
       (flui/code :code inbox)])))

(defn basic-task-box [sim task-name]
  ;; FIXME: Convert to pull syntax
  (let [{env :onyx.sim/env
         sim-name :onyx/name
         dispatch :re-frame/dispatch} (deref-or-value sim)]
    (flui/v-box
      :class "onyx-task onyx-panel"
      :children
      [(flui/title :label task-name :level :level2)
       (flui/button :label "Hide" :on-click #(dispatch [:onyx.sim/hide-task [:onyx/name sim-name] task-name]))
       (flui/call basic-inbox sim task-name)
       (flui/call basic-outbox sim task-name)])))

(defn pretty-outbox [sim task-name]
  (let [{pull :datascript/pull
         render :onyx.sim/render
         sim-name :onyx/name} (deref-or-value sim)
        {{tasks :tasks} :onyx.sim/env}
        (pull
          '[{:onyx.sim/env [*]}]
          [:onyx/name sim-name])
        outputs (get-in tasks [task-name :outputs])]
    (when outputs
      (flui/v-box
        :class "onyx-outbox"
        :children
        [(flui/title
           :label "Outbox"
           :level :level3)
         (render (reduce render (render) outputs))]))))

(defn pretty-inbox [sim task-name]
  (let [{dispatch :re-frame/dispatch
         pull :datascript/pull
         render :onyx.sim/render ;; FIXME: move to the pull
         sim-name :onyx/name} (deref-or-value sim)
         {import-uri :onyx.sim/import-uri
          {tasks :tasks} :onyx.sim/env}
        (pull '[:onyx.sim/import-uri
                {:onyx.sim/env [*]}]
              [:onyx/name sim-name])
        inbox (get-in tasks [task-name :inbox])]
    (flui/v-box
      :class "onyx-inbox"
      :children
      [(flui/title
         :label "Inbox"
         :level :level3)
       (flui/input-text
         :model import-uri
         :on-change #(dispatch [:onyx.sim/import-uri [:onyx/name sim-name] task-name %]))
       (flui/button
         :label "Import Segments"
         :on-click #(dispatch [:onyx.sim/import-segments [:onyx/name sim-name] task-name]))
       (render (reduce render (render) inbox))])))

(defn pretty-task-box [sim task-name]
  (let [{sim-name :onyx/name
         dispatch :re-frame/dispatch} (deref-or-value sim)]
    (flui/v-box
      :class "onyx-task onyx-panel"
      :children
      [(flui/title :label task-name :level :level2)
       (flui/button :label "Hide" :on-click #(dispatch [:onyx.sim/hide-task [:onyx/name sim-name] task-name]))
       (flui/call pretty-inbox sim task-name)
       (flui/call pretty-outbox sim task-name)])))



(defn pretty-env [sim]
  (let [{pull :datascript/pull
         sim-name :onyx/name} (deref-or-value sim)
         {hidden                :onyx.sim/hidden-tasks
         {sorted-tasks :sorted-tasks} :onyx.sim/env}
        (pull '[:onyx.sim/hidden-tasks
                {:onyx.sim/env
                 [:sorted-tasks]}]
              [:onyx/name sim-name])]
  (flui/v-box
    :class "onyx-env onyx-panel"
    :children
    (cat-into
      []
    (for [task-name (remove (or hidden #{}) sorted-tasks)]
      ^{:key (:onyx/name task-name)}
      (flui/call pretty-task-box sim task-name))))))

(defn raw-env [sim]
  (let [{env :onyx.sim/env
         summary-fn :onyx.sim/summary-fn
         :or {summary-fn onyx/env-summary}} (deref-or-value sim)]
  (flui/code :class "onyx-env-summary" :code (summary-fn env))))

(defn summary [sim]
  (let [{dispatch :re-frame/dispatch
;;          listen :onyx.sim/listen
         pull :datascript/pull
         sim-name :onyx/name} (deref-or-value sim)
         show-summary false show-full false]
       (when show-summary
         (flui/v-box
           :class "onyx-env onyx-panel"
           :children
           [(flui/title :label "env-summary" :level :level3)
            ;; FIXME:
;;             (flui/call raw-env
;;                        (if show-full
;;                          (assoc sim :onyx.sim/summary-fn identity)
;;                          sim))
            ]))))

(defn task-filter [sim]
  (let [{dispatch :re-frame/dispatch
;;          listen :onyx.sim/listen
         pull :datascript/pull
         sim-name :onyx/name} (deref-or-value sim)
        {?hidden-tasks                           :onyx.sim/hidden-tasks
         {?catalog          :onyx.core/catalog}  :onyx.core/job
         {?sorted-tasks     :sorted-tasks}        :onyx.sim/env}
        (pull '[:onyx.sim/hidden-tasks
                {:onyx.sim/env
                 [:next-action :sorted-tasks]
                 :onyx.core/job
                 [{:onyx.core/catalog
                   [*]}]}]
              [:onyx/name sim-name])
        task-selection (into {} (map (juxt :onyx/name identity) ?catalog))
        ?task-choices (map task-selection ?sorted-tasks)]
  (flui/h-box
         :class "onyx-panel"
         :children
         [(flui/label :label "Hidden Tasks:")
          (flui/selection-list :choices ?task-choices
                               :model ?hidden-tasks
                               :id-fn :onyx/name
                               :max-height "8em"
                               :width "20ch"
                               :label-fn :onyx/name
                               :on-change #(dispatch [:onyx.sim/hide-tasks [:onyx/name sim-name] %]))
         ])))

(defn next-action [sim]
  (let [{pull :datascript/pull
         sim-name :onyx/name} (deref-or-value sim)
        {{?next-action :next-action} :onyx.sim/env}
        (pull '[{:onyx.sim/env
                 [:next-action]}]
              [:onyx/name sim-name])]
    (flui/v-box
         :class "onyx-pc onyx-panel"
         :children
         [(flui/title :label "Next Action" :level :level4)
          (flui/label :label (pr-str ?next-action))])))

(defn main-controls [sim]
  (let [{dispatch :re-frame/dispatch
         sim-name :onyx/name} (deref-or-value sim)]
    (flui/h-box
      :class "onyx-controls onyx-panel"
      :children
      [(flui/button
         :class "onyx-button"
         :label "Tick"
         :on-click #(dispatch [:onyx.api/tick [:onyx/name sim-name]]))
       (flui/button
         :class "onyx-button"
         :label "Step"
         :on-click #(dispatch [:onyx.api/step [:onyx/name sim-name]]))
       (flui/button
         :class "onyx-button"
         :label "Drain"
         :on-click #(dispatch [:onyx.api/drain [:onyx/name sim-name]]))
       (flui/button
         :class "onyx-button"
         :label "Start"
         :on-click #(dispatch [:onyx.api/start [:onyx/name sim-name]]))
       (flui/button
         :class "onyx-button"
         :label "Stop"
         :on-click #(dispatch [:onyx.api/stop [:onyx/name sim-name]]))])))

(defn view [sim]
  (let [{dispatch :re-frame/dispatch
         pull :datascript/pull
         sim-name :onyx/name} (deref-or-value sim)
        {?title       :onyx.sim/title
         ?description :onyx.sim/description}
        (pull '[:onyx.sim/title
                :onyx.sim/description]
              [:onyx/name sim-name])]
    (flui/v-box
      :class "onyx-sim"
      :children
      [(flui/title :class "onyx-panel" :label (str "Onyx-Sim: " ?title) :level :level1)
       (flui/box :class "onyx-panel" :child [:p ?description])
       (flui/call main-controls sim)
       (flui/call next-action sim)
       (flui/call task-filter sim)
       (flui/call summary sim)
       (flui/call pretty-env sim)
       ])))
