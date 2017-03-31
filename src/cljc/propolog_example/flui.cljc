(ns propolog-example.flui
  (:require [taoensso.timbre :as log]
            #?(:cljs [re-com.core :as rc])
            [propolog-example.utils :as utils]
            [clojure.pprint :refer [pprint]]))

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

#?
(:cljs
  (defn code*
    "Eventually a pretty lookin code block with syntax highlighting. For now very bad."
    [& {:as args :keys [code child] cl :class}]
    (let [args (-> args
                   (dissoc :code)
                   (assoc :class (str "rc-code" cl )))
          code (with-out-str (pprint code))]
      (assert (not child) (str "Code should not have a :child element. Got " child))
      (utils/mapply rc/box :child [:code [:pre code]] args))))


(def code (provide #?(:clj 'code
                      :cljs code*)))
(def button (provide #?(:clj 'button :cljs rc/button)))
(def h-box (provide #?(:clj 'h-box :cljs rc/h-box)))
(def v-box (provide #?(:clj 'v-box :cljs rc/v-box)))
(def box (provide #?(:clj 'box :cljs rc/box)))
(def label (provide #?(:clj 'label :cljs rc/label)))
(def p (provide #?(:clj 'p :cljs rc/p)))
(def title (provide #?(:clj 'title :cljs rc/title)))
(def input-text (provide #?(:clj 'input-text :cljs rc/input-text)))
(def input-textarea (provide #?(:clj 'input-textarea :cljs rc/input-textarea)))
(def selection-list (provide #?(:clj 'selection-list :cljs rc/selection-list)))
(def single-dropdown (provide #?(:clj 'single-dropdown :cljs rc/single-dropdown)))
