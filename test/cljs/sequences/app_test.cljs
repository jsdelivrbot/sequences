(ns sequences.app-test
  (:require-macros [cljs.test :refer [deftest testing is]])
  (:require [cljs.test :as t]
            [sequences.core :as app]))

(deftest test-arithmetic []
  (is (= 1 0)))
