(ns propolog-example.vega
  (:require [taoensso.timbre :as log]
            [propolog-example.onyx :as onyx]
            [propolog-example.flui :as flui]
;;             #?(:cljs [vega-tools.core :refer [validate-and-parse]])
;;             #?(:cljs [cljsjs.vega])
;;             #?(:cljs [promesa.core :as p])
;;             #?(:cljs [d4.core :as d4])
;;             #?(:cljs [cljsjs.d3])
            ))

;; (defn gmap-inner []
;;   (let [gmap    (atom nil)
;;         options (clj->js {"zoom" 9})
;;         update  (fn [comp]
;;                   (let [{:keys [latitude longitude]} (reagent/props comp)
;;                         latlng (js/google.maps.LatLng. latitude longitude)]
;;                     (.setPosition (:marker @gmap) latlng)
;;                     (.panTo (:map @gmap) latlng)))]

;;     (reagent/create-class
;;       {:reagent-render (fn []
;;                          [:div
;;                           [:h4 "Map"]
;;                           [:div#map-canvas {:style {:height "400px"}}]])

;;        :component-did-mount (fn [comp]
;;                               (let [canvas  (.getElementById js/document "map-canvas")
;;                                     gm      (js/google.maps.Map. canvas options)
;;                                     marker  (js/google.maps.Marker. (clj->js {:map gm :title "Drone"}))]
;;                                 (reset! gmap {:map gm :marker marker}))
;;                               (update comp))

;;        :component-did-update update
;;        :display-name "gmap-inner"})))



;; (defn gmap-outer []
;;   (let [pos (subscribe [:current-position])]   ;; obtain the data
;;     (fn []
;;       [gmap-inner @pos])))

;; #?(:cljs
;; (defn process-vega [element-id spec]
;;   (-> (validate-and-parse spec)
;;       (p/catch #(log/debug "Unable to parse spec:\n" %))
;;       (p/then #(-> (% {:el (js/document.getElementById element-id)
;;                        })
;;                    (.update))))))

;; #?(:cljs
;; (defn hello-vega* []
;;   (let [vega (r/atom {})
;;         spec {:width 200 :height 200
;;               :marks [{:type "symbol"
;;                        :properties {:enter {:size {:value 1000}
;;                                             :x {:value 100}
;;                                             :y {:value 100}
;;                                             :shape {:value "circle"}
;;                                             :stroke {:value "red"}}}}]}
;;         ]
;;     #?(:cljs
;;         (r/create-class
;;           {:reagent-render (fn []
;;                              [:div
;;                               [:h2 "Vega Chart"]
;;                               [:div#chart]])
;;            :component-did-mount (fn [c]
;;                                   (log/debug "#chart is "
;;                                              (js/document.getElementById "chart"))
;;                                   (process-vega "chart" spec)
;; ;;                                   (-> (validate-and-parse spec)
;; ;;                                       (p/catch #(log/debug "Unable to parse spec:\n" %))
;; ;;                                       (p/then #(-> (% {:el (js/document.getElementById "chart")
;; ;;                                                        })
;; ;;                                                    (.update))))
;;                                   )
;;            :component-did-update #(let [tasks (r/props %)]
;;                                     (log/debug (r/props %)))
;;            :display-name "hello-vega"})))))

;; (defn hello-vega [boo]
;;   #?(:cljs [hello-vega* boo]
;;      :clj  [:div#chart]))

;; (defn nodes-graph [sim]
;; #?(:clj
;;     [:div "cljc error"]
;;    :cljs
;;     (let [{dispatch :onyx.sim/dispatch
;;            listen :onyx.sim/listen} (deref-or-value sim)
;;           tester (d3.hierarchy (clj->js
;;                    {:name :giggles
;;                     :children [{:name :b
;;                                 :children [{:name :bear}]}
;;                                {:name :c}]}))
;;           ]
;;       (js/console.log "t-before" tester)
;;       (js/console.log "t-after" ((.size (js/d3.cluster) #js [200 200]) tester))
;;       [:svg.nodes-graph
;;        (str
;; ;;          (-> js/d3
;; ;;              (js/d3.data tester)
;; ;;              (js->clj )
;;          )
;; ;;        (str (d4/layout {:d4/nodes tester
;; ;;                         :d4/type :d4.hierarchy/tree
;; ;;                         :d4/tree {:d4.hierarchy/size #js [200 200]}
;; ;;                         }))

;;        ])))
