(ns tasks)

(defn hello
  "Prints a friendly greeting.

   To be specific, the greeting is 'hello.'"
  []
  (println "hello"))

(defn goodbye
  "Prints a friendly farewell."
  []
  (println "goodbye"))

(def tasks
  {"hello" #'hello
   "goodbye" #'goodbye})
