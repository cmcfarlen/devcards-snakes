(defproject snakes "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [devcards "0.2.0-8"]
                 [sablono "0.3.6"]
                 [org.omcljs/om "0.9.0"]
                 #_[reagent "0.5.1"]]

  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.4.1"]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "target"]

  :source-paths ["src"]

  :cljsbuild {
              :builds [{:id "devcards"
                        :source-paths ["src"]
                        :figwheel { :devcards true } ;; <- note this
                        :compiler { :main       "snakes.core"
                                    :asset-path "js/compiled/devcards_out"
                                    :output-to  "resources/public/js/compiled/snakes_devcards.js"
                                    :output-dir "resources/public/js/compiled/devcards_out"
                                    :source-map-timestamp true }}
                       {:id "dev"
                        :source-paths ["src"]
                        :figwheel true
                        :compiler {:main       "snakes.core"
                                   :asset-path "js/compiled/out"
                                   :output-to  "resources/public/js/compiled/snakes.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :source-map-timestamp true }}
                       {:id "prod"
                        :source-paths ["src"]
                        :compiler {:main       "snakes.core"
                                   :asset-path "js/compiled/out"
                                   :output-to  "resources/public/js/compiled/snakes.js"
                                   :optimizations :advanced}}
                       {:id "hosted"
                        :source-paths ["src"]
                        :compiler {:main "snakes.core"
                                   :devcards true
                                   :asset-path "js/compiled/out"
                                   :output-to "resources/public/js/compiled/snakes.js"
                                   :optimizations :advanced }}]}

  :figwheel { :css-dirs ["resources/public/css"]
              :nrepl-port 7888
              :repl true })

