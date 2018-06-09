(ns influx-reporter.resolver-test
  (:require [influx-reporter.resolver :as res]
            [clojure.test :refer :all])
  (:import [com.codahale.metrics Gauge MetricRegistry]
           [influx_reporter ValuesResolver]))

(deftest test-dotted-tag-resolver
  (is (= ["cpu_load" {}] (res/dotted-tag-resolver "cpu_load")))
  (is (= ["cpu_load" {"tag1" "val1"}]
         (res/dotted-tag-resolver "cpu_load.tag1.val1")))
  (is (= ["my.cpu_load" {}] (res/dotted-tag-resolver "my.cpu_load")))
  (is (= ["my.cpu_load" {"tag1" "val1"}]
         (res/dotted-tag-resolver "my.cpu_load.tag1.val1"))))

(deftest test-resolve-metric
  (let [reg (MetricRegistry.)
        counter (.counter reg "req")
        resolver (ValuesResolver.)]
    (is (= {"req" 0} (.resolveValues resolver counter)))))

(comment
    (.register reg "req"
               (reify Gauge
                 (getValue [this]
                   1)))
    (.mark (.meter  reg "req"))
    (.update (.histogram reg "req") 1)
    (.time (.timer reg "req") #(Thread/sleep 5))
  )
