(ns influx-reporter.reporter
  (:require [clojure.string :as s]
            [clj-http.conn-mgr :refer [make-reusable-conn-manager]]
            [clj-http.client :as http]
            [influx-reporter.resolver :as res]
            [clojure.tools.logging :as log])
  (:import [java.util.concurrent TimeUnit]
           [com.codahale.metrics
            Metric MetricRegistry MetricFilter
            ScheduledReporter]))

(defn- write-url [{:keys [url db] :as spec}]
  (if (and url db)
    (str (if (s/ends-with? url "/")
           url
           (str url "/"))
         "write?"
         "db=" db
         "&"
         "precision=s")
    (throw (ex-info "Please set influx url and db." {}))))

(def influx-url
  "Build influx write url according to spec."
  (memoize write-url))

(def cm (make-reusable-conn-manager {}))

(defn send-data
  "Write lines of measurements to influxdb."
  [influx-spec & lines]
  (let [url (influx-url influx-spec)]
    (log/infof "Sending %s points to influxdb." (count lines))
    (loop [lines lines]
      (when-let [[xs ys] (split-at 100 lines)]
        (when-let [resp (try
                          (http/post url {:connection-manager cm
                                          :body (s/join "\n" xs)})
                          (catch clojure.lang.ExceptionInfo ex
                            (log/errorf "Send data to %s failed: %s"
                                        url (:body (ex-data ex))))
                          (catch Exception ex
                            (log/errorf ex "Send data to %s failed." url)))]
          (if (<= 200 (:status resp 0) 299)
            (if (seq ys)
              (recur ys)
              true)
            (log/errorf "Send data to %s failed: %s" url (:body resp))))))))

(comment
  (send-data {:url "http://127.0.0.1:8086/"
              :db "reporter"}
             "cpu_load,host=air.local value=0.6"))

(defn- map->line [m]
  (->> m
       (map (fn [[k v]] (str (name k) "=" v)))
       (s/join ",")))

(defn- serialize-measure
  "Serialize measurement map to influxdb line."
  [{:keys [measurement tags values ts]}]
  (when (seq values)
    (str measurement
         (when (seq tags)
          (str "," (map->line tags)))
         " "
         (map->line values)
         (when ts
           (str " " ts)))))

(defn- resolve-metric [opts [name metric :as map-entry]]
  (res/resolve-measurement opts name metric))

(defn send-report
  [{:keys [influx-spec] :as opts} & metrics]
  (->> metrics
       (mapcat #(map (partial resolve-metric opts) %))
       (map serialize-measure)
       (apply send-data influx-spec)))

(defn ^ScheduledReporter reporter
  "Build a ScheduledReporter that collect metrics and send to influxdb."
  [& {:keys [^MetricRegistry registry
             ^MetricFilter metric-filter
             ^TimeUnit rate-unit
             ^TimeUnit duration-unit
             ^clojure.lang.IFn tags-resolver
             ^clojure.lang.IFn counter-resolver
             ^clojure.lang.IFn gauge-resolver
             ^clojure.lang.IFn meter-resolver
             ^clojure.lang.IFn histogram-resolver
             ^clojure.lang.IFn timer-resolver
             default-tags]
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
                    counters
                    gauges
                    meters
                    histograms
                    timers))
      ([]
       (.report this
                (.getGauges     registry metric-filter)
                (.getCounters   registry metric-filter)
                (.getHistograms registry metric-filter)
                (.getMeters     registry metric-filter)
                (.getTimers     registry metric-filter))))))

(defn start
  "Start sending report every seconds."
  [^ScheduledReporter reporter ^Long seconds]
  (.start reporter seconds TimeUnit/SECONDS))

(defn stop
  [^ScheduledReporter reporter]
  (.stop reporter))

(comment
  (def reg (MetricRegistry.))

  (def my-influx {:url "http://127.0.0.1:8086/"
                  :db "reporter"})

  (def my-reporter
    (reporter :registry reg
              :influx-spec my-influx
              :default-tags {:host "air.local"})))

