(ns propolog-example.gui
  (:require [taoensso.timbre :as log]
;;             [propolog-example.sub :refer [subscribe]]
            [propolog-example.sim :as sim]
            [re-frame.core :as rf]
            [propolog-example.flui :as flui]
            [propolog-example.utils :as utils]
            [propolog-example.catalog :as catalog]
            [propolog-example.svg :as svg]))

;; TODO: I made that pretty error message for onyx runtime. Make sure I submit it.

(defn root []
  (let [env {:re-frame.core/dispatch rf/dispatch
             :datascript.core/conn @(rf/subscribe [:propolog-example.sub/conn])}]
  (flui/call sim/sim-selector env)))
;;   (let [sim-id [:onyx/name
;;                 :main-env
;; ;;                 :render-env
;;                 ]]
;;     (flui/v-box
;;       :children
;;       [
       ;;(flui/call sim/view (subscribe [:propolog-example.sub/sim-env sim-id]))
;;        ]))
