(ns localist.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame :refer [dispatch]]
   [secretary.core :as secretary]
   [goog.events :as events]
   [localist.events]
   [localist.views :as views]
   [localist.config :as config]
   [localist.re-frame-firebase :as firebase]
   )
   (:import [goog History]
            [goog.history EventType]))

    ;; apiKey: "AIzaSyA837oi7FajjTjpcv6SI7_jUglLkIx-wkc",
    ;; authDomain: "localist-e864f.firebaseapp.com",
    ;; databaseURL: "https://localist-e864f.firebaseio.com",
    ;; projectId: "localist-e864f",
    ;; storageBucket: "localist-e864f.appspot.com",
    ;; messagingSenderId: "842586756957",
    ;; appId: "1:842586756957:web:c50501fcec438dff13fab4",
    ;; measurementId: "G-S19TMXDBKF"

(defonce firebase-app-info
  {:apiKey "AIzaSyA837oi7FajjTjpcv6SI7_jUglLkIx-wkc"
   :authDomain "localist-e864f.firebaseapp.com"
   :databaseURL "https://localist-e864f.firebaseio.com"
   :projectId "localist-e864f"
   :storageBucket "localist-e864f.appspot.com"})

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn routes
  []
  ;(set! (.-hash js/location) "/")      ;; on app startup set location to "/"
  (secretary/set-config! :prefix "#")  ;; and don't forget about "#" prefix
  (defroute "/" [] (dispatch [:assoc :active-page :home]))
  (defroute "/login" [] (dispatch [:assoc :active-page :login]))
  (defroute "/register" [] (dispatch [:assoc :active-page :register]))
  (defroute "/settings" [] (dispatch [:assoc :active-page :settings]))
  (defroute "/editor" [] (dispatch [:assoc :active-page :editor]))
  (defroute "/editor/:slug" [slug] (dispatch [:assoc :active-page :editor :slug slug]))
  (defroute "/logout" [] (dispatch [:sign-out]))
  (defroute "/article/:slug" [slug] (dispatch [:assoc :active-page :article :slug slug]))
  (defroute "/:profile/favorites" [profile] (dispatch [:assoc :active-page :favorited :favorited (subs profile 1)]))
  (defroute "/:profile" [profile] (dispatch [:assoc :active-page :profile :profile profile]))
  (hook-browser-navigation!))



(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn init []
  (re-frame/dispatch-sync [:initialize-db])
  (re-frame/dispatch [:init-captcha])
  (dev-setup)
  (routes)
  (firebase/init :firebase-app-info      firebase-app-info
                 ; See: https://firebase.google.com/docs/reference/js/firebase.firestore.Settings
                 ;:firestore-settings     {:timestampsInSnapshots true}
                 :get-user-sub           [:user]
                 :set-user-event         [:set-user]
                 :default-error-handler  [:firebase-error])
  (mount-root))
