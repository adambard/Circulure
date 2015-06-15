(ns circulure.db
  (:require [monger.core :as mg]
            [monger.collection :as mongo]
            [circulure.twitter :as tw]))

(def conn (mg/connect))
(def db (mg/get-db conn "circulure"))

;; Coerce to object-id
(defn object-id [it]
  (if (instance? org.bson.types.ObjectId it)
    it
    (org.bson.types.ObjectId. (str it))))


;; Users and accounts

(defn user-from-twitter-response
  [{:keys [oauth_token
           oauth_token_secret
           user_id
           screen_name]}]
  (tw/fill-user-object
    {:twitter_access_token oauth_token
     :twitter_access_token_secret oauth_token_secret
     :twitter_user_id user_id
     :twitter_screen_name screen_name}))


(defn get-users-by-account [account]
  (mongo/find-maps db "users" {:_id {:$in (map object-id (:users account))}}))


(defn get-user-by-twitter-user-id [user-id]
  (mongo/find-one-as-map db "users" {:twitter_user_id user-id}))


(defn put-user! [user]
  (if (:_id user)
    (do
      (mongo/update db "users" {:_id (object-id (:_id user))} user)
      user)
    (mongo/insert-and-return db "users" user)))


(defn get-or-create-account-by-user [user]
  (if-let [account (mongo/find-one-as-map db "accounts" {:users [(object-id (:_id user))]})]
    account
    (do
      (mongo/insert db "accounts" {:users [(object-id (:_id user))]})
      (get-or-create-account-by-user user))))



;; circulure.posts

(defn get-posts [user]
  (mongo/find-maps db "posts" {:user (object-id (:_id user))}))


(defn get-post [user ^String post-id]
  (mongo/find-one-as-map db "posts" {:user (object-id (:_id user))
                                     :_id (object-id post-id)}))


(defn put-post! [post]
  (if (:_id post)
    (do (mongo/update db "posts" {:_id (object-id (:_id post))} post)
        post)
    (mongo/insert-and-return db "posts" post)))


(defn delete-post! [user post-id]
  (mongo/remove db "posts" {:user (object-id (:_id user))
                            :_id (object-id post-id)}))


(defn post-count []
  (mongo/count db "posts"))


;; circulure.queue

(defn put-queued-post! [post]
  (mongo/insert db "queue" post))


(defn move-to-queue [user post-id]
  (when-let [post (get-post user post-id)]
    (delete-post! user post-id)
    (put-queued-post! post)))


(defn update-queue-time [user post-id post-time]
  (mongo/update db "posts" {:_id (object-id post-id)
                            :user (object-id (:_id user))}
                {:$set {:time post-time}}))

(defn update-account-email [account email]
  (mongo/update db "accounts" {:_id (object-id (:_id account))}
                (if email
                  {:$set {:email email}}
                  {:$unset {:email true}})))
