(ns propolog-example.repl
  (:require ;;[taoensso.timbre :as log]
;;             [propolog-example.utils :as utils :refer [ppr-str]]
            [propolog-example.catalog :as c]
;;             #?(:clj [clojure.core.async :refer [<! chan go]]
;;                :cljs [clojure.core.async :refer [<! chan]])
            )
;;   #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  #?(:clj (:gen-class)))

#?(:cljs (enable-console-print!))

(defn gen-transactions []
  {:transactions
   #{[42 :shape :triangle]
     [42 :sides 3]
     [43 :shape :square]
     [43 :sides 4]
     [44 :shape :rect]
     [44 :sides 4]
     [45 :shape :circle]
     [45 :sides 1]
     }})

(comment
{:onyx.core/workflow
  [[:in :datoms]
   [:datoms :rule1] [:rule1 :rule3]
   [:datoms :rule2]
   [:rule3 :q1]
   [:rule2 :q2]
   [:q1 :render]
   [:q2 :render]]})

(defn wrap-task [task]
  (fn [seg-or-segs]
    (if (map? seg-or-segs)
      (task seg-or-segs)
      (map task seg-or-segs))))

(defn start [transactions]
  (let [path1
        ((comp
           (partial remove nil?)
           (wrap-task c/q1-task)
           (wrap-task c/rule3-task)
           (wrap-task c/rule1-task)
           (wrap-task c/datoms-task))
         transactions)
        path2
        ((comp
           (partial remove nil?)
           (wrap-task c/q2-task)
           (wrap-task c/rule2-task)
           (wrap-task c/datoms-task))
         transactions)]
    ;; a little clunky right now. will look more similar to onyx when we switch to transducers
    (str "path1: " (pr-str path1)
         "path2: " (pr-str path2))))

(defn run []
  (prn
  (start (gen-transactions))))
