(ns propolog-example.control
  (:require [taoensso.timbre :as log]
            [propolog-example.flui :as flui]
            [propolog-example.utils :as utils :refer [cat-into]]
            [datascript.core :as d]
;;             [onyx-local-rt.impl :refer [kw->fn]]
            [onyx.static.util :refer [kw->fn]]
            #?(:cljs [posh.reagent :as posh])))


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

(defn compile-control [conn control-spec]
  ;; ???: are controls compiling more than once? that would be less than ideal
  (into
    {}
    (for [[attr value] control-spec]
      (if (list? value)
        [attr
         (apply (kw->fn (first value)) conn (rest value))]
        [attr value]))))

(defn pull-control [conn control-name]
  (compile-control conn (pull conn '[*] [:control/name control-name])))

(defn ^:export control-attr [conn control-name attr]
  (get
    (compile-control conn (pull conn [attr] [:control/name control-name]))
    attr))

;;
;; simple ds handlers
;;
(defn ^:export simple-toggle [conn control-name]
  ;; FIXME: should use dispatch
  #(d/transact!
     conn
     [[:db/add [:control/name control-name] :control/toggled? %]]))

(defn ^:export simple-choose-one [conn control-name]
  ;; FIXME: should use dispatch
  #(d/transact!
     conn
     [[:db/add [:control/name control-name] :control/chosen %]]))

;;
;; simple model fns
;;
(defn ^:export simple-chosen? [conn control-name choice]
  (let [chosen (control-attr conn control-name :control/chosen)]
    (contains? chosen choice)))

(defn ^:export simple-not-chosen? [conn control-name choice]
  (not (simple-chosen? conn control-name choice)))

;;
;; controls -> hiccup
;;
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

(defn active-logo [conn control-name]
  (let [{:keys [:control/label :control/img :control/active?]} (pull-control conn control-name)]
    ;; FIXME: abrupt ending animation
    (flui/h-box
      :class "active-logo"
      :children
      [[flui/box :child [:img {:class (str "active-logo-img" (when active? " spinning"))
                               :src img}]]
       [flui/label :label label]])))

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

(defn nav-bar [conn control-name]
  (let [{:keys [:control/id-fn :control/label-fn :control/choices :control/choose :control/chosen]} (pull-control conn control-name)
        id-fn (or id-fn :id)
        label-fn (or label-fn :label)]
    ;; ???: should the or-clause for id-fn be part of compile-controls?
    ;; ???: maybe a more generic way to do the bridging. drop nil arguments?
    [flui/horizontal-bar-tabs
     :tabs choices
     :model chosen ;; ???: treat chosen as a set always? distinction for choose one vs choose many?
     :id-fn id-fn
     :label-fn label-fn
     :label-fn label-fn
     :on-change choose]))

(defn indicator-display [conn control-name]
  (let [{:keys [:control/display]} (pull-control conn control-name)]
    [flui/p display]))


;; FIXME: should be middleware/interceptors so you can use lots of this style
(defn when-show? [[component-fn conn control-name]]
  (if (control-attr conn control-name :control/show?)
    [component-fn conn control-name]
    flui/none))
