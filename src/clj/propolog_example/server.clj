(ns propolog-example.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.logger.timbre :as logger.timbre]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [hiccup.core :as hiccup]
            [hiccup.form :as form]
            [hiccup.page :as page]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :refer [hash-bcrypt bcrypt-verify bcrypt-credential-fn]]
            [cemerick.friend.workflows :refer [make-auth interactive-form]]
            [propolog-example.gui :as gui]
            [propolog-example.init :as init])
  (:gen-class))

(def username->user
  {"gandalf" {:user "gandalf"
              :name "Gandalf the Grey"
              :identity 1001
              :roles #{::wizard}
              :password (hash-bcrypt "friend")}})

(defn login [req]
  (hiccup/html
    (form/form-to
      {;;:enctype :multipart/form-data
        } [:post "/login"]
      [:div
       ;;       (anti-forgery-field)
       [:span "Username:"]
       (form/text-field {} :username)]
      [:div
       [:span "Password:"]
       (form/password-field {} :password)]
      [:div
       (form/submit-button {} "Login")
       ])))

(defn index [js?]
  (fn [req]
  (page/html5
     [:head
      [:title "Propolog Example"]
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1"}]
      (page/include-css "css/re-com.css")
      (page/include-css "http://fonts.googleapis.com/css?family=Arvo")
      (page/include-css "http://fonts.googleapis.com/css?family=Work+Sans")
;;       (include-css "css/hljs-rainbow.css")
      (page/include-css "css/style.css")
      ]
     (into
       [:body]
      (if js?
        [[:div#app-hook] (page/include-js "js/compiled/propolog_example.js")]
        [[:div#app-hook (gui/root)]]))
      )))

(defroutes routes
  (GET "/" [] (index true))
  (GET "/no-js" [] (index false))
  (GET "/login" [] login)
  (GET "/mines-of-moria" [] (friend/authorize #{::wizard} "Fly, you fools!"))
  (resources "/"))

(def http-handler
  (do (init/init)
    (-> routes
        (friend/authenticate {:workflows [(interactive-form)]
                              :credential-fn (partial bcrypt-credential-fn username->user)})
        (wrap-keyword-params)
        (wrap-params)
        (wrap-session)
        ;; FIXME: (anti-forgery/wrap-anti-forgery)
        (wrap-defaults api-defaults)
        logger.timbre/wrap-with-logger
        wrap-gzip)))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (run-jetty http-handler {:port port :join? false})))
