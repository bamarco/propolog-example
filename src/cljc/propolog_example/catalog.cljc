(ns propolog-example.catalog
  (:require [taoensso.timbre :as log]))

(defn ^:export datoms-task [{:as segment :keys [transactions]}]
  (for [datom transactions]
    {:datom datom}))

(defn ^:export rule1-task [{:as segment :keys [datom]}]
  (let [[e a v] datom]
    (log/debug "rule1 attr" a)
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
