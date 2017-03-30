(ns propolog-example.flui
  (:require [taoensso.timbre :as log]
            #?(:cljs [re-com.core :as rc])
            [propolog-example.utils :as utils]))

(defn component
  "Because you can't use reagent on the serverside. :("
  [f & args]
  #?(:cljs (into [f] args)
     :clj (apply f args)))

(defn provide
  "map to re-com component and probide stub for hiccup"
  [c-name]
  #?(:cljs (fn [& args]
             (apply component c-name args))
      :clj  (fn [& args]
              [:div.v-box.stub [:p (str "STUB (cljs-only): " (pr-str (cons c-name args)))]])))

(def button (provide #?(:clj 'button :cljs rc/button)))
(def input-text (provide #?(:clj 'button :cljs rc/input-text)))
(def selection-list (provide #?(:clj 'button :cljs rc/selection-list)))
(def single-dropdown (provide #?(:clj 'button :cljs rc/single-dropdown)))
