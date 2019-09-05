(ns influx-reporter.reporter-test
  (:require [influx-reporter.reporter :as rep]
            [influx-reporter.resolver :as res]
            [clojure.test :refer :all])
  (:import [com.codahale.metrics MetricRegistry]))

(deftest test-serialize-metrics
  (let [reg (MetricRegistry.)
        my-influx {:url "http://127.0.0.1:8086/"
                   :db "reporter"}]
    (.inc (.counter reg "req.count"))
    (.update (.histogram reg "req.du") 5)
    (with-redefs [rep/send-data (fn [influx-spec & lines]
                                  [influx-spec lines])
                  res/get-clock-ms (constantly 1000)]
      (is (= [my-influx
              ["req.count,host=air.local,ua=Apache-HttpAsyncClient/4_1_3_(Java/1_8_0_201) count=1 1"
               "req.du,host=air.local,ua=Apache-HttpAsyncClient/4_1_3_(Java/1_8_0_201) p999=5.0,min=5,mean=5.0,p75=5.0,p99=5.0,max=5,count=1,p50=5.0,p95=5.0 1"]]
             (rep/send-report {:influx-spec my-influx
                               :default-tags {:host "air.local"
                                              :ua "Apache-HttpAsyncClient/4,1=3 (Java/1,8=0_201)"}}
                              (.getCounters   reg)
                              (.getHistograms reg)))))))

(deftest test-reset-metrics
  (let [reg (MetricRegistry.)
        my-influx {:url "http://127.0.0.1:8086/"
                   :db "reporter"}
        _ (.inc (.counter reg "req.count"))
        cnt (atom 1)
        reporter (rep/reporter :registry reg
                               :influx-spec my-influx
                               :reset-metrics? true
                               :after-report (fn []
                                               (swap! cnt inc)))]
    (is (= 1 (count (.getNames reg))))
    (rep/start reporter 1)
    (Thread/sleep 1100)
    (is (= 2 @cnt))
    (is (= 0 (count (.getNames reg))))
    (rep/stop reporter)))
