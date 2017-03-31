(ns propolog-example.catalog
  (:require [taoensso.timbre :as log]
            [clojure.spec :as s]
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
(defn ^:export render-match [{:as segment :keys []}]
;;   (match [segment]

;;      )
  )

(defn ^:export datoms-task [{:as segment :keys [transactions]}]
  (for [datom transactions]
    {:datom datom}))

(defn ^:export rule1-task [{:as segment :keys [datom]}]
  (let [[e a v] datom]
    (when (= :sides a)
      {:instance [e v]})))

(defn ^:export rule2-task [{:as segment :keys [datom]}]
  (let [[e a v] datom]
    (when (= :shape a)
      {:instance [e v]})))

(defn ^:export rule3-task [{:as segment :keys [instance]}]
  (let [[e v] instance]
    (when (= 4 v)
      {:instance [e]})))

(defn ^:export q1-task [{:as segment :keys [instance]}]
  segment)

(defn ^:export q2-task [{:as segment :keys [instance]}]
  segment)
