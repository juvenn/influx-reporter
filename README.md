# influx-reporter

[![Build Status](https://travis-ci.org/juvenn/influx-reporter.svg?branch=master)](https://travis-ci.org/juvenn/influx-reporter)
[![Clojars Project](https://img.shields.io/clojars/v/influx-reporter.svg)](https://clojars.org/influx-reporter)

A [metrics](https://github.com/dropwizard/metrics) to influxdb reporter, that supports customizing
how and what fields of metric to collect.

## Usage

```clojure
(require '[influnx-reporter.reporter :as rep])

(def reg (MetricRegistry.))

(def my-influx {:url "http://127.0.0.1:8086/"
                :db "reporter"})

(defn- my-histogram-resolver [histogram]
  {:p98 (.get98thPercentile histogram)})

(def my-reporter
  (reporter :registry reg
            :influx-spec my-influx
            :histogram-resolver my-histogram-resolver
            :default-tags {:host "air.local"}))

;; report to influxdb every 5 mins
(rep/start my-reporter 300)
```


### Features not supported yet

* No http auth for InfluxDB
* No https for InfluxDB

## License

Copyright © 2018 LeanCloud

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
