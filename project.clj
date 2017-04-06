(defproject propolog-example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;;[org.clojure/clojure "1.9.0-alpha7"]
                 [org.clojure/clojurescript "1.9.494"] ;; ???: :scope "provided"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [org.clojure/core.async "0.3.442"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.2.0"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [bk/ring-gzip "0.1.1"]
                 [ring-logger-timbre "0.7.5"]
                 [compojure "1.5.0"]
                 [environ "1.0.3"]
                 [reagent "0.6.0-rc"]
                 [org.onyxplatform/onyx-local-rt "0.10.0.0-beta8"]
                 [com.taoensso/timbre "4.8.0"]
                 [com.taoensso/sente "1.11.0"]
                 [datascript "0.15.5"]
                 [posh "0.5.5"]
                 [com.cemerick/friend "0.2.3"]
                 [re-frame "0.9.2"]
                 [re-com "0.9.0"]
                 [cljs-http "0.1.42"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.rpl/specter "1.0.0"]

                 ;; Vega
;;                  [metosin/vega-tools "0.2.0"]
;;                  [cljsjs/vega "2.3.1-0"]
;;                  [funcool/promesa "1.8.0"]
;;                  [d4 "0.1.0-SNAPSHOT"]
;;                  [cljsjs/d3 "4.3.0-4"]

                 ;; kills clojure 1.9 warnings
                 [medley "0.8.2"]
                 ]

  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-environ "1.0.3"]]

  :min-lein-version "2.6.1"

  :source-paths ["src/clj" "src/cljs" "src/cljc"]

  :test-paths ["test/clj" "test/cljc"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js"]

  :uberjar-name "propolog-example.jar"

  ;; Use `lein run` if you just want to start a HTTP server, without figwheel
  :main propolog-example.server

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (run) and
  ;; (browser-repl) live.
  :repl-options {:init-ns user}

  :cljsbuild {:builds
              [{:id "app"
                :source-paths ["src/cljs" "src/cljc"]

                :figwheel true
                ;; Alternatively, you can configure a function to run every time figwheel reloads.
                ;; :figwheel {:on-jsload "propolog-example.core/on-figwheel-reload"}

                :compiler {:main propolog-example.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/propolog_example.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}

               {:id "test"
                :source-paths ["src/cljs" "test/cljs" "src/cljc" "test/cljc"]
                :compiler {:output-to "resources/public/js/compiled/testable.js"
                           :main propolog-example.test-runner
                           :optimizations :none}}

               {:id "min"
                :source-paths ["src/cljs" "src/cljc"]
                :jar true
                :compiler {:main propolog-example.core
                           :output-to "resources/public/js/compiled/propolog_example.js"
                           :output-dir "target"
                           :source-map-timestamp true
                           :optimizations :advanced
                           :pretty-print false}}]}

  ;; When running figwheel from nREPL, figwheel will read this configuration
  ;; stanza, but it will read it without passing through leiningen's profile
  ;; merging. So don't put a :figwheel section under the :dev profile, it will
  ;; not be picked up, instead configure figwheel here on the top level.

  :figwheel {;; :http-server-root "public"       ;; serve static assets from resources/public/
             ;; :server-port 3449                ;; default
             ;; :server-ip "127.0.0.1"           ;; default
             :css-dirs ["resources/public/css"]  ;; watch and update CSS

             ;; Instead of booting a separate server on its own port, we embed
             ;; the server ring handler inside figwheel's http-kit server, so
             ;; assets and API endpoints can all be accessed on the same host
             ;; and port. If you prefer a separate server process then take this
             ;; out and start the server with `lein run`.
             :ring-handler user/http-handler

             ;; Start an nREPL server into the running figwheel process. We
             ;; don't do this, instead we do the opposite, running figwheel from
             ;; an nREPL process, see
             ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl
             ;; :nrepl-port 7888

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             :server-logfile "log/figwheel.log"}

  :doo {:build "test"}

  :profiles {:dev
             {:dependencies [[figwheel "0.5.9"]
                             [figwheel-sidecar "0.5.9"]
                             [com.cemerick/piggieback "0.2.1"]
                             [org.clojure/tools.nrepl "0.2.12"]]

              :plugins [[lein-figwheel "0.5.9"]
                        [lein-doo "0.1.6"]]

              :source-paths ["dev"]
              :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}

             :uberjar
             {:source-paths ^:replace ["src/clj" "src/cljc"]
              :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
              :hooks []
              :omit-source true
              :aot :all}})
