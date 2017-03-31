(ns propolog-example.catalog
  (:require [taoensso.timbre :as log]
            [clojure.spec :as s]
;;             [propolog-example.flui :as flui]
            #?(:clj  [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])))

;; Flow-control
(def ^:export always (constantly true))

(defn ^:export not-nil?
  "flow-control - "
  [event old-seg seg all-new]
  (not (nil? seg)))

(defn ^:export valid?
  "flow-control - "
  [event old-seg seg all-new spec]
  (s/valid? spec seg))

;; tasks
(defn ^:export render-match [{:as seg}]
;;   (log/debug (str "rendering segment with keys" (keys seg)))
  (match
    [seg]
    [{:transactions transactions}] [:p (str "TXS: " (pr-str transactions))]
    [{:type :datom :eav [e a v]}] [:p (str "EAV [" e " " a " " v "]")]
    [{:type :instance-sides :v v}] [:p (str "SIDES: " v)]
    [{:type :instance-shape :v v}] [:p (str "SHAPE: " v)]
    [{:type :instance-4sides}] [:p "QUAD"]
    :else [:div "fail"]))

(defn ^:export datoms-task [{:as segment :keys [transactions]}]
  (for [[e a v] transactions]
    {:type :datom
     :eav [e a v]}))

(defn ^:export rule1-task [{:as segment [e a v] :eav}]
  (when (= :sides a)
    {:type :instance-sides
     :e e
     :v v}))

(defn ^:export rule2-task [{:as segment [e a v] :eav}]
  (when (= :shape a)
    {:type :instance-shape
     :e e
     :v v}))

(defn ^:export rule3-task [{:as segment :keys [e v]}]
  (when (= 4 v)
    {:type :instance-4sides
     :e e}))

(defn ^:export q1-task [{:as segment :keys [e v]}]
  segment)

(defn ^:export q2-task [{:as segment :keys [e v]}]
  segment)
