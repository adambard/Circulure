(ns circulure.util)

(defn build-full-url [req path]
  (str (name (:scheme req)) "://"
       (:server-name req)
       (if (= 80 (:server-port req))
         ""
         (str ":" (:server-port req)))
       path))
