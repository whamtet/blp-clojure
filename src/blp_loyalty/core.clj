(ns blp-loyalty.core)

(import com.bloomberglp.blpapi.CorrelationID)
(import com.bloomberglp.blpapi.Element)
(import com.bloomberglp.blpapi.Event)
(import com.bloomberglp.blpapi.Event$EventType)
(import com.bloomberglp.blpapi.Message)
(import com.bloomberglp.blpapi.MessageIterator)
(import com.bloomberglp.blpapi.Name)
(import com.bloomberglp.blpapi.Request)
(import com.bloomberglp.blpapi.Service)
(import com.bloomberglp.blpapi.Session)
(import java.util.Calendar)

(defn value-map [f m]
  (zipmap (keys m) (map f (vals m))))

(defn value-filter [f m]
  (into {}
        (for [[k v] m :when (f v)]
          [k v])))

(defn reduce-map
  ([vs] (reduce-map {} vs))
  ([m vs]
   (reduce
    (fn [m v]
      (assoc-in m (pop v) (peek v))) m vs)))

(defn date []
  (let [
        cal (Calendar/getInstance)
        ]
    [(.get cal Calendar/YEAR) (inc (.get cal Calendar/MONTH)) (.get cal Calendar/DATE)]))

(defn new-session []
  (doto (Session.)
    .start
    (.openService "//blp/refdata")))

(defonce session (atom (new-session)))

(defmacro add-items [items element-name]
  `(let [securities-el# (.getElement ~'request ~element-name)]
     (doseq [item# ~items]
       (.appendValue securities-el# item#))))

(def session-lock (Object.))

(defn date->str [[year month date]]
  (format "%04d%02d%02d" year month date))

(defn filter-messages [event correlation-id]
  (filter #(= correlation-id (.correlationID %)) (iterator-seq (.messageIterator event))))

(defn assoc-in-last [m [k & rest] v]
  (if rest
    (let [
          k (if (vector? m) (dec (count m)) k)
          ]
      (assoc m k (assoc-in-last (get m k) rest v)))
    (if (vector? m)
      (conj m v)
      (assoc m k v))))

(defn get-v [line]
  (let [
        v (-> line (.split " = ") second)
        ]
    (if v
      (try
        (Long/parseLong v)
        (catch Exception e
          (try
            (Double/parseDouble v)
            (catch Exception e
              (.replace v "\"" ""))))))))

(defn parse-response [response]
  ;  (println response)
  (loop [
         lines (->> response str clojure.string/split-lines (drop 1) drop-last)
         done {}
         stack []
         ]
    (if (empty? lines)
      done
      (let [
            line (first lines)
            ;            k (re-find #"[a-zA-Z_\[\]0-9]+" line)
            k (-> line (.split " = ") first .trim)
            new-stack (conj stack k)
            ]
        (cond
         (.contains line "[]")
         (recur (rest lines) (assoc-in-last done new-stack []) new-stack)
         (.contains line "{")
         (if (and (not-empty stack) (.contains (peek stack) "[]"))
           (recur (rest lines) (assoc-in-last done new-stack {}) new-stack)
           (recur (rest lines) done new-stack))
         (.contains line "}")
         (recur (rest lines) done (pop stack))
         :default
         (recur (rest lines) (assoc-in-last done new-stack (get-v line)) stack))))))

(defn request [securities fields & [start-date end-date periodicity]]
  ;periodicity is DAILY WEEKLY MONTHLY QUARTERLY SEMI_ANNUALLY YEARLY
  (locking session-lock
    (try
      (let [
            service (.getService @session "//blp/refdata")
            request-type (if start-date "HistoricalDataRequest" "ReferenceDataRequest")
            request (.createRequest service request-type)
            ]
        (add-items securities "securities")
        (add-items fields "fields")
        (when start-date
          (.set request "startDate" (date->str start-date))
          (.set request "endDate" (date->str end-date))
          (.set request "periodicitySelection" periodicity)
          (.set request "adjustmentSplit" false)
          (.set request "adjustmentFollowDPDF" false)
          (.set request "adjustmentNormal" false)
          (.set request "adjustmentAbnormal" false)
          )
        (let [
              correlation-id (.sendRequest @session request nil)
              ]
          (loop [
                 done []
                 ]
            (let [
                  event (.nextEvent @session)
                  done (concat done (filter-messages event correlation-id))
                  ]
              (if (= Event$EventType/RESPONSE (.eventType event))
                (map parse-response done)
                (recur done))))))
      (catch IllegalStateException e
        (reset! session (new-session))
        (request securities fields start-date end-date periodicity)))))

(defn latest-request [securities fields]
  (reduce-map
   (for [
         response (request securities fields)
         security-data (response "securityData[]")
         ]
     [(security-data "security") (security-data "fieldData")])))
