(set-env!
 :source-paths #{"src" "test"}
 :resource-paths #{"resources"}
 :dependencies '[;Tasks
                 [pandeiro/boot-http "0.6.3-SNAPSHOT"]
                 [adzerk/boot-test "1.0.4" :scope "test"]

                 ; Clojure
                 [org.clojure/clojure "1.6.0"]
                 [environ "1.0.0"]
                 [ring "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [compojure "1.3.4"]
                 [javax.servlet/servlet-api "2.5"]
                 [org.clojure/data.json "0.2.6"]
                 [http-kit "2.1.18"]
                 [overtone/at-at "1.2.0"]
                 [com.taoensso/timbre "3.4.0"]
                 [com.novemberain/monger "2.1.0"]

                 [twitter-api "0.7.8"]
                 [image-resizer "0.1.6"]

                 [com.cemerick/friend "0.2.1" :exclusions [net.sourceforge.nekohtml/nekohtml]]
                 [friend-oauth2 "0.1.3"]
                 [clj-oauth "1.5.2"]
                 [org.apache.httpcomponents/httpclient "4.5"] 
                 [org.clojure/core.cache "0.6.3"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
])

(require '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-test :refer [test]])

(deftask dev []
  (comp
    (repl)
    (watch)
    (test)
    ))

(deftask package []
(comp
  (aot :all true)
  (pom :project 'circulure :version "0.1.0-STANDALONE")
  (uber :exclude #{#"(?i)^META-INF/[^/]*.(MF|SF|RSA|DSA)$"
                   #"^((?i)META-INF)/.*pom.(properties|xml)$"
                   #"(?i)^META-INF/INDEX.LIST$"
                   #"(?i)^META-INF/LICENSE.*$"})
  (jar :main 'circulure.core)))
