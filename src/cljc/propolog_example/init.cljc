(ns propolog-example.init
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require
    [re-frame.core :as rf]
    [datascript.core :as d]
    [propolog-example.utils :as utils]
    #?(:cljs [posh.reagent :as posh])
    #?(:cljs [cljs.core.async :refer [<! chan]])
    #?(:cljs [cljs-http.client :as http]) ;; TODO: switch to sente
    ))

(defn init []
  (let [conn (d/create-conn {:location {:db/type :db.type/ref}})]
    #?(:cljs (posh/posh! conn))
    #?(:cljs
        (go (let [response (<! (http/get "propolog-example.edn"))]
              ;; FIXME: Transacting directly in an event handler is bad. Change to the other kind of fx handler.
              ;; ???: go block probably also bad.
              (d/transact! conn (:body response))))
        :clj (first (utils/edn-read-file "resources/public/Resume.edn")))
    (rf/reg-sub :propolog-example.sub/conn (fn [] conn))
    (rf/dispatch [:propolog-example.event/init])))
