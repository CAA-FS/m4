(defproject marathon "4.0.9-SNAPSHOT"
  :description "An Integrated Suite of Rotational Analysis Tools."
  :dependencies [[org.clojure/clojure "1.7.0"]
;                 [org.clojure.contrib/standalone "1.3.0-alpha4"]
                 [spork "0.1.9.7-SNAPSHOT"]
                 [piccolotest  "0.1.0-SNAPSHOT"]
                 [com.taoensso/nippy "2.11.0-RC1"] ;temporarily added to tes serialization.
                 ;;temporarily added to explore possible uses of inference...
                 [datascript "0.15.0"] 
                 [org.clojure/core.logic "0.8.10"]
                 ]
  :jvm-opts ^:replace ["-Xmx1000m" "-XX:NewSize=200m"]
  :profiles {:uberjar {:aot  [marathon.main]
                       :main  marathon.main
                       :jvm-opts ^:replace ["-Xmx1000m" "-XX:NewSize=200m" "-server"]
                       }
             :publish [:uberjar
                       {:aot [spork.util.reducers
                              spork.cljgui.components.PaintPanel
                              spork.cljgui.components.swing
                              spork.util.table
                              spork.util.metaprogramming                              
                              marathon.ces.core
                              marathon.serial 
                              marathon.core]}]})

