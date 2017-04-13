(ns propolog-example.sim
  (:require [taoensso.timbre :as log]
            [propolog-example.onyx :as onyx]
            [propolog-example.flui :as flui]
            [propolog-example.event :as event :refer [dispatch raw-dispatch]]
            [propolog-example.svg :as svg]
            [propolog-example.utils :refer [deref-or-value ppr-str cat-into educe]]
            [datascript.core :as d]
            #?(:cljs [posh.reagent :as posh])
            #?(:cljs [reagent.core :as r :refer [atom]])))

;;
;; UTILS
;;
(def onyx-green "#43d16b")
(def onyx-gray "#354d56")
(def sim-blue "#6a8aac")
(def sim-pale-blue "#9ee3ff")
(def sim-gold "#c0b283")
(def sim-dark-tan "#A07F60")
(def sim-light-tan "#D7CEC7")

(def q
  #?(:cljs (comp deref posh/q)
    :clj (fn [query conn & args]
           (apply d/q query @conn args))))

(def pull
  #?(:cljs (comp deref posh/pull)
    :clj
           (fn [conn expr eid]
           (d/pull @conn expr eid))))

(defn pull-q [pull-expr query conn & input]
  (map (fn [[eid]] (pull conn pull-expr eid)) (apply q query conn input)))

(def task-colors
  {:function sim-pale-blue
   :input onyx-gray
   :output onyx-green})

(defn- option-control [{:keys [sim-id conn]} option]
  (pull conn '[*] [:onyx/name option]))

(defn- option-selected? [{:keys [sim-id conn]} option]
  (:onyx.sim.control/toggled? (pull conn '[:onyx.sim.control/toggled?] [:onyx/name option])))

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
        catalog-ids (map (comp - (partial + 3)) (range))
        catalog (map #(assoc % :db/id %2) catalog catalog-ids)
        job (assoc job :onyx.core/catalog catalog)
        sim (into sim {:onyx.core/job job
                       :onyx.sim/env (onyx/init (ds->onyx job))})]
    (cat-into
      [sim
       job]
      catalog)))

(defn db-create-ui [db]
  (let [options (map #(assoc % :db/id %2) default-view-options (range (- (inc (count default-view-options))) -1))]
    options))




;;
;; VIEWS
;;
(defn pretty-outbox [sim task-name]
  (let [{:keys [sim-id conn :onyx.sim/render]} (deref-or-value sim)
        {{tasks :tasks} :onyx.sim/env}
        (pull conn
          '[{:onyx.sim/env [*]}] sim-id)
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
  (let [{:keys [sim-id   conn :onyx.sim/render :db/id]} (deref-or-value sim)
         {import-uri :onyx.sim/import-uri
          {tasks :tasks} :onyx.sim/env}
        (pull conn '[:onyx.sim/import-uri
                {:onyx.sim/env [*]}] sim-id)
        inbox (get-in tasks [task-name :inbox])
        render-segments? (option-selected? sim :onyx.sim.control/render-segments?)]
    (flui/v-box
      :class "onyx-inbox"
      :children
      [(flui/h-box
         :gap ".5ch"
         :align :center
         :children
         [(flui/title
            ;;             :class "onyx-element"
            :label "Inbox"
            :level :level3)
          (flui/input-text
            ;;             :class "onyx-element"
            :model (str import-uri)
            :on-change #(dispatch conn {:onyx/type :onyx.sim.event/import-uri
                                   :onyx.sim/sim sim-id
                                   :onyx.sim/task-name task-name
                                   :uri %}))
          (flui/button
            ;;             :class "onyx-element"
            :label "Import Segments"
            :on-click #(raw-dispatch conn {:onyx/type :onyx.sim.event/import-segments
                                      :onyx.sim/sim sim-id
                                      :onyx.sim/task-name task-name}))])
       ;;        (flui/gap :size ".5rem")
       (if (and render-segments? render)
         (render (reduce render (render) inbox))
         (flui/code :code inbox))])))

(defn pretty-task-box [sim task-name]
  (let [{:keys [sim-id  conn]} (deref-or-value sim)
        {{tasks :tasks} :onyx.sim/env}
        (pull conn '[{:onyx.sim/env [*]}] sim-id)
        task-type (get-in tasks [task-name :event :onyx.core/task-map :onyx/type])]
    (flui/v-box
      :class "onyx-task onyx-panel"
      :gap ".25rem"
      :children
      [(flui/h-box
         :gap ".5ch"
         :align :center
         :style {:background-color (get task-colors task-type)
                 :border-radius :5px}
         :children
         [(flui/gap :size ".5ch")
          (flui/title
;;             :class "onyx-element"
            :label task-name
            :level :level2)
          (flui/button
;;             :class "onyx-element"
            :label "Hide"
            :on-click #(dispatch conn {:onyx/type :onyx.sim.event/hide-task
                                  :onyx.sim/sim sim-id
                                  :onyx.sim/task-name task-name}))])
       [pretty-inbox sim task-name]
       [pretty-outbox sim task-name]])))

(defn pretty-env [sim]
  (let [{:keys [sim-id conn]} (deref-or-value sim)
         {hidden                :onyx.sim/hidden-tasks
         {sorted-tasks :sorted-tasks} :onyx.sim/env}
        (pull conn '[:onyx.sim/hidden-tasks
                {:onyx.sim/env
                 [:sorted-tasks]}] sim-id)]
    (if-not (option-selected? sim :onyx.sim.control/pretty-env?)
      flui/none
      (flui/v-box
        :class "onyx-env"
        :children
        (cat-into
          []
          (for [task-name (remove (or hidden #{}) sorted-tasks)]
            ^{:key (:onyx/name task-name)}
            [pretty-task-box sim task-name]))))))

(defn summary [sim]
  (let [{:keys [sim-id conn :onyx.sim/summary-fn]
         :or {summary-fn onyx/env-summary}} (deref-or-value sim)
        {:keys [:onyx.sim/env]} (pull conn '[{:onyx.sim/env [*]}] sim-id)]
  (flui/code :class "onyx-panel" :code (summary-fn env))))

(defn raw-env [sim]
  (let [raw-env? (option-selected? sim :onyx.sim.control/raw-env?)
        only-summary? (option-selected? sim :onyx.sim.control/only-summary?)]
       (if-not raw-env?
         flui/none
         (flui/v-box
           :class "onyx-env"
           :children
           [(flui/title
;;               :class "onyx-element"
              :label "Raw Environment"
              :level :level3)
            (flui/call
              summary
              (if-not only-summary?
                (assoc (deref-or-value sim) :onyx.sim/summary-fn identity)
                sim))
            ]))))

(defn task-filter [sim]
  (let [{:keys [sim-id  conn]} (deref-or-value sim)
        {hidden-tasks                           :onyx.sim/hidden-tasks
         {catalog          :onyx.core/catalog}  :onyx.core/job
         {sorted-tasks     :sorted-tasks}        :onyx.sim/env}
        (pull conn '[:onyx.sim/hidden-tasks
                {:onyx.sim/env
                 [:next-action :sorted-tasks]
                 :onyx.core/job
                 [{:onyx.core/catalog
                   [*]}]}] sim-id)
        task-selection (into {} (map (juxt :onyx/name identity) catalog))
        task-choices (map task-selection sorted-tasks)]
    (log/debug sim-id)
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
                            :on-change #(dispatch conn
                                          {:onyx/type :onyx.sim.event/hide-tasks
                                           :onyx.sim/sim sim-id
                                           :onyx.sim/task-names %})
                            )])))

(defn env-presentation-controls [sim]
  (let [{:keys [conn ]} (deref-or-value sim)
        [pretty-env-control summary-control segments-control action-control description-control]
        (map #(pull conn '[*] [:onyx/name %]) [:onyx.sim.control/pretty-env? :onyx.sim.control/only-summary? :onyx.sim.control/render-segments? :onyx.sim.control/next-action? :onyx.sim.control/description?])
        pretty-env? (:onyx.sim.control/toggled? pretty-env-control)
        env-style (if pretty-env? :onyx.sim.control/pretty-env? :onyx.sim.control/raw-env?)]
    (flui/v-box
      :class "onyx-panel"
      :children
      [(flui/title :level :level4 :label "Env Style")
       (flui/checkbox
         :model (:onyx.sim.control/toggled? action-control)
         :on-change #(dispatch conn {:onyx/type :onyx.sim.control/toggled?
                                :onyx.sim/control (:db/id action-control)
                                :onyx.sim.control/toggled? %})
         :label (:onyx.sim.control/label action-control))
       (flui/checkbox
         :model (:onyx.sim.control/toggled? description-control)
         :on-change #(dispatch conn {:onyx/type :onyx.sim.control/toggled?
                                :onyx.sim/control (:db/id description-control)
                                :onyx.sim.control/toggled? %})
         :label (:onyx.sim.control/label description-control))
       (flui/radio-button
         :model env-style
         :value :onyx.sim.control/pretty-env?
         :label "Pretty"
         :on-change #(dispatch conn {:onyx/type :onyx.sim.control/env-style
                                :onyx.sim.control/selected :onyx.sim.control/pretty-env?}))
       (flui/checkbox
         :model (:onyx.sim.control/toggled? segments-control)
         :on-change #(dispatch conn {:onyx/type :onyx.sim.control/toggled?
                                :onyx.sim/control (:db/id segments-control)
                                :onyx.sim.control/toggled? %})
         :disabled? (not pretty-env?)
         :label (:onyx.sim.control/label segments-control))
       (flui/radio-button
         :model env-style
         :value :onyx.sim.control/raw-env?
         :label "Raw"
         :on-change #(dispatch conn {:onyx/type :onyx.sim.control/env-style
                                :onyx.sim.control/selected :onyx.sim.control/raw-env?}))
       (flui/checkbox
         :model (:onyx.sim.control/toggled? summary-control)
         :on-change #(dispatch conn {:onyx/type :onyx.sim.control/toggled?
                                :onyx.sim/control (:db/id summary-control)
                                :onyx.sim.control/toggled? %})
         :disabled? pretty-env?
         :label (:onyx.sim.control/label summary-control))
       ])))

(defn view-controls [sim]
    (flui/v-box
      :class "onyx-panel"
      :children
      [(flui/title
;;          :class "onyx-element"
         :level :level3
         :label "View Options")
       (flui/h-box
         :children
         [;;[env-presentation-controls sim)
          [task-filter sim]])]))

(defn main-controls [sim]
  (let [{:keys [sim-id   conn]} (deref-or-value sim)
        {:keys [:onyx.sim/running?]} (pull conn [:onyx.sim/running?] sim-id)]
    (flui/h-box
      :class "onyx-panel"
      :gap ".5ch"
      :children
      [(flui/button
;;          :class "onyx-element"
         :label "Tick"
         :disabled? running?
         :on-click #(dispatch conn {:onyx/type :onyx.api/tick
                               :onyx.sim/sim sim-id}))
       (flui/button
;;          :class "onyx-element"
         :label "Step"
         :disabled? running?
         :on-click #(dispatch conn {:onyx/type :onyx.api/step
                               :onyx.sim/sim sim-id}))
       (flui/button
;;          :class "onyx-element"
         :label "Drain"
         :disabled? running?
         :on-click #(dispatch conn {:onyx/type  :onyx.api/drain
                               :onyx.sim/sim sim-id}))
       (flui/button
;;          :class "onyx-element"
         :label "Start"
         :disabled? running?
         :on-click #(raw-dispatch conn {:onyx/type :onyx.api/start
                               :onyx.sim/sim sim-id}))
       (flui/button
;;          :class "onyx-element"
         :label "Stop"
         :disabled? (not running?)
         :on-click #(dispatch conn {:onyx/type :onyx.api/stop
                               :onyx.sim/sim sim-id}))])))

(defn next-action [sim]
  (let [{:keys [sim-id conn]} (deref-or-value sim)
        {{next-action :next-action} :onyx.sim/env} (pull conn '[{:onyx.sim/env [:next-action]}] sim-id)]
    (if-not (option-selected? sim :onyx.sim.control/next-action?)
      flui/none
      (flui/h-box
        :class "onyx-panel"
        :gap "1ch"
;;         :align :end
        :children
        [(flui/label
           :class "field-label"
           :label "Next Action")
         (flui/label :label (pr-str next-action))]))))

(defn view [sim]
  (let [{:keys [sim-id conn]} (deref-or-value sim)
        {:keys [:onyx.sim/title :onyx.sim/description]} (pull conn '[:onyx.sim/title :onyx.sim/description] sim-id)
        description? (option-selected? sim :onyx.sim.control/description?)]
    (flui/v-box
      :class "onyx-sim"
      :gap ".25rem"
      :children
      [(flui/title
;;          :class "onyx-element"
         :label title
         :level :level1)
       (when (and description? description)
         (flui/box
           :class "onyx-panel"
           :child [:p description]))
;;        [view-controls sim)
;;        [task-filter sim)
       [main-controls sim]
       [next-action sim]
       [raw-env sim]
       [pretty-env sim]
       ])))

(defn manage-sims [{:keys [conn]}]
  (let [sims (pull-q '[:onyx/name]
               '[:find ?sim
                 :in $
                 :where
                 [?sim :onyx/type :onyx.sim/sim]
                 ] conn)]
  (flui/v-box
    :class "onyx-sim"
    :children
    (cat-into
      []
      (for [sim sims]
        [:p (:onyx/name sim)])
      [[:div "TODO: Add simulator"]]))))

(defn settings [sim]
  (flui/box
    :class "onyx-sim"
    :child
    [env-presentation-controls sim]))

(def icons
  {:sims
   {:id :sims
    :label [:i {:class "zmdi zmdi-widgets"}]
    :target manage-sims}
   :settings
   {:id :settings
    :label [:i {:class "zmdi zmdi-settings"}]
    :target settings}})

(defn sim-selector [conn]
#?(:cljs
  (let [selected (atom (:db/id (d/entity @conn [:onyx/name :main-env])))
        selection-view (fn [id]
                         (if (keyword? id)
                           [(get-in icons [id :target])
                            {:conn conn}]
                         [view
                          {:sim-id id
                           :conn conn
                           }]))]
    (fn [conn]
      (let [sims (q '[:find ?sim-name ?sim ?running
                      :in $
                      :where
                      [?sim :onyx/name ?sim-name]
                      [?sim :onyx/type :onyx.sim/sim]
                      [?sim :onyx.sim/running? ?running]] conn)
            any-running? (transduce
                       (map (fn [[_ _ r]]
                              r))
                       #(or %1 %2)
                       false
                       sims)
            sims (for [[nam id _] sims]
                   {:id id
                    :label (name nam)})
            ]
        (flui/v-box
          :children
          [(flui/gap :size ".25rem")
           (flui/h-box
             :class "onyx-nav"
             :align :center
             :gap "1ch"
             :children
             [(flui/h-box
                :class "onyx-logo"
                :children
                [(flui/box :child [:img {:class (str "onyx-logo-img"
                                                     ;; FIXME: abrupt ending animation
                                                     (when any-running? " spinning"))
                                         :src "onyx-logo.png"}])
                 (flui/label :label "nyx-sim")])
              (flui/horizontal-bar-tabs
;;                 :class "onyx-panel"
                :tabs (conj (into [(:settings icons)] sims) (:sims icons))
                :model @selected
;;                 :id-fn :db/id
;;                 :label-fn (comp name :onyx/name)
                :on-change #(reset! selected %))])
           (flui/gap :size ".25rem")
           (selection-view @selected)
           ]))))
    :clj
    [:div "Standard HTML not yet supported."]))
