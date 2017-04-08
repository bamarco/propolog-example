(ns propolog-example.naive
;;   (:require
;;             )
)

(defonce
  slide-7
  [{:type :Actor
    :id 344759
    :first-name "Douglas"
    :last-name "Fowley"}
   {:type :Casts
    :actor 344759
    :movie 29851}
   {:type :Casts
    :actor 355713
    :movie 29000}
   {:type :Movie
    :id 7909
    :title "A Night in Armour"
    :year 1910}
   {:type :Movie
    :id 29000
    :title "Arizona"
    :year 1940}
   {:type :Movie
    :id 29445
    :title "Ave Maria"
    :year 1940}])

(defn Q1-xf [?title]
  (comp
    (filter (fn [{:keys [type year]}]
              (and (= type :Movie) (= year 1940))))
    (map (fn [{:keys [title]}]
           {?title title}))))

(defn movies-in-1940 [?m]
  (filter (fn [{:keys [type year]}]
            (and (= type :Movie) (= year 1940))))
  (map (fn [{:keys [id]}]
           {?m id})))

;; (defn Q2-xf [?first ?last]
;;   (filter (fn [{:keys [type year]}]
;;             (and (= type :Actor) (= year 1940))))
;;   (map (fn [{:keys [first-name last-name]}]
;;          {?first first-name
;;           ?last last-name})))

(def Q1 '{:find [?title]
          :where [{:type :Movie :title ?title :year ?year}
                  {?year 1940}]})
(def Q2 `{:find [?first ?last]
          :where [{:type :Actor :id ?a :first-name ?first :last-name ?last}
                  {:type :Casts :actor ?a :movie ?m}
                  {:type :Movie :id ?m :year 1940}]})



