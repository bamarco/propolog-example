(ns propolog-example.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [propolog-example.event] ;; Needed for registrations for events
            [propolog-example.gui :as gui]
            [propolog-example.init :as init]
            ))

(defn hook []
  (init/init)
  (fn []
    [gui/root]))

(r/render [hook] (js/document.getElementById "app-hook"))
