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
         [?job :propolog/type :onyx.core/job]] conn)))

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
  (fn [conn [_ env-id]]
    (pull conn '[{:onyx.core/job [*]} *] env-id)))

(rf/reg-sub
  :onyx.sim/hide-tasks
  :<- [::conn]
  (fn [conn [_ env-id]]
    (:onyx.sim/hide-tasks (pull conn '[:onyx.sim/hide-tasks] env-id))))

(rf/reg-sub
  :onyx.sim/env
  :<- [::conn]
  (fn [conn [_ env-id]]
    (:onyx.sim/env (pull conn '[:onyx.sim/env] env-id))))




