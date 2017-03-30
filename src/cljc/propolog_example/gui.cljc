(ns propolog-example.gui
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [propolog-example.sub :as sub :refer [listen]]
            [propolog-example.onyx :as onyx]
            [propolog-example.flui :as flui]
            [propolog-example.utils :as utils]
            [clojure.set :refer [map-invert]]
            ))

;; (defn pretty-onyx-out [env out]
;; ;;   (cond
;; ;;     (container? out) [pretty-layout-path out]
;; ;;     (representation? out) [re-com/v-box :children [[pretty-layout-path out] [display out]]]
;; ;;     :else
;;     [:div.v-box

;;      [
;; ;;        [re-com/button :label "^Feed" :on-click #(swap! env (fn [e] (onyx-feed-loop e out)))]
;; ;;       [pretty-layout-path out]
;;        ]]
;;     )

(defn pretty-onyx-outbox [env outputs]
;;   (into
  [:div.v-box.outbox
   [:h3 "Outbox"]

   [:p.segment (pr-str outputs)]
;;    (component button
;;               :label "^Feed-all"
;;               :on-click (fn [] #()))
   ]
;;     (for [out outputs]
;;       [pretty-onyx-out env out]
      )

(defn pretty-onyx-inbox [env task-name inputs]
  (let [import-uri (listen [:onyx.sim/import-uri [:propolog/name :main-env]])]
;;     (if (= 0 (count inputs))
;;       [:span]
      [:div.v-box.inbox
       [:h3 "Inbox"]
       (flui/input-text :model import-uri :on-change #(rf/dispatch [:onyx.sim/import-uri [:propolog/name :main-env] task-name %]))
       (flui/button :label "Import Segments" :on-click #(rf/dispatch [:onyx.sim/import-segments [:propolog/name :main-env] task-name]))
       [:p.segment (pr-str inputs)]]))

(defn task-box [env task-name]
  (let [{:keys [inbox outputs]} (get-in env [:tasks task-name])]
;;     (log/debug "task-name" (get-in env [:tasks task-name]))
    [:div.v-box.task
     [:h2 task-name]
     (flui/button :label "Hide" :on-click #(rf/dispatch [:onyx.sim/hide-task [:propolog/name :main-env] task-name]))
     (flui/component pretty-onyx-inbox env task-name inbox)
     (when outputs (flui/component pretty-onyx-outbox env outputs))]))

(defn pretty-onyx [env & {:keys [hidden]}]
  (into
    [:div.v-box
     [:p (str "Next Action: " (pr-str (:next-action env)))]]
    (for [task-name
          ;;               (keys (:tasks env))
          (remove (or hidden #{}) (:sorted-tasks env))
          ]
      ^{:key (:onyx/name task-name)}
      (flui/component task-box env task-name))))

(defn env-info [env]
  [:div.v-box
   [:p (str ":sorted-tasks" (:sorted-tasks env))]
   [:p (str "env-summary" (pr-str (onyx/env-summary env)))]])

(defn onyx-sim []
  (let [env (listen [:propolog-example.sub/propolog-env [:propolog/name :main-env]])
        onyx-env (listen [:onyx.sim/env [:propolog/name :main-env]])
        hidden-tasks (listen [:onyx.sim/hide-tasks [:propolog/name :main-env]])
        job (listen [:onyx.core/job [:propolog/name :main-env]])
        catalog (into {} (map (juxt :onyx/name identity) (:onyx.core/catalog job)))
        sorted-tasks (map catalog (:sorted-tasks onyx-env))
        visible-tasks (into #{} (remove (or hidden-tasks #{}) (:sorted-tasks onyx-env)))]
    [:div.v-box
     [:h1 (:propolog/title env)]
     [:p (:propolog/description env)]
;;      [env-info onyx-env]
     [:h2 "Onyx Simulation"]
     (flui/h-box
       :children
       [(flui/button :label "Tick" :on-click #(rf/dispatch [:onyx.api/tick [:propolog/name :main-env]]))
        (flui/button :label "Step" :on-click #(rf/dispatch [:onyx.api/step [:propolog/name :main-env]]))
        (flui/button :label "Drain" :on-click #(rf/dispatch [:onyx.api/drain [:propolog/name :main-env]]))
        (flui/button :label "Start" :on-click #(rf/dispatch [:onyx.api/start [:propolog/name :main-env]]))
        (flui/button :label "Stop" :on-click #(rf/dispatch [:onyx.api/stop [:propolog/name :main-env]]))])
     (flui/h-box
       :children
       [(flui/label :label "Hidden Tasks:")
        (flui/selection-list :choices sorted-tasks
                             :model hidden-tasks
                             :id-fn :onyx/name
                             :max-height "8em"
                             :width "20ch"
                             :label-fn :onyx/name
                             :on-change #(rf/dispatch [:onyx.sim/hide-tasks [:propolog/name :main-env] %]))])
     (flui/component pretty-onyx onyx-env :hidden hidden-tasks)]))

(defn root []
  (flui/component onyx-sim))
