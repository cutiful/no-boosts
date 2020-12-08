(defproject no-boosts.core "0.1.0"
  :description "A tool to show a fediverse user's posts excluding boosts"
  :url "https://no-boosts.glitch.me"
  :license {:name "Common Development and Distribution License 1.0"
            :url "https://spdx.org/licenses/CDDL-1.0.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [cljs-ajax "0.8.0"]
                 [enfocus "2.1.0"]]

  :source-paths ["src"]

  :aliases {"start" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "build" ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "no-boosts.test-runner"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.3"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   }})

