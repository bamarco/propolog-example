(ns propolog-example.gui
  (:require [taoensso.timbre :as log]
            [propolog-example.init :as init]
            [propolog-example.sim :as sim]
            [propolog-example.flui :as flui]))

;; TODO: I made that pretty error message for onyx runtime. Make sure I submit it.
;; ???: what is re-frame using for logging? it looks nice.

(defonce conn (init/create-conn))

(defn root []
  (flui/call sim/sim-selector conn))
