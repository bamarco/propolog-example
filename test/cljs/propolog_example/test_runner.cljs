(ns propolog-example.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [propolog-example.core-test]
   [propolog-example.common-test]))

(enable-console-print!)

(doo-tests 'propolog-example.core-test
           'propolog-example.common-test)
