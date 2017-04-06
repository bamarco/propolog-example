(ns propolog-example.gui
  (:require [taoensso.timbre :as log]
            [propolog-example.sub :refer [subscribe]]
            [propolog-example.sim :as sim]
            [propolog-example.flui :as flui]
            [propolog-example.utils :as utils]))

(defn root []
  (let [sim-id [:onyx/name
                :main-env
;;                 :render-env
                ]]
    (flui/v-box
      :children
      [(flui/call sim/view (subscribe [:propolog-example.sub/sim-env sim-id]))])))
