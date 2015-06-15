(ns circulure.scheduler
  (:require [monger.core :as mg]
            [monger.collection :as mongo]
            [circulure.twitter :as tw]
            [circulure.db :refer [conn]]
            [clojure.core.async :refer [chan go <! >!]]
            ))

(def db (mg/get-db conn "circulure"))

(def currently-processing (atom #{}))

(defn now []
  (int (/ (System/currentTimeMillis) 1000)))


;; DB Stuff

(defn get-due-posts []
  (mongo/find-maps db "posts" {:time {:$lte (now)}}))

(defn move-to-queue [post]
  (mongo/remove db "posts" {:_id (:_id post)})
  (mongo/insert db "queue" post))

(defn get-queued []
  (mongo/find-maps db "queue"))

(defn move-to-archive [queued-post]
  (mongo/remove db "queue" {:_id (:_id queued-post)})
  (mongo/insert db "archive" queued-post) )

(defn get-user [user-id]
  (mongo/find-one-as-map db "users" {:_id user-id}))


;; 1) Move stuff to queue

(defn prepare-queued-posts []
  (doall (for [post (get-due-posts)]
           (move-to-queue post))))


;; 2) Send stuff in queue and archive it

(defn archive-item [item]
  (move-to-archive item)
  (swap! currently-processing disj (:_id item)))

(defn send-queued-item [item]
  (when-let [user (get-user (:user item))]
    (let [c (tw/update-status (get-user (:user item)) (:status item))]
      (go
        (let [result (<! c)]
          (archive-item (assoc item :api-result result)))))))

(defn send-queued-posts []
  (doall (for [item (get-queued)]
           (when-not (@currently-processing (:_id item))
             (swap! currently-processing conj (:_id item))
             (send-queued-item item)))))


(defn run-scheduler []
  (future
    (while true
      (prepare-queued-posts)
      (send-queued-posts)
      (Thread/sleep 60000) ; 1 minute
      )))



(comment
  (-main)
  (def queued (mongo/find-maps db "queue"))
  (prn queued)
  (send-queued-item (first queued))

  (-main)
  (require '[twitter.api.restful :as twitter])
  (mongo/find-maps db "users" {} {:_id true :twitter_access_token true})
  (mongo/remove db "users" {:_id (org.bson.types.ObjectId. "55797b3458f24f4fdf8bba99")})
  (def user (mongo/find-one-as-map db "users" {}))
  (identity user)
  (def creds (tw/user-creds user))
  )
