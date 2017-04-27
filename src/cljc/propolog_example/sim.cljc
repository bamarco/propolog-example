(ns propolog-example.sim
  (:require [taoensso.timbre :as log]
            [propolog-example.onyx :as onyx]
            [propolog-example.flui :as flui]
            [propolog-example.svg :as svg]
            [propolog-example.event :as event :refer [dispatch raw-dispatch]]
            [propolog-example.utils :as utils :refer [cat-into]]
            [datascript.core :as d]
            #?(:cljs [posh.reagent :as posh])
            #?(:cljs [reagent.core :as r :refer [atom]])))


(declare control-attr)
(declare control-catalog)
(declare selected-sim)

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
  #?(:cljs
      (comp deref posh/q)
      :clj
      (fn [query conn & args]
        (apply d/q query @conn args))))

(def pull
  #?(:cljs
      (comp deref posh/pull)
      :clj
      (fn [conn expr eid]
        (d/pull @conn expr eid))))

(defn pull-q [pull-expr query conn & input]
  (map (fn [[eid]] (pull conn pull-expr eid)) (apply q query conn input)))

(def task-colors
  {:function sim-pale-blue
   :input onyx-gray
   :output onyx-green})

(def default-sim
  {:onyx/type :onyx.sim/sim
   :onyx.sim/import-uris ["example.edn"]
   :onyx.sim/speed 1.0
   :onyx.sim/running? false
   :onyx.sim/hidden-tasks #{}})


(def schema
  {:onyx.core/catalog {:db/type :db.type/ref
                       :db/cardinality :db.cardinality/many}
   :onyx.sim.view/options {:db/type :db.type/ref
                           :db/cardinality :db.cardinality/many}
   :onyx/name {:db/unique :db.unique/identity}
   :control/name {:db/unique :db.unique/identity}
   :onyx.core/job {:db/type :db.type/ref}
   :onyx.sim/selected-env {:db/type :db.type/ref}
   :onyx.sim/env {:db/type :db.type/ref}})

(defn ds->onyx [sim-job]
  (-> sim-job
      (clojure.set/rename-keys {:onyx.core/catalog :catalog
                                :onyx.core/workflow :workflow
                                :onyx.core/lifecycles :lifecycles
                                :onyx.core/flow-conditions :flow-conditions})))

(defn- add-tempids [coll gen-tempid!]
  (map #(into {:db/id %2} %1) coll (repeatedly gen-tempid!)))

(defn db-create-sim [db {:as sim-spec
                         {:as job :keys [:onyx.core/catalog]} :onyx.core/job} & {:keys [gen-tempid!] :or {gen-tempid! utils/gen-tempid!}}]
  (let [sim (into (assoc default-sim :db/id (gen-tempid!)) sim-spec)
        job (into {:db/id (gen-tempid!)} job)
        catalog (add-tempids catalog gen-tempid!)
        job (assoc job :onyx.core/catalog catalog)
        sim (into sim {:onyx.core/job job
                       :onyx.sim/env (onyx/init (ds->onyx job))})]
    (cat-into
      [sim
       job]
      catalog)))

(defn db-create-ui [db & {:keys [gen-tempid!] :or {gen-tempid! utils/gen-tempid!}}]
  (let [control-catalog (add-tempids control-catalog gen-tempid!)]
    (cat-into
      [{:db/id (gen-tempid!)
        :onyx/name :onyx.sim/settings
        :onyx.sim/selected-view :onyx.sim/selected-env
        :onyx.sim/selected-env [:onyx/name :main-env] ;; FIXME: set up a default hello world env. maybe a few others.
        }]
      control-catalog)))


;;
;; VIEWS
;;
(defn pretty-outbox [conn & {:keys [task-name render]}]
  (let [sim-id (selected-sim conn)
        {{tasks :tasks} :onyx.sim/env}
        (pull conn
              '[{:onyx.sim/env [*]}] sim-id)
        outputs (get-in tasks [task-name :outputs])]
    ;; ???: dump segments button
    ;; ???: feedback segments
    (when outputs
      [flui/v-box
        :class "onyx-outbox"
        :children
        [[flui/title
           :label "Outbox"
           :level :level3]
         [render outputs]]])))

(defn pretty-inbox [conn & {:keys [task-name render]}]
  (let [sim-id (selected-sim conn)
        {:keys [:onyx.sim/import-uri]
         {tasks :tasks} :onyx.sim/env}
        (pull conn '[:onyx.sim/import-uri
                     {:onyx.sim/env [*]}] sim-id)
        inbox (get-in tasks [task-name :inbox])]
    [flui/v-box
     :class "onyx-inbox"
     :children
     [[flui/h-box
       :gap ".5ch"
       :align :center
       :children
       [[flui/title
         :label "Inbox"
         :level :level3]
        [flui/input-text
         :model (str import-uri)
         :on-change #(dispatch conn {:onyx/type :onyx.sim.event/import-uri
                                     :onyx.sim/sim sim-id
                                     :onyx.sim/task-name task-name
                                     :uri %})]
        [flui/button
         :label "Import Segments"
         :on-click #(raw-dispatch conn {:onyx/type :onyx.sim.event/import-segments
                                        :onyx.sim/sim sim-id
                                        :onyx.sim/task-name task-name})]]]
      [render inbox]]]))

(def code-render (partial flui/code :code))

(defn pretty-task-box [conn task-name]
  (let [sim-id (selected-sim conn)
        {:keys [:onyx.sim/render]
         {tasks :tasks} :onyx.sim/env}
        (pull conn '[:onyx.sim/render {:onyx.sim/env [*]}] sim-id)
        task-type (get-in tasks [task-name :event :onyx.core/task-map :onyx/type])
        local-render (get-in tasks [task-name :onyx.sim/render])
        render-segments? (control-attr conn :onyx.sim/render-segments? :control/toggled?)
        render-fn (if render-segments?
                    (or local-render render code-render)
                    code-render)
        ]
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
       [pretty-inbox conn
        :task-name task-name
        :render render-fn]
       [pretty-outbox conn
        :task-name task-name
        :render render-fn]])))

(defn pretty-env [conn]
  (let [sim-id (selected-sim conn)
        {hidden                :onyx.sim/hidden-tasks
         {sorted-tasks :sorted-tasks} :onyx.sim/env}
        (pull conn '[:onyx.sim/hidden-tasks
                     {:onyx.sim/env
                      [:sorted-tasks]}] sim-id)]
    (flui/v-box
      :class "onyx-env"
      :children
      (cat-into
        []
        (for [task-name (remove (or hidden #{}) sorted-tasks)]
          ^{:key (:onyx/name task-name)}
          [pretty-task-box conn task-name])))))

(defn summary
  ([conn] (summary conn nil))
  ([conn summary-fn]
   (let [sim-id (selected-sim conn)
         summary-fn (or summary-fn onyx/env-summary)
         {:keys [:onyx.sim/env]} (pull conn '[{:onyx.sim/env [*]}] sim-id)]
     (flui/code :class "onyx-panel" :code (summary-fn env)))))

(defn raw-env [conn]
  (let [only-summary? (control-attr conn :onyx.sim/only-summary? :control/toggled?)]
    (flui/v-box
      :class "onyx-env"
      :children
      [(flui/title
         :label "Raw Environment"
         :level :level3)
       [summary conn (when-not only-summary? identity)]
       ])))

(defn selected-sim [conn]
  (let [{{:keys [:db/id]} :onyx.sim/selected-env} (pull conn '[:onyx.sim/selected-env] [:onyx/name :onyx.sim/settings])]
    ;; ???: Should we use {:db/id id} or just id
    id))

(defn selected-view [conn]
  (let [{:keys [:onyx.sim/selected-view]} (pull conn '[:onyx.sim/selected-view] [:onyx/name :onyx.sim/settings])]
    selected-view))

(defn toggle-play [conn]
  #(event/raw-dispatch conn {:onyx/type :onyx.sim/toggle-play
                             :onyx.sim/sim (selected-sim conn)}))

(defn running? [conn]
  (:onyx.sim/running? (pull conn '[:onyx.sim/running?] (selected-sim conn))))

(defn tick [conn]
  #(event/dispatch conn {:onyx/type :onyx.sim/transition-env
                         :onyx.sim/transition :onyx.api/tick
                         :onyx.sim/sim (selected-sim conn)}))

(defn step [conn]
  #(event/dispatch conn {:onyx/type :onyx.sim/transition-env
                         :onyx.sim/transition :onyx.api/step
                         :onyx.sim/sim  (selected-sim conn)}))

(defn drain [conn]
  #(event/dispatch conn {:onyx/type :onyx.sim/transition-env
                         :onyx.sim/transition :onyx.api/drain
                         :onyx.sim/sim (selected-sim conn)}))

(defn next-action-actual [conn]
  (let [{{:keys [next-action]} :onyx.sim/env} (pull conn '[{:onyx.sim/env [:next-action]}] (selected-sim conn))]
    next-action))

(defn description-actual [conn]
  (let [{:keys [:onyx.sim/description]}
        (pull conn '[:onyx.sim/description] (selected-sim conn))]
    description))

(defn hidden-tasks [conn]
  (log/debug "hidden-tasks")
  (:onyx.sim/hidden-tasks (pull conn '[:onyx.sim/hidden-tasks] (selected-sim conn))))

(defn hide-tasks [conn]
  (log/debug "hide-tasks")
  #(event/dispatch conn {:onyx/type :onyx.sim.event/hide-tasks
                         :onyx.sim/sim (selected-sim conn)
                         :onyx.sim/task-names %}))

(defn sorted-tasks [conn]
  (let [{{:keys [sorted-tasks]} :onyx.sim/env
         {:keys [:onyx.core/catalog]} :onyx.core/job}
        (pull
          conn
          '[{:onyx.sim/env [:sorted-tasks]
             :onyx.core/job [{:onyx.core/catalog [*]}]}]
          (selected-sim conn))
        task-possible (into {} (map (juxt :onyx/name identity) catalog))
        task-choices (map task-possible sorted-tasks)]
    task-choices))

(defn show-description? [conn]
  (let [{:keys [:control/toggled?]} (pull conn '[:control/toggled?] [:control/name :onyx.sim/description?])]
    toggled?))

(defn show-next-action? [conn]
  (let [{:keys [:control/toggled?]} (pull conn '[:control/toggled?] [:control/name :onyx.sim/next-action?])]
    toggled?))

(defn simple-toggle [conn control-name]
  ;; FIXME: should use dispatch
  #(d/transact!
     conn
     [[:db/add [:control/name control-name] :control/toggled? %]]))

(defn simple-choose-one [conn control-name]
  ;; FIXME: should use dispatch
  #(d/transact!
     conn
     [[:db/add [:control/name control-name] :control/chosen %]]))

(defn simple-chosen? [conn control-name choice]
  (let [{:keys [:control/chosen]} (pull conn '[:control/chosen] [:control/name control-name])]
    (contains? chosen choice)))

(defn simple-not-chosen? [conn control-name choice]
  (not (simple-chosen? conn control-name choice)))

(def control-catalog
  [{:control/type :indicator
    :control/name :onyx.sim/next-action
    :control/label "Next Action"
    :control/show? (list show-next-action?)
    :control/display (list next-action-actual)}
   {:control/type :indicator
    :control/name :onyx.sim/description
    :control/label "Description"
    :control/show? (list show-description?)
    :control/display (list description-actual)}
   {:control/type :toggle
    :control/name :onyx.sim/description?
    :control/label "Show Description"
    :control/toggle (list simple-toggle :onyx.sim/description?)
    :control/toggled? false}
   {:control/type :toggle
    :control/name :onyx.sim/next-action?
    :control/label "Show Next Action"
    :control/toggle (list simple-toggle :onyx.sim/next-action?)
    :control/toggled? true}
   {:control/type :choice
    :control/name :onyx.sim/env-display-style
    :control/label "Environment Display Style"
    :control/chosen #{:pretty-env}
    :control/choose (list simple-choose-one :onyx.sim/env-display-style)
    :control/choices [{:id :pretty-env
                       :label "Pretty"}
                      {:id :raw-env
                       :label "Raw"}]}
   {:control/type :toggle
    :control/name :onyx.sim/render-segments?
    :control/label "(Render Segments)"
    :control/toggle (list simple-toggle :onyx.sim/render-segments?)
    :control/disabled? (list simple-not-chosen? :onyx.sim/env-display-style :pretty-env)
    :control/toggled? true}
   {:control/type :toggle
    :control/name :onyx.sim/only-summary?
    :control/label "(Only Summary)"
    :control/toggle (list simple-toggle :onyx.sim/only-summary?)
    :control/disabled? (list simple-not-chosen? :onyx.sim/env-display-style :raw-env)
    :control/toggled? true}
   {:control/type :action
    :control/name :onyx.api/tick
    :control/label "Tick"
    :control/action (list tick)
    :control/disabled? (list running?)}
   {:control/type :action
    :control/name :onyx.api/step
    :control/label "Step"
    :control/action (list step)
    :control/disabled? (list running?)}
   {:control/type :action
    :control/name :onyx.api/drain
    :control/label "Drain"
    :control/action (list drain)
    :control/disabled? (list running?)}
   {:control/type :toggle
    :control/name :onyx.sim/play
    :control/label "Play"
    :control/toggle-label "Pause"
    :control/toggled? (list running?)
    :control/toggle (list toggle-play)}
   {:control/type :choice
    :control/name :onyx.sim/hidden-tasks
    :control/label "Hidden Tasks"
    :control/chosen (list hidden-tasks)
    :control/choose (list hide-tasks)
    :control/choices (list sorted-tasks)
    :control/id-fn :onyx/name
    :control/label-fn :onyx/name}])

(defn compile-control [conn control-spec]
  (into
    {}
    (for [[attr value] control-spec]
      (if (list? value)
        [attr (apply (first value) conn (rest value))]
        [attr value]))))

(defn pull-control [conn control-name]
  (compile-control conn (pull conn '[*] [:control/name control-name])))

(defn control-attr [conn control-name attr]
  (get
    (compile-control conn (pull conn [attr] [:control/name control-name]))
    attr))

(defn field-label [conn control-name]
  [flui/label
   :class "field-label"
   :label (control-attr conn control-name :control/label)])

(defn action-button [conn control-name]
  (let [{:keys [:control/disabled? :control/action :control/label]} (pull-control conn control-name)]
    [flui/button
     :label label
     :disabled? disabled?
     :on-click action]))

(defn toggle-button [conn control-name]
  (let [{:keys [:control/label :control/toggle-label :control/toggled? :control/toggle]} (pull-control conn control-name)]
    [flui/button
     :label (if toggled? (or toggle-label label) label)
     :on-click toggle]))

(defn toggle-checkbox [conn control-name]
  (let [{:keys [:control/disabled? :control/label :control/toggle-label :control/toggled? :control/toggle]} (pull-control conn control-name)]
    [flui/checkbox
     :model toggled?
     :disabled? disabled?
     :label (if toggled? (or toggle-label label) label)
     :on-change toggle
      ]))

(defn selection-list [conn control-name]
  (let [{:keys [:control/label :control/choices :control/chosen :control/choose :control/id-fn :control/label-fn]} (pull-control conn control-name)]
    [flui/v-box
     :children
     [[flui/label
       :class "field-label"
       :label label]
      [flui/selection-list
       :choices choices
       :model chosen
       :id-fn id-fn
       :label-fn label-fn
       :on-change choose]]]))

(defn indicator-label [conn control-name]
  (let [{:keys [:control/label :control/display]} (pull-control conn control-name)]
    [flui/h-box
     :gap "1ch"
     :children
     [[flui/label
       :class "field-label"
       :label label]
      [flui/label
       :label display]]]))

(defn radio-choice [conn control-name index]
  (let [{:keys [:control/label-fn :control/id-fn :control/chosen :control/choices :control/choose]} (pull-control conn control-name)
        label-fn (or label-fn :label)
        id-fn (or id-fn :id)
        choice (get choices index)]
    ;; ???: assert only one chosen
    [flui/radio-button
     :model (first chosen)
     :value (id-fn choice)
     :label (label-fn choice)
     :on-change #(choose #{(id-fn choice)})]))

(defn indicator-display [conn control-name]
  (let [{:keys [:control/display]} (pull-control conn control-name)]
    [flui/p display]))

(defn when-show? [[component-fn conn control-name]]
  ;; FIXME: should be middleware/interceptors so you can use lots of this style
  (if (control-attr conn control-name :control/show?)
    [component-fn conn control-name]
    flui/none))

(defn env-style [conn]
  [flui/v-box
   :children
   [[field-label conn :onyx.sim/env-display-style]
    [radio-choice conn :onyx.sim/env-display-style 0]
    [toggle-checkbox conn :onyx.sim/render-segments?]
    [radio-choice conn :onyx.sim/env-display-style 1]
    [toggle-checkbox conn :onyx.sim/only-summary?]
    ]])

(defn action-box [conn]
  [flui/h-box
   :gap ".5ch"
   :children
   [[action-button conn :onyx.api/tick]
    [action-button conn :onyx.api/step]
    [action-button conn :onyx.api/drain]
    [toggle-button conn :onyx.sim/play]]])

(defn sim-view [conn]
  [flui/v-box
   :children
   [[selection-list conn :onyx.sim/hidden-tasks]
    [when-show? [indicator-display conn :onyx.sim/description]]
    [action-box conn]
    [when-show? [indicator-label conn :onyx.sim/next-action]]

    (when (simple-chosen? conn :onyx.sim/env-display-style :pretty-env)
      [pretty-env conn])
    (when (simple-chosen? conn :onyx.sim/env-display-style :raw-env)
      [raw-env conn])
    ]])

(defn manage-sims [conn]
  (let [sims (pull-q '[*]
               '[:find ?sim
                 :in $
                 :where
                 [?sim :onyx/type :onyx.sim/sim]
                 ] conn)]
  (flui/v-box
    :children
    (cat-into
      [(flui/title
         :label "Simulator Management"
         :level :level1)]
      (for [{:keys [:onyx.sim/title :onyx.sim/description]} sims]
        (flui/v-box
          :gap "1ch"
          :children
          [(flui/title
             :level :level2
             :label title)
           (flui/p description)]))
      [(flui/p "TODO: + Simulator")]))))

(defn settings [conn]
  (flui/v-box
    :children
    [(flui/title
         :label "Settings"
         :level :level1)
     [toggle-checkbox conn :onyx.sim/description?]
     [toggle-checkbox conn :onyx.sim/next-action?]
     [env-style conn]]))

(defmulti display-selected (fn [_ selection]
                             selection))

(defmethod display-selected
  :onyx.sim/selected-env
  [conn _]
  [sim-view conn])

(defmethod display-selected
  :settings
  [conn _]
  [settings {:conn conn}])

(defmethod display-selected
  :sims
  [conn _]
  [manage-sims conn])

(defn content-view [conn]
  (let [view (selected-view conn)]
    [flui/box
     :class "onyx-sim"
     :child
     [display-selected conn (selected-view conn)]]))

(defn toggle->button [{:keys [:control/label :control/toggle-label :control/toggled? :control/toggle]}]
  [flui/button
   :label (if toggled? label toggle-label)
   :on-click toggle])

(defn toggle->checkbox [{:keys [:control/label :control/toggle-label :control/toggled? :control/toggle]}]
  [flui/checkbox
   :model toggled?
   :label (if toggled? label toggle-label)
   :on-click toggle])

(defn toggle->aui [{:keys [:control/label :control/toggle-label :control/toggled? :control/toggle]}]
  (let [aui-api pr-str]
    ;; TODO: make an audio interface toolkit
    [(aui-api
       :command label
       :disabled? toggled?
       :action toggle)
     (aui-api
       :command toggle-label
       :disabled? (not toggled?)
       :action toggle)]))

(defn toggle->cli [{:keys [:control/label :control/toggle-label :control/toggled? :control/toggle]}]
  (let [cli-api pr-str]
    ;; TODO: make a command line interface toolkit
    [(cli-api
       :command label
       :disabled? toggled?
       :action toggle)
     (cli-api
       :command toggle-label
       :disabled? (not toggled?)
       :action toggle)]))

(def icons
  {:sims
   {:id :sims
    :label [:i {:class "zmdi zmdi-widgets"}]
    :target manage-sims}
   :settings
   {:id :settings
    :label [:i {:class "zmdi zmdi-settings"}]
    :target settings}})

(defn sim-selection [conn]
  (let [{{:keys [:db/id]} :onyx.sim/selected-env
         :keys [:onyx.sim/selected-view]}
        (pull
          conn
          '[:onyx.sim/selected-env :onyx.sim/selected-view]
          [:onyx/name :onyx.sim/settings])]
    (if (= :onyx.sim/selected-env selected-view)
      id
      selected-view)))

(defn sim-selector [conn]
#?(:cljs
    (let [selected (sim-selection conn)
          sims (q '[:find ?title ?sim ?running
                    :in $
                    :where
                    [?sim :onyx/name ?sim-name]
                    [?sim :onyx.sim/title ?title]
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
                  :label nam})]
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
               (flui/label :label "nyx-sim (alpha)")])
            (flui/horizontal-bar-tabs
              :tabs (conj (into [(:settings icons)] sims) (:sims icons))
              :model selected
              :on-change #(dispatch conn {:onyx/type :onyx.sim/select-view
                                          :selected %}))])
         [flui/gap :size ".25rem"]
         [content-view conn]]
         ;; ???: bottom gap for scrolling
        ))
:clj
[:div "Standard HTML not yet supported."]))
