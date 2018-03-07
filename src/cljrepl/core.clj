(ns cljrepl.core
  (:require [rebel-readline.core]
            [rebel-readline.clojure.main]
            [rebel-readline.clojure.line-reader]
            [cljrepl.unrepl-service]
            [clojure.main]))


(defn -main []
  (rebel-readline.core/with-line-reader
    (rebel-readline.clojure.line-reader/create
     (cljrepl.unrepl-service/create))
    (let [r rebel-readline.core/read-line]
      (->> (r) rebel-readline.clojure.line-reader/evaluate prn (while 1)))))
