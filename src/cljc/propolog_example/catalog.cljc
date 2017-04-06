(ns propolog-example.catalog
  (:require [taoensso.timbre :as log]
            [clojure.spec :as s]))

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
