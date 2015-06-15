(ns circulure.core
  (:gen-class)
  (:require
    [cemerick.friend :as friend]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [compojure.core :refer [defroutes context GET POST DELETE PUT]]
    [compojure.route :as route]
    [environ.core :refer [env]]
    [image-resizer.format :refer [as-file]]
    [image-resizer.core :refer [resize]]

    [ring.util.response :as resp]
    [ring.util.request :refer [path-info]]
    (ring.middleware [multipart-params :refer [wrap-multipart-params]]
                     [session :refer [wrap-session]]
                     [params :refer [wrap-params]]
                     [keyword-params :refer [wrap-keyword-params]]
                     [json :refer [wrap-json-params]])
    [ring.adapter.jetty :refer [run-jetty]]

    [circulure.db :as db]
    [circulure.scheduler :as scheduler]
    [circulure.auth :as auth]
    [circulure.util :refer [build-full-url]])
  (:import
    org.bson.types.ObjectId
    javax.xml.bind.DatatypeConverter
    java.security.MessageDigest
    java.security.DigestInputStream))


;; JSON Response

(declare clean-object-ids)

(defn- clean-object-id [ acc [k v]]
  (assoc acc
         (if (= k :_id) :id k)
         (cond
           (instance? ObjectId v) (str v)
           (map? v) (clean-object-ids v)
           (coll? v) (map clean-object-ids v)
           :else v)))

(defn clean-object-ids [d]
  (cond
    (map? d) (reduce clean-object-id {} d )
    (coll? d) (map clean-object-ids d)
    (instance? com.mongodb.WriteResult d) nil
    :else d))

(defn json-response [data]
  {:body (json/write-str (clean-object-ids data))
   :status 200
   :headers {"Content-Type" "application/json"} })


;; Endpoints

(defn get-account-info [{account :account :as req}]
  (if account
    {:id (:_id account)
     :users (for [user (db/get-users-by-account account)]
              {:user_id (:twitter_user_id user)
               :user_screen_name (:twitter_screen_name user)
               :profile_image_url (:profile_image_url user)
               :name (:twitter_screen_name user)
               :id (:_id user)
               })
     }
    {:loggedin false}))

;; GET /posts
(defn get-all-posts [{user :user :as req}]
  (db/get-posts user))

;; POST /posts
(defn create-post [{user :user {:keys [picture time status]} :params :as req}]
  (db/put-post! {:user (:_id user)
                 :time (if (= time "now") 0 (Integer/parseInt time))
                 :status status
                 :type (if picture "post_with_media" "post")
                 :picture picture}))

;; DELETE /posts/:id
(defn delete-post [{user :user {post-id :id} :params :as req}]
  (db/delete-post! user post-id))

;; PUT /posts/:id
(defn update-post [{user :user {post-id :id post-time :time :as params} :params :as req}]
  (if (= post-time "now")
    (db/move-to-queue user post-id)))

;; POST /times
(defn update-times [{user :user {posts :posts} :params}]
  (doall (for [{post-id :id post-time :time} posts]
              (db/update-queue-time user post-id post-time)))
  {:success true})

;; GET /settings
(defn get-settings [{account :account}]
  (dissoc account :users))

;; POST /settings
(defn update-settings [{account :account {email :email} :params}]
  (db/update-account-email account email))

;; GET /counter
(defn get-counter [req]
  {:count (db/post-count)})



(defn md5-file [f]
  (let [md (MessageDigest/getInstance "MD5")
        dis (DigestInputStream. (io/input-stream f) md)
        ]
    (while (not (= (.read dis) -1)))
    (-> (.digest md)
        (DatatypeConverter/printHexBinary)
        (.toUpperCase))))

;; POST /upload
(defn handle-upload [{{{filename :filename tempfile :tempfile} "userfile"} :multipart-params
                      {acct-id :_id} :account
                      :as req}]
  (.mkdir (io/file "resources/uploads")) ; For first run
  (.mkdir (io/file "resources/uploads/" (str acct-id)))
  (let [file-md5      (md5-file tempfile)
        dirname       (str "uploads/" acct-id "/") 
        destfilename  (str dirname file-md5 "-" filename)
        thumbnailpath (as-file (resize tempfile 100 100)
                               (str "resources/" destfilename))
        thumbnailname (.getName (io/file thumbnailpath)) ]

    (io/copy tempfile (io/file (str "resources/" destfilename )))
    {:url (build-full-url req (str "/" destfilename))
     :thumbnail (build-full-url req (str "/uploads/" acct-id "/" thumbnailname))}))


;; Routes

(defmacro r [method route handler]
  `(~method ~route req# (friend/authorize #{::auth/user} (json-response (~handler req#)))))

(defroutes api-routes
  (r GET "/posts" get-all-posts)
  (r POST "/posts" create-post)
  (r DELETE "/posts/:id" delete-post)
  (r PUT "/posts/:id" update-post)
  (r POST "/times" update-times)
  (r GET "/settings" get-settings)
  (wrap-multipart-params (r POST "/upload" handle-upload))
  (r POST "/settings" update-settings))

(defroutes app-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/api/oauth.php" req (json-response (get-account-info req)))
  (GET "/api/counter" req (json-response (get-counter req)))
  (r GET "/authorized" #(str "HELLO, FRIEND!" %))
  (context "/api" req
      (friend/wrap-authorize api-routes #{::auth/user}))
  (route/resources "/" {:root "Circular"})
  (route/resources "/uploads" {:root "uploads"})
  )


;; Handler

(def app
  (-> #'app-routes 
    (auth/wrap-auth)
    (wrap-keyword-params)
    (wrap-params)
    (wrap-json-params)
    (wrap-session)))


(defn -main []
  (scheduler/run-scheduler)
  (run-jetty #'app {:port (Integer/parseInt (:port env "8080")) :join? false}))


(comment
  (run-jetty #'app {:port 8080 :join? false}))
