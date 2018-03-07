(ns rebel-unrepl.unrepl-service
  (:require
   [rebel-readline.clojure.line-reader :as service]
   [clj-sockets.core :as sockets]
   [clojure.core.async :as async]
   [clojure.edn :as edn]))


(defmethod service/-eval ::service [self form]
  (sockets/write-to (::conn self) (str form "\n"))
  (let [res (async/<!! (::eval-chan self))]
    res))

(defmethod service/-read-string ::service [self form-str]
  (when (string? form-str)
    (try
      {:form (with-in-str form-str
               (read {:read-cond :allow} *in*))}
      (catch Throwable e
        {:exception (Throwable->map e)}))))

(defmethod service/-complete ::service [_ word options]
  (println "complete"))

(defmulti handle-message (fn [chans msg] (first msg) ))

(defmethod handle-message :eval [chans msg]
  (async/go
    (async/>! (::eval-chan chans) (second msg))))

(defmethod handle-message :default [chans msg]
  #_(println "default:" msg))

(defn start-sideload-loop [conn-opts upgrade-fn]
  (let [sideload-conn (sockets/create-socket (:host conn-opts) (:port conn-opts))]
    (sockets/write-to sideload-conn upgrade-fn)
    (async/go-loop []
      (let [req]))))

(defn create
  ([] (create {}))
  ([options]
   (let [conn (sockets/create-socket "localhost" 50505)
         sideload-conn (sockets/create-sockets "localhost" 50505)
         unrepl-blob (-> "rebel_unrepl/unrepl_blob.clj" clojure.java.io/resource slurp)
         stdout-chan (async/chan)
         eval-chan (async/chan)
         chans {::eval-chan eval-chan
                ::stdout-chan stdout-chan}]
     ;; upgrade the repl
     (sockets/write-to conn unrepl-blob)
     (async/go-loop []
       (let [line (sockets/read-line conn)
             parsed-line (edn/read-string {:default (fn [tag value] value)} line)]
         (if (vector? parsed-line) ; was the result an edn by unrepl?
           (handle-message chans parsed-line)
           (println line)))
       (recur))
     (merge service/default-config
            options
            {:rebel-readline.service/type ::service
             ::conn conn}
            chans))))
