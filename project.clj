(defproject blp-loyalty "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]

                 [com.bloomberglabs/blpapi "3.7.1"]
                 ]
    :repositories [["ghost4j" "http://repo.ghost4j.org/maven2/releases"]
                 ["local" {:url "http://admiral-maven.s3-website-ap-northeast-1.amazonaws.com/repo/"
                           :checksum :ignore}
                           ]]
  )
