(ns circulure.twitter
  (:require [twitter.oauth :refer [make-oauth-creds]]
            [twitter.api.restful :as twitter] 
            [twitter.callbacks :as callbacks]
            [environ.core :refer [env]]
            [clojure.core.async :refer [chan go <! >!]]

            )
  (:import twitter.callbacks.protocols.AsyncSingleCallback)
  )


(defn user-creds [user]
  (make-oauth-creds
    (:twitter-consumer-key env) 
    (:twitter-consumer-secret env)
    (:twitter_access_token user)
    (:twitter_access_token_secret user)))

(defn get-user-info [user]
  (:body (twitter/users-show :oauth-creds (user-creds user) :params {:screen-name "adambard"})) 
  )


(defn fill-user-object [user]
  (let [user-info (get-user-info user)]
    (merge user
           (select-keys user-info [:profile_image_url :description]))))

(defn async-callback [c]
  (AsyncSingleCallback.
    (fn [& args] (go (>! c :ok)))
    (fn [& args] (go (>! c :failure)))
    (fn [& args] (go (>! c :exception)))))


(defn update-status [user status]
  (let [c (chan)]
    (twitter/statuses-update :oauth-creds (user-creds user)
                             :params {:status status}
                             :callbacks (async-callback c))
    c))
