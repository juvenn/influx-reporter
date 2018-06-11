(ns influx-reporter.resolver-test
  (:require [influx-reporter.resolver :as res]
            [clojure.test :refer :all])
  (:import [com.codahale.metrics Gauge MetricRegistry]))

(deftest test-dotted-tag-resolver
  (is (= ["cpu_load" {}] (res/dotted-tag-resolver "cpu_load")))
  (is (= ["cpu_load" {"tag1" "val1"}]
         (res/dotted-tag-resolver "cpu_load.tag1.val1")))
  (is (= ["my.cpu_load" {}] (res/dotted-tag-resolver "my.cpu_load")))
  (is (= ["my.cpu_load" {"tag1" "val1"}]
         (res/dotted-tag-resolver "my.cpu_load.tag1.val1"))))

(deftest test-resolve-counter
  (let [reg (MetricRegistry.)
        counter (.counter reg "req")]
    (is (= {:count 0} (res/resolve-values {} counter)))
    (.inc counter)
    (is (= {:count 1} (res/resolve-values {} counter)))))

(deftest test-resolve-gauge
  (let [reg (MetricRegistry.)
        gauge (.register reg "req"
                         (reify Gauge
                           (getValue [this]
                             1)))]
    (is (= {:value 1} (res/resolve-values {} gauge)))))

(deftest test-resolve-meter
  (let [reg (MetricRegistry.)
        meter (.meter reg "req")]
    (is (= {:count 0 :rate_1m 0.0}
           (res/resolve-values {} meter)))
    (.mark meter)
    (is (= {:count 1 :rate_1m 0.0}
           (res/resolve-values {} meter)))))

(deftest test-resolve-histogram
  (let [reg (MetricRegistry.)
        histogram (.histogram reg "req")]
    (.update histogram 2)
    (is (= {:count 1 :min 2 :max 2 :mean 2.0
            :p50 2.0 :p75 2.0 :p95 2.0 :p99 2.0 :p999 2.0}
           (res/resolve-values {} histogram)))))

