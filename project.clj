(defproject influx-reporter "0.1.0-SNAPSHOT"
  :description "InfluxDB reporter for dropwizard metrics"
  :url "https://github.com/juvenn/influx-reporter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[io.dropwizard.metrics/metrics-core "4.0.2"]
                 [clj-http "3.9.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.clojure/clojure "1.9.0"]])

