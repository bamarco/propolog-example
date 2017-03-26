(ns propolog-example.sub
  (:require [taoensso.timbre :as log]
            [re-frame.core :as rf]
            [propolog-example.utils :as utils]
            #?(:cljs [posh.reagent :as posh])
            #?(:cljs [reagent.core :refer [atom]])
            [datascript.core :as d]
            [onyx-local-rt.api :as onyx]
            [propolog-example.catalog] ;; need these functions for onyx catalog
            ))

(defn listen
  [query-v]
  @(rf/subscribe query-v))

(def q
  #?(:cljs (comp deref posh/q)
    :clj d/q))

(def pull
  #?(:cljs (comp deref posh/pull)
    :clj d/pull))

;; NOTE: ::conn is registered in :propolog.event/init

;; (rf/reg-sub
;;   ::ds-db
;;   :-> [::conn]
;;   (fn [conn _]
;;     #?(:clj @conn :cljs conn)))

;; (rf/reg-sub
;;   ::catalog
;;   :-> [::ds-db]
;;   (fn [db _]
;;     (utils/cat-into
;;       #{}
;;       (q
;;         '{:find [?c]
;;           :in $
;;           :where
;;           [?c :onyx/type :input]}
;;         db)
;;       (q
;;         '{:find [?c]
;;           :in $
;;           :where
;;           [?c :onyx/type :output]}
;;         db)
;;       (q
;;         '{:find [?c]
;;           :in $
;;           :where
;;           [?c :onyx/type :function]}
;;         db))))

(rf/reg-sub
  ::onyx-job
  (fn [db _]
    (:job db)))

(rf/reg-sub
  ::onyx-env
  (fn [db _]
    (:env db)))




