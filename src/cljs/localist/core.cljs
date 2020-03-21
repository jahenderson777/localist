(ns localist.core
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [localist.events :as events]
   [localist.views :as views]
   [localist.config :as config]
   [localist.re-frame-firebase :as firebase]
   ))

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

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn init []
  (re-frame/dispatch-sync [:initialize-db])
  (dev-setup)
  (firebase/init :firebase-app-info      firebase-app-info
                 ; See: https://firebase.google.com/docs/reference/js/firebase.firestore.Settings
                 ;:firestore-settings     {:timestampsInSnapshots true}
                 :get-user-sub           [:user]
                 :set-user-event         [:set-user]
                 :default-error-handler  [:firebase-error])
  (mount-root))
