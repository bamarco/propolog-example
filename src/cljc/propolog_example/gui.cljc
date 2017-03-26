(ns propolog-example.gui
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            #?(:cljs [re-com.core :as re-com])
            [propolog-example.sub :as sub :refer [listen]]
            [propolog-example.onyx :as onyx]
            [propolog-example.utils :as utils]))

(defn component
  "Because you can't use reagent on the serverside. :("
  [f & args]
  #?(:cljs (into [f] args)
     :clj (apply f args)))

(defn button [& {:as args :keys [label]}]
  #?(:cljs (utils/mapply component re-com/button args)
     :clj (component (fn [& {:keys [label]}]
                            [:p.fake-button (str "JS-only button: " label)]
                            ))))

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
   [:p (pr-str outputs)]
;;    (component button
;;               :label "^Feed-all"
;;               :on-click (fn [] #()))
   ]
;;     (for [out outputs]
;;       [pretty-onyx-out env out]
      )

(defn pretty-onyx-inbox [env inputs]
  (if (= 0 (count inputs))
    [:span]
    [:div.v-box.inbox
      [:h3 "Inbox"]
      [:p (pr-str inputs)]]))

(defn task-box [env task-name]
  (let [{:keys [inbox outputs]} (get-in env [:tasks task-name])]
;;     (log/debug "task-name" (get-in env [:tasks task-name]))
    [:div.v-box.task
     [:h2 task-name]
     (when inbox [pretty-onyx-inbox env inbox])
     (when outputs [pretty-onyx-outbox env outputs])]))

(defn pretty-onyx [env]
  (let [env-data (onyx/env-summary env)]
     (into
       [:div.v-box
        [:p (str "Next Action: " (pr-str (:next-action env-data)))]]
        (for [task-name
              (map first (:tasks env))
;;               (:sorted-tasks env) ???: right now the task come out in a better order naively than :sorted-tasks is
              ]
          ^{:key (:onyx/name task-name)}
          [task-box env task-name]))))

(defn env-info [env]
  [:div
   [:p (str (keys env))]
   [:p (str (:sorted-tasks env))]])

(defn onyx-sim []
  (let [env (listen [:propolog-example.sub/onyx-env])]
    [:div.v-box
     [env-info env]
     [:div.h-box
      (button :label "Tick" :on-click #(rf/dispatch [:propolog-example.event/onyx-tick]))
      (button :label "Step" :on-click #(rf/dispatch [:propolog-example.event/onyx-step]))
      (button :label "Drain" :on-click #(rf/dispatch [:propolog-example.event/onyx-drain]))]
  (component pretty-onyx env)]))

(defn root []
  [:div.v-box
   [:h1 "Hello, Onyx!"]
   [onyx-sim]])
