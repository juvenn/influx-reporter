(ns influx-reporter.resolver
  "Resolve Metric into measurement.

  `ValuesResolver` serves as a default resolver to resolve Counter,
   Gauge, Meter, Histogram and Timer into values map. It collects only
   rate_1m of a Meter and Timer, and assumes rate-unit in seconds,
   while duration-unit in ms.

   It can be extended, for example if a rate_5m is desired:
     
   ```
   (proxy [ValuesResolver] []
    (resolveTimer [^Meter meter]
      {:rate_1m (.getOneMinuteRate meter)
       :rate_5m (.getFiveMinuteRate Meter)}))
   ```
  "
  (:require [clojure.string :as s])
  (:import [java.util.concurrent TimeUnit]
           [com.codahale.metrics Metric
            Counter Gauge Meter Histogram Timer])
  (:gen-class :name influx_reporter.ValuesResolver))

(defn dotted-tag-resolver
  "Resolve dotted metric name as influx measurement and tags.
   Returns measurement and tags pair.

   E.g.:
   cpu_load.tag1.value1.tag2.value2
   => [cpu_load {:tag1 :value1 :tag2 :value2}]
   my.cpu_load.tag1.value1.tag2.value2
   => [my.cpu_load {:tag1 :value1 :tag2 :value2}]
  "
  [^String name]
  (let [ks (s/split name #"\.")
        cnt (count ks)]
    (cond
      (= cnt 1) [name {}]
      (= cnt 2) [name {}]
      (even? cnt)
      [(s/join "." (take 2 ks))
       (apply assoc {} (drop 2 ks))]
      :else
      [(first ks)
       (apply assoc {} (rest ks))])))

(defn rate-scale
  "Returns an fn that convert per-second rate vaule into of unit."
  [^TimeUnit unit]
  (fn [v]
    (* v (.toSeconds unit 1))))

(defn duration-scale
  "Returns an fn that convert duration value in ns into of unit."
  [^TimeUnit unit]
  (fn [v]
    (/ v (.toNanos unit 1) 1.0)))

(def rate->s identity)
(def duration->ms (duration-scale TimeUnit/MILLISECONDS))

(defn -resolveCounter [this ^Counter counter]
  {:count (.getCount counter)})

(defn -resolveGauge [this ^Gauge gauge]
  {:value (.getValue gauge)})

(defn -resolveMeter [this ^Meter meter]
  ;; Only cut 1m rate , as 5m, 15m rate can be easily rollup in influx. 
  {:rate_1m  (rate->s (.getOneMinuteRate meter))
   ;; :rate_5m  (rate->s (.getFiveMinuteRate meter))
   ;; :rate_15m  (rate->s (.getFifteenMinuteRate meter))
   ;; :rate_mean  (rate->s (.getMeanRate meter))
   :count    (.getCount    meter)})

(defn -resolveHistogram [this ^Histogram histogram]
  (let [snapshot (.getSnapshot histogram)]
    {:count (.getCount histogram)
     :mean  (.getMean           snapshot)
     :min   (.getMin            snapshot)
     :max   (.getMax            snapshot)
     :p50   (.getMedian         snapshot)
     :p75   (.get75thPercentile snapshot)
     :p95   (.get95thPercentile snapshot)
     :p99   (.get99thPercentile snapshot)
     :p999  (.get999thPercentile snapshot)}))

(defn -resolveTimer [this ^Timer timer]
  ;; Timer by default record rate by seconds, and duration in
  ;; nanoseconds.
  (let [snapshot (.getSnapshot timer)]
    {:count (.getCount timer)
     :rate_1m  (rate->s (.getOneMinuteRate timer))
     ;; :rate_5m  (rate->s (.getFiveMinuteRate meter))
     ;; :rate_15m  (rate->s (.getFifteenMinuteRate meter))
     ;; :rate_mean  (rate->s (.getMeanRate meter))
     :mean  (duration->ms (.getMean           snapshot))
     ;; :stddev  (duration->ms (.getStdDev           snapshot))
     :min   (duration->ms (.getMin            snapshot))
     :max   (duration->ms (.getMax            snapshot))
     :p50   (duration->ms (.getMedian         snapshot))
     :p75   (duration->ms (.get75thPercentile snapshot))
     :p95   (duration->ms (.get95thPercentile snapshot))
     ;; :p98   (duration->ms (.get98thPercentile snapshot))
     :p99   (duration->ms (.get99thPercentile snapshot))
     :p999  (duration->ms (.get999thPercentile snapshot))}))

(defn -resolveValues [this ^Metric metric]
  (condp instance? metric
    Counter (.resolveCounter metric)
    Gauge   (.resolveGauge   metric)
    Meter   (.resolveMeter   metric)
    Timer   (.resolveTimer   metric)
    Histogram (.resolveHistogram metric)
    :else {}))
