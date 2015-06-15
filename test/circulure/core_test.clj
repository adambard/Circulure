(ns circulure.core-test
  (:require [clojure.test :refer [is deftest]]
            [cemerick.friend :as friend]
            [oauth.client :as oauth]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [circulure.auth :refer [twitter-auth-workflow]]
            ))


(deftest my-test
  (with-redefs [oauth/request-token (fn [& args] {:oauth_token "tok"
                                                  :oauth_token_secret "sec"
                                                  :oauth_callback_confirmed "true"})
                oauth/access-token (fn [& args] {:oauth_token "HEY HEY HEY"})
                ]
    (let [workflow (twitter-auth-workflow
                     {
                     :consumer-key ""
                     :consumer-secret ""
                     :oauth-callback-uri {:url "http://example.com/oauth/callback"
                                          :path "/oauth/callback"}
                     :credential-fn (fn [access-token] {:identity (:oauth_token access-token)
                                                        :resp access-token
                                                        :roles #{::user}
                                                        :complete "HECKYES"})}
                     )
          handler (-> (fn [req] {:body req :status 200 :headers {}})
                    (friend/authenticate {:workflows [workflow]})
                    (wrap-keyword-params)
                    (wrap-params)
                    )
          req {:server-port 80 :server-name "example.com" :scheme "http" :request-method :get :headers {} :session {} :query-string ""}
          login-resp (handler (assoc req :uri "/login" :query-string ""))
          _ (prn (:session login-resp))
          ]

      (is (= (-> (handler req) :session ::friend/identity :current) nil))
      (is (= (-> login-resp :headers (get "Location")) "https://api.twitter.com/oauth/authorize?oauth_token=tok"))
      (is (= (-> (handler (assoc req
                                 :uri "/oauth/callback"
                                 :query-string "&oauth_verifier=veri"
                                 :session (:session login-resp)))
                 :session ::friend/identity :current) "HEY HEY HEY"))))

  )

