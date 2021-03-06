(ns slacker.serialization
  (:require [clojure.tools.logging :as logging]
            [clojure.java.io :refer [copy]]
            [slacker.common :refer :all]
            [clojure.edn :as edn])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.nio ByteBuffer]
           [java.nio.charset Charset]
           [java.util.zip DeflaterInputStream InflaterInputStream]))

(defn- bytebuffer-bytes [^ByteBuffer data]
  (let [bs (byte-array (.remaining data))]
    (.get data bs)
    bs))

(defn- resolve-by-name [ns mem]
  @(ns-resolve (symbol ns) (symbol mem)))

(defmulti serialize
  "serialize clojure data structure to bytebuffer with
  different types of serialization"
  (fn [f _ & _] (if (.startsWith (name f) "deflate")
                 :deflate f)))
(defmulti deserialize
  "deserialize clojure data structure from bytebuffer using
  matched serialization function"
  (fn [f _ & _] (if (.startsWith (name f) "deflate")
                 :deflate f)))

(try
  (require '[carbonite.api])
  (require '[slacker.serialization.carbonite])

  (let [read-buffer (resolve-by-name "carbonite.api" "read-buffer")
        write-buffer (resolve-by-name "carbonite.api" "write-buffer")
        carb-registry (resolve-by-name "slacker.serialization.carbonite" "carb-registry")]
    (defmethod deserialize :carb
      ([_ data] (deserialize :carb data :buffer))
      ([_ data it]
       (if (= it :buffer)
         (read-buffer @carb-registry data)
         (deserialize :carb (ByteBuffer/wrap data) :buffer))))
    (defmethod serialize :carb
      ([_ data] (serialize :carb data :buffer))
      ([_ data ot]
       (if (= ot :bytes)
         (write-buffer @carb-registry data *ob-init* *ob-max*)
         (ByteBuffer/wrap (serialize :carb data :bytes))))))

  (catch Throwable _
    (logging/info "Disable carbonite support.")))

(try
  (require 'cheshire.core)

  (let [parse-string (resolve-by-name "cheshire.core" "parse-string")
        generate-string (resolve-by-name "cheshire.core" "generate-string")]
    (defmethod deserialize :json
      ([_ data] (deserialize :json data :buffer))
      ([_ data it]
       (let [jsonstr
             (case it
               :buffer (.toString (.decode (Charset/forName "UTF-8") data))
               :bytes (String. ^bytes ^String data "UTF-8")
               :string data)]
         (if *debug* (println (str "dbg:: " jsonstr)))
         (parse-string jsonstr true))))

    (defmethod serialize :json
      ([_ data] (serialize :json data :buffer))
      ([_ data ot]
       (let [jsonstr (generate-string data)]
         (if *debug* (println (str "dbg:: " jsonstr)))
         (case ot
           :buffer (.encode ^Charset (Charset/forName "UTF-8") ^String jsonstr)
           :string jsonstr
           :bytes (.getBytes ^String jsonstr "UTF-8"))))))

  (catch Throwable _
    (logging/info  "Disable cheshire (json) support.")))


(defmethod deserialize :clj
  ([_ data] (deserialize :clj data :buffer))
  ([_ data ot]
   (let [cljstr
         (case ot
           :buffer (.toString (.decode (Charset/forName "UTF-8") data))
           :bytes (String. ^bytes ^String data "UTF-8")
           :string data)]
     (if *debug* (println (str "dbg:: " cljstr)))
     (edn/read-string cljstr))))

(defmethod serialize :clj
  ([_ data] (serialize :clj data :buffer))
  ([_ data ot]
   (let [cljstr (pr-str data)]
     (if *debug* (println (str "dbg:: " cljstr)))
     (case ot
       :buffer (.encode (Charset/forName "UTF-8") cljstr)
       :string cljstr
       :bytes (.getBytes cljstr "UTF-8")))))

(try
  (require '[taoensso.nippy])
  (try
    (require '[slacker.serialization.nippy])
    (catch Throwable _
      (logging/info "Nippy version below 2.7.1. Disable stacktrace transfer support")))

  (let [thaw (resolve-by-name "taoensso.nippy" "thaw")
        freeze (resolve-by-name "taoensso.nippy" "freeze")]

    (defmethod deserialize :nippy
      ([_ data] (deserialize :nippy data :buffer))
      ([_ data ot]
       (if (= ot :bytes)
         (thaw data)
         (thaw (bytebuffer-bytes data)))))

    (defmethod serialize :nippy
      ([_ data] (serialize :nippy data :buffer))
      ([_ data ot]
       (let [bytes (freeze data)]
         (case ot
           :bytes bytes
           :buffer (ByteBuffer/wrap bytes))))))

  (catch Throwable _
    (logging/info "Disable nippy support.")))

(defmethod serialize :deflate
  ([dct data] (serialize dct data :buffer))
  ([dct data ot]
   (let [ct (keyword (subs (name dct) 8))
         sdata (serialize ct data :bytes)
         deflater (DeflaterInputStream.
                    (ByteArrayInputStream. sdata))
         out-s (ByteArrayOutputStream.)
         out-bytes (do
                     (copy deflater out-s)
                     (.toByteArray out-s))]
     (case ot
       :buffer (ByteBuffer/wrap out-bytes)
       :bytes out-bytes))))

(defmethod deserialize :deflate
  ([dct data] (deserialize dct data :buffer))
  ([dct data ot]
   (let [ct (keyword (subs (name dct) 8))
         in-bytes (case ot
                    :buffer (bytebuffer-bytes data)
                    :bytes data)
         inflater (InflaterInputStream.
                   (ByteArrayInputStream. in-bytes))
         out-s (ByteArrayOutputStream.)
         out-bytes (do
                     (copy inflater out-s)
                     (.toByteArray out-s))]
     (deserialize ct out-bytes :bytes))))
