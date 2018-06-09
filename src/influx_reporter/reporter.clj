(ns influx-reporter.reporter
  (:require [clojure.string :as s])
  (:import [java.util.concurrent TimeUnit]
           [influx_reporter ValuesResolver]
           [com.codahale.metrics
            Metric
            MetricRegistry
            MetricFilter
            ScheduledReporter]))

(defn- serialize-measure
  "Serialize measurement map to influxdb line."
  [measure])

(defn- -influx-url [influx-spec])

(def influx-url
  "Build influx write url according to spec."
  (memoize -influx-url))

(defn- send-data
  "Write lines of measurements to influxdb."
  [influx-spec & lines]
  (println influx-spec lines))

(defn- resolve-metric [{:keys [default-tags
                               values-resolver
                               tags-resolver]}
                       ^String name
                       ^Metric metric]
  (let [values (values-resolver metric)]
    (when-not (empty? values)
      (let [[measurement tags] (tags-resolver name)]
        {:measurement measurement
         :tags (merge default-tags tags)
         :values values}))))

(defn- send-report [opts & metrics]
  (->> metrics
       (mapcat
        (fn [ms]
          (map (fn [[k metric]]
                 (resolve-metric opts k metric))
               ms)))))

(defn ^ScheduledReporter reporter
  [& {:keys [^MetricRegistry registry
             ^MetricFilter metric-filter
             rate-unit duration-unit]
      :or {metric-filter MetricFilter/ALL
           rate-unit TimeUnit/SECONDS
           duration-unit TimeUnit/MILLISECONDS}
      :as opts}]
  (proxy [ScheduledReporter]
      [registry "influx-reporter" metric-filter
       rate-unit duration-unit]
    (report
      ([gauges counters histograms meters timers]
       (send-report opts
                    gauges
                    counters
                    histograms
                    meters
                    timers))
      ([]
       (send-report opts
                    (.getCounters   registry metric-filter)
                    (.getGauges     registry metric-filter)
                    (.getMeters     registry metric-filter)
                    (.getTimers     registry metric-filter)
                    (.getHistograms registry metric-filter))))))

(def reg (MetricRegistry.))

(defn stat-item []
  (.inc (.counter reg "my-count"))
  (.update (.histogram reg "my-histo") (rand-int 10))
  (.mark (.meter reg "my-meter")))

(comment
  (reporter
   :registry ""
   :influx-spec {
                 :influx-url ""
                 :influx-db ""
                 }
   :metric-filter MetricFilter/ALL
   :rate-unit TimeUnit/SECONDS
   :duration-unit TimeUnit/MILLISECONDS
   :tags-resolver identity
   :values-resolver identity
   :tags {}))


(comment
  (proxy [ValuesResolver] []
      (resolve-timer [m] {:a 42})))
