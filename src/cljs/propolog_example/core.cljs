(ns propolog-example.core
  (:require [reagent.core :as r]
            [propolog-example.gui :as gui]))

(r/render [gui/root] (js/document.getElementById "app-hook"))
