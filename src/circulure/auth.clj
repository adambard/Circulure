(ns circulure.auth
  (:require
    [cemerick.friend :as friend]
    [cemerick.friend.workflows :refer [make-auth]]
    [environ.core :refer [env]]
    [oauth.client :as oauth]
    [ring.util.request :refer [path-info]]
    [clojure.data.json :as json]
    [circulure.db :as db]
    [circulure.util :refer [build-full-url]]
    ))


(def consumer
  (oauth/make-consumer
    (:twitter-consumer-key env)
    (:twitter-consumer-secret env)
    "https://api.twitter.com/oauth/request_token"
    "https://api.twitter.com/oauth/access_token"
    "https://api.twitter.com/oauth/authorize"
    :hmac-sha1))

(defn circular-oauth-workflow
  "Handle OAuth responses as expected by the Circular frontend, based on params:

  ?start=1: obtain the request token and return the authorization url
  ?oauth_verifier=?: When the user returns from twitter, get the access_token
                     and authenticate the user with friend"
  [credential-fn]
  (fn [{params :params :as req}]

    (when (= (path-info req) "/api/oauth.php")
      (cond

        ; Get the request token and get the redirect url
        (:start params)
        (let [request-token (oauth/request-token
                              consumer
                              (build-full-url req "/api/oauth.php"))
              approval-url (oauth/user-approval-uri
                             consumer
                             (:oauth_token request-token))]
          {:body (json/write-str {:authurl approval-url})
           :status 200
           :headers {"Content-Type" "application/json"}
           :session (assoc (:session req) :oauth-request-token request-token)})

        ; Log the user in after getting an access token
        (:oauth_verifier params)
        (when-let [request-token (get-in req [:session :oauth-request-token])]
          (when-let [access-token (oauth/access-token
                                    consumer
                                    request-token
                                    (get-in req [:params :oauth_verifier]))]
            (make-auth (credential-fn access-token))))))))


(defn credential-fn
  "Get (create if necessary) the user and account by the token from mongo"
  [oauth-token-response]
  (let [user (merge (db/get-user-by-twitter-user-id (:user_id  oauth-token-response))
                    (db/user-from-twitter-response oauth-token-response))
        user (db/put-user! user)
        account (db/get-or-create-account-by-user user)
        ]
    {:identity (:twitter_user_id user)
     :user user
     :account account
     :roles #{::user}}))


(defn wrap-user
  "A middleware that extracts the :user and :account from friends' credential
  array, and moves them up to the main request body"
  [handler]
  (fn [{{id-obj ::friend/identity} :session :as req}]
    (handler
      (if-let [id (-> id-obj :current)]
        (assoc req
               :user (-> id-obj :authentications (get id) :user)
               :account (-> id-obj :authentications (get id) :account))
        req))))


(defn wrap-auth
  "Add friend and wrap-user middlewares to handler"
  [handler]
  (-> handler
    (wrap-user)
    (friend/authenticate {:workflows [(circular-oauth-workflow credential-fn)]})))
