(defproject influx-reporter "0.1.0-SNAPSHOT"
  :description "Report metrics to influxdb"
  :url "https://github.com/juvenn/influx-reporter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot [influx-reporter.resolver]
  :dependencies [[io.dropwizard.metrics/metrics-core "4.0.2"]
                 [org.clojure/clojure "1.9.0"]])

