(ns propolog-example.sub
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [propolog-example.utils :as utils]
            #?(:cljs [posh.reagent :as posh])
            #?(:cljs [reagent.core :refer [atom]])
            [datascript.core :as d]
            [propolog-example.onyx :as onyx]
            [propolog-example.catalog] ;; need these functions for onyx catalog
            ))

;;  ANCESTOR EXAMPLE
;;  [:find ?child ?ancestor
;;   :where [ancestor? ?child ?ancestor]
;;   :in $ %]
 ;; with rules
;;  [[(ancestor? ?child ?parent)
;;    [?child :parent ?parent]]
;;   [(ancestor? ?child ?ancestor)
;;    [?child :parent ?x]
;;    [ancestor? ?x ?ancestor]]]

(defn listen
  [query-v]
  @(rf/subscribe query-v))

(def q
  #?(:cljs (comp deref posh/q)
    :clj (fn [query conn & args]
           (apply d/q query @conn args))))

(def pull
  #?(:cljs (comp deref posh/pull)
    :clj (fn [conn expr eid]
           (d/pull @conn expr eid))))

;; NOTE: ::conn is registered in :init.cljc

(rf/reg-sub
  ::job-ids
  :<- [::conn]
  (fn [conn _]
    (q '[:find [?job]
         :in $
         :where
         [?job :onyx.sim/type :onyx.core/job]] conn)))

(rf/reg-sub
  ::jobs
  :<- [::conn]
  :<- [::job-ids]
  (fn [[conn job-ids] _]
    (for [id job-ids]
      (pull conn '[{:onyx.core/catalog [*]} *] id))))

(rf/reg-sub
  ::onyx-job
  :<- [::jobs]
  (fn [jobs _]
    (first jobs)
    ))

(rf/reg-sub
  ::propolog-env
  :<- [::conn]
  (fn [conn [_ sim-id]]
    (assoc
      (pull conn '[{:onyx.core/job [{:onyx.core/catalog [*]} *]} *] sim-id)
      :onyx.sim/dispatch rf/dispatch
      :onyx.sim/listen listen)))

(rf/reg-sub
  :onyx.sim/import-uri
  :<- [::conn]
  (fn [conn [_ sim-id]]
    (:onyx.sim/import-uri (pull conn '[:onyx.sim/import-uri] sim-id))))

(rf/reg-sub
  :onyx.sim/hidden-tasks
  :<- [::conn]
  (fn [conn [_ sim-id]]
    (:onyx.sim/hidden-tasks (pull conn '[:onyx.sim/hidden-tasks] sim-id))))

(rf/reg-sub
  :onyx.sim/env
  :<- [::conn]
  (fn [conn [_ sim-id]]
    (:onyx.sim/env (pull conn '[:onyx.sim/env] sim-id))))

(rf/reg-sub
  :onyx.core/job
  :<- [::conn]
  (fn [conn [_ sim-id]]
    (:onyx.core/job (pull conn '[{:onyx.core/job [{:onyx.core/catalog [*]} *]}] sim-id))))




