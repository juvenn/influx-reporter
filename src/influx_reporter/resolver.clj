(ns influx-reporter.resolver
  "Resolve Metric into interested measurement."
  (:require [clojure.string :as s])
  (:import [java.util.concurrent TimeUnit]
           [com.codahale.metrics Metric Clock
            Counter Gauge Meter Histogram Timer]))

(defn dotted-tag-resolver
  "Resolve dotted metric name as measurement and tags.
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
  "Returns an fn that convert per-second rate vaule into other unit."
  [^TimeUnit unit]
  (fn [v]
    (* v (.toSeconds unit 1))))

(defn duration-scale
  "Returns an fn that convert duration value in ns into other unit."
  [^TimeUnit unit]
  (fn [v]
    (/ v (.toNanos unit 1) 1.0)))

(def rate->s identity)
(def duration->ms (duration-scale TimeUnit/MILLISECONDS))

(defn resolve-counter [^Counter counter]
  {:count (.getCount counter)})

(defn resolve-gauge [^Gauge gauge]
  {:value (.getValue gauge)})

(defn resolve-meter
  "Resolve Meter values. Meter rate is measured in per second by default."
  [^Meter meter]
  {:rate_1m  (rate->s (.getOneMinuteRate meter))
   ;; :rate_5m  (rate->s (.getFiveMinuteRate meter))
   ;; :rate_15m  (rate->s (.getFifteenMinuteRate meter))
   ;; :rate_mean  (rate->s (.getMeanRate meter))
   :count    (.getCount    meter)})

(defn resolve-timer
  "Resolve Timer values. Timer is a combination of Meter and Histogram.
  the rate is measured in per second, while duration is measured in ns,
  of which we scale it to ms."
  [^Timer timer]
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

(defn resolve-histogram [^Histogram histogram]
  (let [snapshot (.getSnapshot histogram)]
    {:count (.getCount histogram)
     :mean  (.getMean           snapshot)
     :min   (.getMin            snapshot)
     :max   (.getMax            snapshot)
     :p50   (.getMedian         snapshot)
     :p75   (.get75thPercentile snapshot)
     :p95   (.get95thPercentile snapshot)
     :p99   (.get99thPercentile snapshot)
     ;; :p98   (.get98thPercentile snapshot)
     :p999  (.get999thPercentile snapshot)}))

(defn resolve-values
  [{:keys [counter-resolver gauge-resolver meter-resolver
           timer-resolver histogram-resolver]
    :or {counter-resolver resolve-counter
         gauge-resolver resolve-gauge
         meter-resolver resolve-meter
         timer-resolver resolve-timer
         histogram-resolver resolve-histogram}}
   ^Metric metric]
  (condp instance? metric
    Counter   (counter-resolver metric)
    Gauge     (gauge-resolver metric)
    Meter     (meter-resolver metric)
    Timer     (timer-resolver metric)
    Histogram (histogram-resolver metric)
    :else {}))

(defonce clock (Clock/defaultClock))

(defn get-clock-ms []
  (.getTime clock))

(defn resolve-measurement
  "Resolve metric to measurement."
  [{:keys [default-tags
           tags-resolver]
    :or {tags-resolver dotted-tag-resolver}
    :as opts}
   ^String name
   ^Metric metric]
  (let [values (resolve-values opts metric)]
    (when (seq values)
      (let [[measurement tags] (tags-resolver name)
            ;; timestamp in s
            ts (quot (get-clock-ms) 1000)]
        {:measurement measurement
         :tags (merge default-tags tags)
         :values values
         :ts ts}))))

