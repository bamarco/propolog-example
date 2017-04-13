(ns propolog-example.svg
  (:require [taoensso.timbre :as log]
            [clojure.spec :as s]
            [propolog-example.flui :as flui]
;;             #?(:cljs [vega-tools.core :refer [validate-and-parse]])
;;             #?(:cljs [cljsjs.vega])
            #?(:clj [propolog-example.utils :refer [educe xfn ppr-str]]
               :cljs [propolog-example.utils :refer [educe ppr-str] :refer-macros [xfn]])
            #?(:clj [clojure.math.numeric-tower :refer [sqrt]])
            #?(:clj  [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])))


#?(:cljs (def sqrt (.-sqrt js/Math)))

(defonce scale 2.0)
(defonce shape-d (* scale 20))

(defn point->js-str [point]
  (apply str (interpose "," point)))

(defn points->js-str [points]
  ;; !!!: Security. Just building js strings is this okay???
  (apply str (interpose " " (map point->js-str points))))

(defn ->svg [{:as svg :keys [points]}]
  (let [svg (into svg {:points (points->js-str points)})
        tag (:svg/type svg)
        body (:svg/body svg)
        opts svg;;(select-keys svg (:svg/keys svg))
        ]
  [tag opts body]))

(defn +bounding-box []
  (xfn [{:as svg :keys [width height padding]}]
       (into
         svg
         {:bound-width (+ width padding)
          :bound-height (+ height padding)
          :x (+ (/ padding  2))
          :y (+ (/ padding 2))})))

(defn +r []
  (xfn [{:as svg :keys [width]}]
    (assoc svg :r (/ width 2))))

(defn +center []
  (xfn [{:as svg :keys [x y cx cy width height]}]
    (into
      svg
      {:cx (or cx (+ x (/ width 2)))
       :cy (or cy (+ y (/ height 2)))})))

(defn +xy []
  (xfn [{:as svg :keys [x y cx cy width height]}]
    (into
      svg
      {:x (or x (- cx width))
       :y (or y (- cy height))})))

(defn +fill [color]
  (xfn [svg]
    (assoc svg :fill color)))

(defn +normalize []
  (xfn [{:as svg :keys [width]}]
         (assoc svg :height width)))

(defn +shape [tag]
  (xfn [svg] (assoc svg :svg/type tag)))

(defn +circumscribe-equi-tri []
  (comp
    (+center)
    (+r)
    (xfn [{:as svg :keys [cx cy r]}]
         (let [d (* 2 r)
               a [cx (- cy (/ (* (sqrt 3) r) 3))]
               b [(- cx r) (+ cy (/ (* (sqrt 3) d) 6))]
               c [(+ cx r) (+ cy (/ (* (sqrt 3) d) 6))]]
           (assoc svg :points [a b c])))))

(defn +zebra [& colors]
  (fn [step]
    (let [mod-current (volatile! 0)]
    (fn
      ([] (step))
      ([acc] (step acc))
      ([acc element]
        (let [modi @mod-current]
        (if [modi < (count colors)]
          (reset! mod-current (inc modi))
          (reset! mod-current 0))
          ;; ???: How should we handle differences between underlying container?
          (step acc (assoc-in element
                              [:fill] ;; svg
                              ;; [:style :background-color] ;; div
                              (get colors modi)))))))))

(defn x>rect []
  (comp
    (+xy)
    (+bounding-box)
    (+shape :rect)))


(defn x>square []
  (comp
    (+normalize)
    (x>rect)))

(defn x>circle []
  (comp
    (+normalize)
    (+bounding-box)
    (+center)
    (+r)
    (+shape :circle)))

(defn x>circumscribed-equi-tri []
  (comp
    (+normalize)
    (+bounding-box)
    (+circumscribe-equi-tri)
    (+shape :polygon)))

(defn x>number-text [n]
  (comp
    (+normalize)
    (+bounding-box)
    (+center)
    (+r)
    (xfn [{:as svg :keys [cx cy r]}]
         (into
           svg
           {:svg/type :text
            :svg/body n
            :x cx ;; ???: calc (- cx text-width) need to do this without using a js str
            :y cy
            :font-size r
            }
           ))))

(def dark-outline
  (xfn [svg]
    (into
      svg
      {:stroke "black"
       :stroke-width 3})))
(def no-outline
  (xfn [svg]
       (assoc svg :stroke :none)))

(def v-shape {:x 0 :y 0 :width shape-d :height shape-d :padding 5})

(def v-circle
  (educe
    (comp
      (x>circle)
      (+fill "magenta")
      dark-outline)
    v-shape))

(def v-square
  (educe
    (comp
      (x>square)
      (+fill "LightGreen")
      dark-outline)
    v-shape))

(def v-rect
  (educe
    (comp
      (xfn [{:as svg :keys [height]}]
           (assoc svg :height (/ (* 2 height) 3)))
      (x>rect)
      (+fill "cyan")
      dark-outline)
    v-shape))

(def v-triangle
  (educe
    (comp
      (x>circumscribed-equi-tri)
      (+fill "cornsilk")
      dark-outline)
    v-shape))

(def v-quad
  [v-square
  (educe
    (comp
      (x>number-text 4)
      (+fill "white")
      no-outline)
    v-shape)])

(def padding 5)

(defn svg-orphan [{:as svg :keys [bound-width bound-height]} & more]
  (flui/box
    :child
    (into [:svg {:width bound-width :height bound-height} (->svg svg)]
          (map ->svg more)
          )))

(defn sides->shape [sides]
  (case sides
    1 :loop
    2 :error
    3 :tri
    4 :quad
    :poly))

;; tasks
(defn ^:export render-match
    ([] [])
    ([dom] (flui/h-box :children dom))
    ([dom seg]
     ;; FIXME: I definitely am doing this wrong. I need to figure out which parts need to be a transducer and which need to be the reducing function. You can tell by the travesty of a call that is in sim.cljc: (render (reduce render (render) outputs))
     ;; ???: how do we match containers to representations that go together. It seems like the container is the accumulation and the representations are the stream of inputs.
     ;; ???: how do we have a tree of transducers. Wait that sounds like the compute-graph. Somehow we need to be able to control the path the render should follow, maybe?
     ;; ???: how do we integrate transducers with onyx. Draegalus said that feature was coming soon in talk, is NOW soon?
;;      (log/debug "rendering segment with keys" dom seg)
     (conj dom
           (match
             [seg]
             [{:transactions transactions}] (flui/box :child [:p (str "TXS: " (pr-str transactions))])
             [{:type :datom :eav [e a v]}] (flui/box :child [:p (str "EAV [" e " " a " " v "]")])
             [{:type :instance-sides :v v}] (flui/box :child [:p (str "SIDES: " v)])
             [{:type :instance-shape :v :square}] (svg-orphan v-square)
             [{:type :instance-shape :v :circle}] (svg-orphan v-circle)
             [{:type :instance-shape :v :rect}] (svg-orphan v-rect)
             [{:type :instance-shape :v :triangle}] (svg-orphan v-triangle)
             [{:type :instance-shape :v v}] (flui/box :child [:p (str "SHAPE: " v)]) ;; TODO: make a generic shape with a label equal to the keyword v
             [{:type :instance-4sides}] (apply svg-orphan v-quad)
             :else (flui/box :child [:p "fail"])))))

(defn ^:export render-match2
    ([seg]
     ;; FIXME: I definitely am doing this wrong. I need to figure out which parts need to be a transducer and which need to be the reducing function. You can tell by the travesty of a call that is in sim.cljc: (render (reduce render (render) outputs))
     ;; ???: how do we match containers to representations that go together. It seems like the container is the accumulation and the representations are the stream of inputs.
     ;; ???: how do we have a tree of transducers. Wait that sounds like the compute-graph. Somehow we need to be able to control the path the render should follow, maybe?
     ;; ???: how do we integrate transducers with onyx. Draegalus said that feature was coming soon in talk, is NOW soon?
;;      (log/debug "rendering segment with keys" dom seg)
           (match
             [seg]
             [{:type :instance-shape :v :square}] v-square
             [{:type :instance-shape :v :circle}] v-circle
             [{:type :instance-shape :v :rect}] v-rect
             [{:type :instance-shape :v :triangle}] v-triangle
             [{:type :instance-4sides}] v-quad
             :else {:render/type :unmatched
                    :seg seg})))
