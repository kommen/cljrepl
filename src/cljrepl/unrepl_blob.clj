(clojure.core/let [prefix__502__auto__ (clojure.core/name (clojure.core/gensym)) code__503__auto__ (.replaceAll "(ns unrepl.print\n  (:require [clojure.string :as str]\n            [clojure.edn :as edn]\n            [clojure.main :as main]))\n\n(defprotocol MachinePrintable\n  (-print-on [x write rem-depth]))\n\n(defn print-on [write x rem-depth]\n  (let [rem-depth (dec rem-depth)]\n    (if (and (neg? rem-depth) (or (nil? *print-length*) (pos? *print-length*)))\n      (binding [*print-length* 0]\n        (print-on write x 0))\n      (do\n        (when (and *print-meta* (meta x))\n          (write \"#unrepl/meta [\")\n          (-print-on (meta x) write rem-depth)\n          (write \" \"))\n        (-print-on x write rem-depth)\n        (when (and *print-meta* (meta x))\n          (write \"]\"))))))\n\n(defn base64-encode [^java.io.InputStream in]\n  (let [table \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\"\n        sb (StringBuilder.)]\n    (loop [shift 4 buf 0]\n      (let [got (.read in)]\n        (if (neg? got)\n          (do\n            (when-not (= shift 4)\n              (let [n (bit-and (bit-shift-right buf 6) 63)]\n                (.append sb (.charAt table n))))\n            (cond\n              (= shift 2) (.append sb \"==\")\n              (= shift 0) (.append sb \\=))\n            (str sb))\n          (let [buf (bit-or buf (bit-shift-left got shift))\n                n (bit-and (bit-shift-right buf 6) 63)]\n            (.append sb (.charAt table n))\n            (let [shift (- shift 2)]\n              (if (neg? shift)\n                (do\n                  (.append sb (.charAt table (bit-and buf 63)))\n                  (recur 4 0))\n                (recur shift (bit-shift-left buf 6))))))))))\n\n(defn base64-decode [^String s]\n  (let [table \"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\"\n        in (java.io.StringReader. s)\n        bos (java.io.ByteArrayOutputStream.)]\n    (loop [bits 0 buf 0]\n      (let [got (.read in)]\n        (when-not (or (neg? got) (= 61 #_\\= got))\n          (let [buf (bit-or (.indexOf table got) (bit-shift-left buf 6))\n                bits (+ bits 6)]\n            (if (<= 8 bits)\n              (let [bits (- bits 8)]\n                (.write bos (bit-shift-right buf bits))\n                (recur bits (bit-and 63 buf)))\n              (recur bits buf))))))\n    (.toByteArray bos)))\n\n(def ^:dynamic *elide*\n  \"Function of 1 argument which returns the elision.\"\n  (constantly nil))\n\n(def ^:dynamic *string-length* 80)\n\n(def ^:dynamic *max-colls* 100)\n\n(def ^:dynamic *realize-on-print*\n  \"Set to false to avoid realizing lazy sequences.\"\n  true)\n\n(defmacro ^:private blame-seq [& body]\n  `(try (seq ~@body)\n        (catch Throwable t#\n          (list (tagged-literal 'unrepl/lazy-error t#)))))\n\n(defn- may-print? [s]\n  (or *realize-on-print* (not (instance? clojure.lang.IPending s)) (realized? s)))\n\n(declare ->ElidedKVs)\n\n(defn- print-kvs\n  [write kvs rem-depth]\n  (let [print-length *print-length*]\n    (loop [kvs kvs i 0]\n      (if (< i print-length)\n        (when-some [[[k v] & kvs] (seq kvs)]\n          (when (pos? i) (write \", \"))\n          (print-on write k rem-depth)\n          (write \" \")\n          (print-on write v rem-depth)\n          (recur kvs (inc i)))\n        (when (seq kvs)\n          (when (pos? i) (write \", \"))\n          (write \"#unrepl/... nil \")\n          (print-on write (tagged-literal 'unrepl/... (*elide* (->ElidedKVs kvs))) rem-depth))))))\n\n(defn- print-vs\n  [write vs rem-depth]\n  (let [print-length *print-length*]\n    (loop [vs vs i 0]\n      (when-some [[v :as vs] (blame-seq vs)]\n        (when (pos? i) (write \" \"))\n        (if (and (< i print-length) (may-print? vs))\n          (if (and (tagged-literal? v) (= (:tag v) 'unrepl/lazy-error))\n            (print-on write v rem-depth)\n            (do\n              (print-on write v rem-depth)\n              (recur (rest vs) (inc i))))\n          (print-on write (tagged-literal 'unrepl/... (*elide* vs)) rem-depth))))))\n\n(defrecord WithBindings [bindings x]\n  MachinePrintable\n  (-print-on [_ write rem-depth]\n    (with-bindings bindings\n      (-print-on x write rem-depth))))\n\n(defrecord ElidedKVs [s]\n  MachinePrintable\n  (-print-on [_ write rem-depth]\n    (write \"{\")\n    (print-kvs write s rem-depth)\n    (write \"}\")))\n\n(def atomic? (some-fn nil? true? false? char? string? symbol? keyword? #(and (number? %) (not (ratio? %)))))\n\n(defn- as-str\n  \"Like pr-str but escapes all ASCII control chars.\"\n  [x]\n  ;hacky\n  (cond\n    (string? x) (str/replace (pr-str x) #\"\\p{Cntrl}\"\n                             #(format \"\\\\u%04x\" (int (.charAt ^String % 0))))\n    (char? x) (str/replace (pr-str x) #\"\\p{Cntrl}\"\n                           #(format \"u%04x\" (int (.charAt ^String % 0))))\n    :else (pr-str x)))\n\n(defmacro ^:private latent-fn [& fn-body]\n  `(let [d# (delay (binding [*ns* (find-ns '~(ns-name *ns*))] (eval '(fn ~@fn-body))))]\n     (fn\n       ([] (@d#))\n       ([x#] (@d# x#))\n       ([x# & xs#] (apply @d# x# xs#)))))\n\n(defrecord MimeContent [mk-in]\n  MachinePrintable\n  (-print-on [_ write rem-depth]\n    (with-open [in (mk-in)]\n      (write \"#unrepl/base64 \\\"\")\n      (write (base64-encode in))\n      (write \"\\\"\"))))\n\n(defn- mime-content [mk-in]\n  (when-some [e (*elide* (MimeContent. mk-in))]\n    {:content (tagged-literal 'unrepl/... e)}))\n\n(def ^:dynamic *object-representations*\n  \"map of classes to functions returning their representation component (3rd item in #unrepl/object [class id rep])\"\n  {clojure.lang.IDeref\n   (fn [x]\n     (let [pending? (and (instance? clojure.lang.IPending x) ; borrowed from https://github.com/brandonbloom/fipp/blob/8df75707e355c1a8eae5511b7d73c1b782f57293/src/fipp/ednize.clj#L37-L51\n                         (not (.isRealized ^clojure.lang.IPending x)))\n           [ex val] (when-not pending?\n                      (try [false @x]\n                           (catch Throwable e\n                             [true e])))\n           failed? (or ex (and (instance? clojure.lang.Agent x)\n                               (agent-error x)))\n           status (cond\n                    failed? :failed\n                    pending? :pending\n                    :else :ready)]\n       {:unrepl.ref/status status :unrepl.ref/val val}))\n\n   clojure.lang.AFn\n   (fn [x]\n     (-> x class .getName main/demunge))\n\n   java.io.File (fn [^java.io.File f]\n                  (into {:path (.getPath f)}\n                        (when (.isFile f)\n                          {:attachment (tagged-literal 'unrepl/mime\n                                                       (into {:content-type \"application/octet-stream\"\n                                                              :content-length (.length f)}\n                                                             (mime-content #(java.io.FileInputStream. f))))})))\n\n   java.awt.Image (latent-fn [^java.awt.Image img]\n                             (let [w (.getWidth img nil)\n                                   h (.getHeight img nil)]\n                               (into {:width w, :height h}\n                                     {:attachment\n                                      (tagged-literal 'unrepl/mime\n                                                      (into {:content-type \"image/png\"}\n                                                            (mime-content #(let [bos (java.io.ByteArrayOutputStream.)]\n                                                                             (when (javax.imageio.ImageIO/write\n                                                                                    (doto (java.awt.image.BufferedImage. w h java.awt.image.BufferedImage/TYPE_INT_ARGB)\n                                                                                      (-> .getGraphics (.drawImage img 0 0 nil)))\n                                                                                    \"png\" bos)\n                                                                               (java.io.ByteArrayInputStream. (.toByteArray bos)))))))})))\n\n   Object (fn [x]\n            (if (-> x class .isArray)\n              (seq x)\n              (str x)))})\n\n(defn- object-representation [x]\n  (reduce-kv (fn [_ class f]\n               (when (instance? class x) (reduced (f x)))) nil *object-representations*)) ; todo : cache\n\n(defn- class-form [^Class x]\n  (if (.isArray x) [(-> x .getComponentType class-form)] (symbol (.getName x))))\n\n(def unreachable (tagged-literal 'unrepl/... nil))\n\n(defn- print-tag-lit-on [write tag form rem-depth]\n  (write (str \"#\" tag \" \"))\n  (print-on write form rem-depth))\n\n(defn- print-trusted-tag-lit-on [write tag form rem-depth]\n  (print-tag-lit-on write tag form (inc rem-depth)))\n\n;; --\n;; Throwable->map backport from Clojure 1.9\n;;\n;; The behavior of clojure.core/Throwable->map changed from 1.8 to 1.9.\n;; We need the (more correct) behavior in 1.9.\n;;\n;; https://github.com/clojure/clojure/blob/master/changes.md#33-other-fixes\n\n(defn StackTraceElement->vec'\n  \"Constructs a data representation for a StackTraceElement\"\n  {:added \"1.9\"}\n  [^StackTraceElement o]\n  [(symbol (.getClassName o)) (symbol (.getMethodName o)) (.getFileName o) (.getLineNumber o)])\n\n(defn Throwable->map'\n  \"Constructs a data representation for a Throwable.\"\n  {:added \"1.7\"}\n  [^Throwable o]\n  (let [base (fn [^Throwable t]\n               (merge {:type (symbol (.getName (class t)))\n                       :message (.getLocalizedMessage t)}\n                      (when-let [ed (ex-data t)]\n                        {:data ed})\n                      (let [st (.getStackTrace t)]\n                        (when (pos? (alength st))\n                          {:at (StackTraceElement->vec' (aget st 0))}))))\n        via (loop [via [], ^Throwable t o]\n              (if t\n                (recur (conj via t) (.getCause t))\n                via))\n        ^Throwable root (peek via)\n        m {:cause (.getLocalizedMessage root)\n           :via (vec (map base via))\n           :trace (vec (map StackTraceElement->vec'\n                            (.getStackTrace ^Throwable (or root o))))}\n        data (ex-data root)]\n    (if data\n      (assoc m :data data)\n      m)))\n\n;; use standard implementation if running in Clojure 1.9 or above,\n;; backported version otherwise\n\n(def Throwable->map''\n  (if (neg? (compare (mapv *clojure-version* [:major :minor]) [1 9]))\n    Throwable->map'\n    Throwable->map))\n\n;; --\n\n\n(extend-protocol MachinePrintable\n  clojure.lang.TaggedLiteral\n  (-print-on [x write rem-depth]\n\n    (case (:tag x)\n      unrepl/... (binding ; don't elide the elision \n                  [*print-length* Long/MAX_VALUE\n                   *print-level* Long/MAX_VALUE\n                   *string-length* Long/MAX_VALUE]\n                   (write (str \"#\" (:tag x) \" \"))\n                   (print-on write (:form x) Long/MAX_VALUE))\n      (print-tag-lit-on write (:tag x) (:form x) rem-depth)))\n\n  clojure.lang.Ratio\n  (-print-on [x write rem-depth]\n    (print-trusted-tag-lit-on write \"unrepl/ratio\"\n                              [(.numerator x) (.denominator x)] rem-depth))\n\n  clojure.lang.Var\n  (-print-on [x write rem-depth]\n    (print-tag-lit-on write \"clojure/var\"\n                      (when-some [ns (:ns (meta x))] ; nil when local var\n                        (symbol (name (ns-name ns)) (name (:name (meta x)))))\n                      rem-depth))\n\n  Throwable\n  (-print-on [t write rem-depth]\n    (print-tag-lit-on write \"error\" (Throwable->map'' t) rem-depth))\n\n  Class\n  (-print-on [x write rem-depth]\n    (print-tag-lit-on write \"unrepl.java/class\" (class-form x) rem-depth))\n\n  java.util.Date (-print-on [x write rem-depth] (write (pr-str x)))\n  java.util.Calendar (-print-on [x write rem-depth] (write (pr-str x)))\n  java.sql.Timestamp (-print-on [x write rem-depth] (write (pr-str x)))\n  clojure.lang.Namespace\n  (-print-on [x write rem-depth]\n    (print-tag-lit-on write \"unrepl/ns\" (ns-name x) rem-depth))\n  java.util.regex.Pattern\n  (-print-on [x write rem-depth]\n    (print-tag-lit-on write \"unrepl/pattern\" (str x) rem-depth))\n  String\n  (-print-on [x write rem-depth]\n    (if (<= (count x) *string-length*)\n      (write (as-str x))\n      (let [i (if (and (Character/isHighSurrogate (.charAt ^String x (dec *string-length*)))\n                       (Character/isLowSurrogate (.charAt ^String x *string-length*)))\n                (inc *string-length*) *string-length*)\n            prefix (subs x 0 i)\n            rest (subs x i)]\n        (if (= rest \"\")\n          (write (as-str x))\n          (do\n            (write \"#unrepl/string [\")\n            (write (as-str prefix))\n            (write \" \")\n            (print-on write (tagged-literal 'unrepl/... (*elide* rest)) rem-depth)\n            (write \"]\")))))))\n\n(defn- print-coll [open close write x rem-depth]\n  (write open)\n  (print-vs write x rem-depth)\n  (write close))\n\n(extend-protocol MachinePrintable\n  nil\n  (-print-on [_ write _] (write \"nil\"))\n  Object\n  (-print-on [x write rem-depth]\n    (cond\n      (atomic? x) (write (as-str x))\n      (map? x)\n      (do\n        (when (record? x)\n          (write \"#\") (write (.getName (class x))) (write \" \"))\n        (write \"{\")\n        (print-kvs write x rem-depth)\n        (write \"}\"))\n      (vector? x) (print-coll \"[\" \"]\" write x rem-depth)\n      (seq? x) (print-coll \"(\" \")\" write x rem-depth)\n      (set? x) (print-coll \"#{\" \"}\" write x rem-depth)\n      :else\n      (print-trusted-tag-lit-on write \"unrepl/object\"\n                                [(class x) (format \"0x%x\" (System/identityHashCode x)) (object-representation x)\n                                 {:bean {unreachable (tagged-literal 'unrepl/... (*elide* (ElidedKVs. (bean x))))}}]\n                                (inc rem-depth))))) ; is very trusted\n\n(defn edn-str [x]\n  (let [out (java.io.StringWriter.)\n        write (fn [^String s] (.write out s))]\n    (binding [*print-readably* true\n              *print-length* (or *print-length* 10)\n              *print-level* (or *print-level* 8)\n              *string-length* (or *string-length* 72)]\n      (print-on write x (or *print-level* 8))\n      (str out))))\n\n(defn full-edn-str [x]\n  (binding [*print-length* Long/MAX_VALUE\n            *print-level* Long/MAX_VALUE\n            *string-length* Integer/MAX_VALUE]\n    (edn-str x)))\n(ns unrepl.repl\n  (:require [clojure.main :as m]\n            [unrepl.print :as p]\n            [clojure.edn :as edn]\n            [clojure.java.io :as io]))\n\n(defn classloader\n  \"Creates a classloader that obey standard delegating policy.\n   Takes two arguments: a parent classloader and a function which\n   takes a keyword (:resource or :class) and a string (a resource or a class name) and returns an array of bytes\n   or nil.\"\n  [parent f]\n  (let [define-class (doto (.getDeclaredMethod ClassLoader \"defineClass\" (into-array [String (Class/forName \"[B\") Integer/TYPE Integer/TYPE]))\n                       (.setAccessible true))]\n    (proxy [ClassLoader] [parent]\n      (findResource [name]\n        (when-some  [bytes (f :resource name)]\n          (let [file (doto (java.io.File/createTempFile \"unrepl-sideload-\" (str \"-\" (re-find #\"[^/]*$\" name)))\n                       .deleteOnExit)]\n            (io/copy bytes file)\n            (-> file .toURI .toURL))))\n      (findClass [name]\n        (if-some  [bytes (f :class name)]\n          (.invoke define-class this (to-array [name bytes (int 0) (int (count bytes))]))\n          (throw (ClassNotFoundException. name)))))))\n\n(defn ^java.io.Writer tagging-writer\n  ([write]\n   (proxy [java.io.Writer] []\n     (close []) ; do not cascade\n     (flush []) ; atomic always flush\n     (write\n       ([x]\n        (write (cond\n                 (string? x) x\n                 (integer? x) (str (char x))\n                 :else (String. ^chars x))))\n       ([string-or-chars off len]\n        (when (pos? len)\n          (write (subs (if (string? string-or-chars) string-or-chars (String. ^chars string-or-chars))\n                       off (+ off len))))))))\n  ([tag write]\n   (tagging-writer (fn [s] (write [tag s]))))\n  ([tag group-id write]\n   (tagging-writer (fn [s] (write [tag s group-id])))))\n\n(defn blame-ex [phase ex]\n  (if (::phase (ex-data ex))\n    ex\n    (ex-info (str \"Exception during \" (name phase) \" phase.\")\n             {::ex ex ::phase phase} ex)))\n\n(defmacro blame [phase & body]\n  `(try ~@body\n        (catch Throwable t#\n          (throw (blame-ex ~phase t#)))))\n\n(defn atomic-write [^java.io.Writer w]\n  (fn [x]\n    (let [s (blame :print (p/edn-str x))] ; was pr-str, must occur outside of the locking form to avoid deadlocks\n      (locking w\n        (.write w s)\n        (.write w \"\\n\")\n        (.flush w)))))\n\n(defn fuse-write [awrite]\n  (fn [x]\n    (when-some [w @awrite]\n      (try\n        (w x)\n        (catch Throwable t\n          (reset! awrite nil))))))\n\n(def ^:dynamic write)\n\n(defn unrepl-reader [^java.io.Reader r before-read]\n  (let [offset (atom 0)\n        offset! #(swap! offset + %)]\n    (proxy [clojure.lang.LineNumberingPushbackReader clojure.lang.ILookup] [r]\n      (valAt\n        ([k] (get this k nil))\n        ([k not-found] (case k :offset @offset not-found)))\n      (read\n        ([]\n         (before-read)\n         (let [c (proxy-super read)]\n           (when-not (neg? c) (offset! 1))\n           c))\n        ([cbuf]\n         (before-read)\n         (let [n (proxy-super read cbuf)]\n           (when (pos? n) (offset! n))\n           n))\n        ([cbuf off len]\n         (before-read)\n         (let [n (proxy-super read cbuf off len)]\n           (when (pos? n) (offset! n))\n           n)))\n      (unread\n        ([c-or-cbuf]\n         (if (integer? c-or-cbuf)\n           (when-not (neg? c-or-cbuf) (offset! -1))\n           (offset! (- (alength c-or-cbuf))))\n         (proxy-super unread c-or-cbuf))\n        ([cbuf off len]\n         (offset! (- len))\n         (proxy-super unread cbuf off len)))\n      (skip [n]\n        (let [n (proxy-super skip n)]\n          (offset! n)\n          n))\n      (readLine []\n        (when-some [s (proxy-super readLine)]\n          (offset! (count s))\n          s)))))\n\n(defn- close-socket! [x]\n  ; hacky way because the socket is not exposed by clojure.core.server\n  (loop [x x]\n    (if (= \"java.net.SocketInputStream\" (.getName (class x)))\n      (do (.close x) true)\n      (when-some [^java.lang.reflect.Field field\n                  (->> x class (iterate #(.getSuperclass %)) (take-while identity)\n                       (mapcat #(.getDeclaredFields %))\n                       (some #(when (#{\"in\" \"sd\"} (.getName ^java.lang.reflect.Field %)) %)))]\n        (recur (.get (doto field (.setAccessible true)) x))))))\n\n(defn soft-store [make-action]\n  (let [ids-to-session+refs (atom {})\n        refs-to-ids (atom {})\n        refq (java.lang.ref.ReferenceQueue.)\n        NULL (Object.)]\n    (.start (Thread. (fn []\n                       (let [ref (.remove refq)]\n                         (let [id (@refs-to-ids ref)]\n                           (swap! refs-to-ids dissoc ref)\n                           (swap! ids-to-session+refs dissoc id)))\n                       (recur))))\n    {:put (fn [session-id x]\n            (let [x (if (nil? x) NULL x)\n                  id (keyword (gensym))\n                  ref (java.lang.ref.SoftReference. x refq)]\n              (swap! refs-to-ids assoc ref id)\n              (swap! ids-to-session+refs assoc id [session-id ref])\n              {:get (make-action id)}))\n     :get (fn [id]\n            (when-some [[session-id  ^java.lang.ref.Reference r] (@ids-to-session+refs id)]\n              (let [x (.get r)]\n                [session-id (if (= NULL x) nil x)])))}))\n\n(defonce ^:private sessions (atom {}))\n\n(defn session [id]\n  (some-> @sessions (get id) deref))\n\n(defonce ^:private elision-store (soft-store #(list `fetch %)))\n(defn fetch [id]\n  (if-some [[session-id x] ((:get elision-store) id)]\n    (unrepl.print.WithBindings.\n      (select-keys (some-> session-id session :bindings) [#'*print-length* #'*print-level* #'p/*string-length* #'p/*elide*])\n      (cond\n        (instance? unrepl.print.ElidedKVs x) x\n        (string? x) x\n        (instance? unrepl.print.MimeContent x) x\n        :else (seq x)))\n    p/unreachable))\n\n(defn interrupt! [session-id eval]\n  (let [{:keys [^Thread thread eval-id promise]}\n        (some-> session-id session :current-eval)]\n    (when (and (= eval eval-id)\n               (deliver promise\n                        {:ex (doto (ex-info \"Evaluation interrupted\" {::phase :eval})\n                               (.setStackTrace (.getStackTrace thread)))\n                         :bindings {}}))\n      (.stop thread)\n      true)))\n\n(defn background! [session-id eval]\n  (let [{:keys [eval-id promise future]}\n        (some-> session-id session :current-eval)]\n    (boolean\n     (and\n      (= eval eval-id)\n      (deliver promise\n               {:eval future\n                :bindings {}})))))\n\n(defn exit! [session-id] ; too violent\n  (some-> session-id session :in close-socket!))\n\n(defn reattach-outs! [session-id]\n  (some-> session-id session :write-atom\n          (reset!\n           (if (bound? #'write)\n             write\n             (let [out *out*]\n               (fn [x]\n                 (binding [*out* out\n                           *print-readably* true]\n                   (prn x))))))))\n\n(defn attach-sideloader! [session-id]\n  (prn '[:unrepl.jvm.side-loader/hello])\n  (some-> session-id session :side-loader\n          (reset!\n           (let [out *out*\n                 in *in*]\n             (fn self [k name]\n               (binding [*out* out]\n                 (locking self\n                   (prn [k name])\n                   (some-> (edn/read {:eof nil} in) p/base64-decode)))))))\n  (let [o (Object.)] (locking o (.wait o))))\n\n(defn set-file-line-col [session-id file line col]\n  (when-some [^java.lang.reflect.Field field\n              (->> clojure.lang.LineNumberingPushbackReader\n                   .getDeclaredFields\n                   (some #(when (= \"_columnNumber\" (.getName ^java.lang.reflect.Field %)) %)))]\n    (doto field (.setAccessible true)) ; sigh\n    (when-some [in (some-> session-id session :in)]\n      (set! *file* file)\n      (set! *source-path* file)\n      (.setLineNumber in line)\n      (.set field in (int col)))))\n\n(defn- writers-flushing-repo [max-latency-ms]\n  (let [writers (java.util.WeakHashMap.)\n        flush-them-all #(locking writers\n                          (doseq [^java.io.Writer w (.keySet writers)]\n                            (.flush w)))]\n    (.scheduleAtFixedRate\n     (java.util.concurrent.Executors/newScheduledThreadPool 1)\n     flush-them-all\n     max-latency-ms max-latency-ms java.util.concurrent.TimeUnit/MILLISECONDS)\n    (fn [w]\n      (locking writers (.put writers w nil)))))\n\n(defmacro ^:private flushing [bindings & body]\n  `(binding ~bindings\n     (try ~@body\n          (finally ~@(for [v (take-nth 2 bindings)]\n                       `(.flush ~(vary-meta v assoc :tag 'java.io.Writer)))))))\n\n(defn- non-eliding-write [x]\n  (binding [*print-length* Long/MAX_VALUE\n            *print-level* Long/MAX_VALUE\n            p/*string-length* Long/MAX_VALUE]\n    (write x)))\n\n(defn start []\n  (with-local-vars [in-eval false\n                    unrepl false\n                    eval-id 0\n                    prompt-vars #{#'*ns* #'*warn-on-reflection*}\n                    current-eval-future nil]\n    (let [session-id (keyword (gensym \"session\"))\n          raw-out *out*\n          aw (atom (atomic-write raw-out))\n          write-here (fuse-write aw)\n          schedule-writer-flush! (writers-flushing-repo 50) ; 20 fps (flushes per second)\n          scheduled-writer (fn [& args]\n                             (-> (apply tagging-writer args)\n                                 java.io.BufferedWriter.\n                                 (doto schedule-writer-flush!)))\n          edn-out (scheduled-writer :out (fn [x] (binding [p/*string-length* Integer/MAX_VALUE] (write-here x))))\n          ensure-raw-repl (fn []\n                            (when (and @in-eval @unrepl) ; reading from eval!\n                              (var-set unrepl false)\n                              (write [:bye {:reason :upgrade :actions {}}])\n                              (flush)\n                              ; (reset! aw (blocking-write))\n                              (set! *out* raw-out)))\n          in (unrepl-reader *in* ensure-raw-repl)\n          session-state (atom {:current-eval {}\n                               :in in\n                               :write-atom aw\n                               :log-eval (fn [msg]\n                                           (when (bound? eval-id)\n                                             (write [:log msg @eval-id])))\n                               :log-all (fn [msg]\n                                          (write [:log msg nil]))\n                               :side-loader (atom nil)\n                               :prompt-vars #{#'*ns* #'*warn-on-reflection*}})\n          current-eval-thread+promise (atom nil)\n          ensure-unrepl (fn []\n                          (when-not @unrepl\n                            (var-set unrepl true)\n                            (flush)\n                            (set! *out* edn-out)\n                            (non-eliding-write\n                              [:unrepl/hello {:session session-id\n                                              :actions (into\n                                                         {:exit `(exit! ~session-id)\n                                                          :start-aux `(start-aux ~session-id)\n                                                          :log-eval\n                                                          `(some-> ~session-id session :log-eval)\n                                                          :log-all\n                                                          `(some-> ~session-id session :log-all)\n                                                          :print-limits\n                                                          `(let [bak# {:unrepl.print/string-length p/*string-length*\n                                                                       :unrepl.print/coll-length *print-length*\n                                                                       :unrepl.print/nesting-depth *print-level*}]\n                                                             (some->> ~(tagged-literal 'unrepl/param :unrepl.print/string-length) (set! p/*string-length*))\n                                                             (some->> ~(tagged-literal 'unrepl/param :unrepl.print/coll-length) (set! *print-length*))\n                                                             (some->> ~(tagged-literal 'unrepl/param :unrepl.print/nesting-depth) (set! *print-level*))\n                                                             bak#)\n                                                          :set-source\n                                                          `(unrepl/do\n                                                             (set-file-line-col ~session-id\n                                                                                ~(tagged-literal 'unrepl/param :unrepl/sourcename)\n                                                                                ~(tagged-literal 'unrepl/param :unrepl/line)\n                                                                                ~(tagged-literal 'unrepl/param :unrepl/column)))\n                                                          :unrepl.jvm/start-side-loader\n                                                          `(attach-sideloader! ~session-id)}\n                                                {})}])))\n\n          interruptible-eval\n          (fn [form]\n            (try\n              (let [original-bindings (get-thread-bindings)\n                    p (promise)\n                    f\n                    (future\n                      (swap! session-state update :current-eval\n                             assoc :thread (Thread/currentThread))\n                      (with-bindings original-bindings\n                        (try\n                          (write [:started-eval\n                                  {:actions\n                                   {:interrupt (list `interrupt! session-id @eval-id)\n                                    :background (list `background! session-id @eval-id)}}\n                                  @eval-id])\n                          (let [v (with-bindings {in-eval true}\n                                    (blame :eval (eval form)))]\n                            (deliver p {:eval v :bindings (get-thread-bindings)})\n                            v)\n                          (catch Throwable t\n                            (deliver p {:ex t :bindings (get-thread-bindings)})\n                            (throw t)))))]\n                (swap! session-state update :current-eval\n                       into {:eval-id @eval-id :promise p :future f})\n                (let [{:keys [ex eval bindings]} @p]\n                  (swap! session-state assoc :bindings bindings)\n                  (doseq [[var val] bindings\n                          :when (not (identical? val (original-bindings var)))]\n                    (var-set var val))\n                  (if ex\n                    (throw ex)\n                    eval)))\n              (finally\n                (swap! session-state assoc :current-eval {}))))\n          cl (.getContextClassLoader (Thread/currentThread))\n          slcl (classloader cl\n                            (fn [k x]\n                              (when-some [f (some-> session-state deref :side-loader deref)]\n                                (f k x))))]\n      (swap! session-state assoc :class-loader slcl)\n      (swap! sessions assoc session-id session-state)\n      (binding [*out* raw-out\n                *err* (tagging-writer :err write)\n                *in* in\n                *file* \"unrepl-session\"\n                *source-path* \"unrepl-session\"\n                p/*elide* (partial (:put elision-store) session-id)\n                p/*string-length* p/*string-length*\n                write write-here]\n        (.setContextClassLoader (Thread/currentThread) slcl)\n        (with-bindings {clojure.lang.Compiler/LOADER slcl}\n          (try\n            (m/repl\n             :init #(swap! session-state assoc :bindings (get-thread-bindings))\n             :prompt (fn []\n                       (ensure-unrepl)\n                       (non-eliding-write [:prompt (into {:file *file*\n                                                          :line (.getLineNumber *in*)\n                                                          :column (.getColumnNumber *in*)\n                                                          :offset (:offset *in*)}\n                                                     (map (fn [v]\n                                                            (let [m (meta v)]\n                                                              [(symbol (name (ns-name (:ns m))) (name (:name m))) @v])))\n                                                     (:prompt-vars @session-state))]))\n             :read (fn [request-prompt request-exit]\n                     (blame :read (let [id (var-set eval-id (inc @eval-id))\n                                        line+col [(.getLineNumber *in*) (.getColumnNumber *in*)]\n                                        offset (:offset *in*)\n                                        r (m/repl-read request-prompt request-exit)\n                                        line+col' [(.getLineNumber *in*) (.getColumnNumber *in*)]\n                                        offset' (:offset *in*)\n                                        len (- offset' offset)]\n                                    (write [:read {:from line+col :to line+col'\n                                                   :offset offset\n                                                   :len (- offset' offset)}\n                                            id])\n                                    (if (and (seq?  r) (= (first r) 'unrepl/do))\n                                      (let [write #(binding [p/*string-length* Integer/MAX_VALUE] (write %))]\n                                        (flushing [*err* (tagging-writer :err id write)\n                                                   *out* (scheduled-writer :out id write)]\n                                                  (eval (cons 'do (next r))))\n                                        request-prompt)\n                                      r))))\n             :eval (fn [form]\n                     (let [id @eval-id\n                           write #(binding [p/*string-length* Integer/MAX_VALUE] (write %))]\n                       (flushing [*err* (tagging-writer :err id write)\n                                  *out* (scheduled-writer :out id write)]\n                                 (interruptible-eval form))))\n             :print (fn [x]\n                      (ensure-unrepl)\n                      (write [:eval x @eval-id]))\n             :caught (fn [e]\n                       (ensure-unrepl)\n                       (let [{:keys [::ex ::phase]\n                              :or {ex e phase :repl}} (ex-data e)]\n                         (write [:exception {:ex ex :phase phase} @eval-id]))))\n            (finally\n              (.setContextClassLoader (Thread/currentThread) cl))))\n        (write [:bye {:reason :disconnection\n                      :outs :muted\n                      :actions {:reattach-outs `(reattach-outs! ~session-id)}}])))))\n\n(defn start-aux [session-id]\n  (let [cl (.getContextClassLoader (Thread/currentThread))]\n    (try\n      (some->> session-id session :class-loader (.setContextClassLoader (Thread/currentThread)))\n      (start)\n      (finally\n        (.setContextClassLoader (Thread/currentThread) cl)))))\n\n;; WIP for extensions\n\n(defmacro ensure-ns [[fully-qualified-var-name & args :as expr]]\n  `(do\n     (require '~(symbol (namespace fully-qualified-var-name)))\n     ~expr))\n(ns user)\n(unrepl.repl/start)" "(?<!:)unrepl\\.(?:repl|print)" (clojure.core/str "$0" prefix__502__auto__)) rdr__504__auto__ (clojure.core/-> code__503__auto__ java.io.StringReader. clojure.lang.LineNumberingPushbackReader.)] (try (clojure.core/binding [clojure.core/*ns* clojure.core/*ns*] (clojure.core/loop [ret__505__auto__ nil] (clojure.core/let [form__506__auto__ (clojure.core/read rdr__504__auto__ false (quote eof__507__auto__))] (if (clojure.core/= (quote eof__507__auto__) form__506__auto__) ret__505__auto__ (recur (clojure.core/eval form__506__auto__)))))) (catch java.lang.Throwable t__508__auto__ (clojure.core/println "[:unrepl.upgrade/failed]") (throw t__508__auto__))))