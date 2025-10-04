(ns caveman.math-test
  (:require
   [caveman.test-system :as test-system]
   [clojure.test :as t]
   [next.jdbc :as jdbc]))

(t/deftest one-plus-one
  (t/is (= 2 (+ 1 1)) "One plus one equals 3! Of course"))

(t/deftest counting-works
  (test-system/with-test-db
    (fn [db]
      (jdbc/execute! db ["INSERT INTO prehistoric.hominid(name) VALUES (?)", "Grunto"])
      (jdbc/execute! db ["INSERT INTO prehistoric.hominid(name) VALUES (?)", "Blingus"])
      (t/is (= (:count (jdbc/execute-one! db ["SELECT COUNT(*) as count FROM prehistoric.hominid"])) 2))

      ;
      )))


(comment
  (t/run-tests)

  ; paren gate
  )