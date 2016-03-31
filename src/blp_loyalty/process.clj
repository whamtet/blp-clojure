(ns blp-loyalty.process)
(require '[blp-loyalty.core :as core])

(def companies (.split (slurp "resources/companies2.csv") "\r\n"))
(sort companies)

(def c (take 2 companies))

(defn get-des [{c "CIE_DES_BULK[]"}]
  (apply str
         (interpose "\n"
                    (map #(-> % first second) c))))

(defn get-desciptions [companies]
  (core/value-map get-des (core/latest-request companies ["CIE_DES_BULK"])))

(get-desciptions c)

(def all-des (get-desciptions companies))
(def data (core/latest-request companies ["SECURITY_NAME" "CUR_MKT_CAP"]))

(import java.awt.datatransfer.StringSelection)
(import java.awt.Toolkit)

(defn to-clipboard [s]
  (let [
        selection (StringSelection. s)
        clipboard (.getSystemClipboard (Toolkit/getDefaultToolkit))
        ]
    (.setContents clipboard selection selection)))

(defn to-csv [c]
  (apply str (interpose "\n"
                        (map
                         #(apply str (interpose "\t" %))
                         c))))

(to-clipboard
 (to-csv
  (cons
   ["Bloomberg Code" "Name" "Market Cap" "Description"]
   (sort-by first
    (map
     (fn [[company description]]
       (let [
             {:strs [SECURITY_NAME CUR_MKT_CAP]} (data company)
             ]
         [company SECURITY_NAME CUR_MKT_CAP (.replace description "\n" " ")]))
     all-des)))))
