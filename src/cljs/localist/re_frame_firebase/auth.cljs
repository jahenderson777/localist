;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns localist.re-frame-firebase.auth
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require
   [clojure.spec.alpha :as s]
   [re-frame.core :as re-frame]
   [iron.re-utils :refer [>evt]]
   ["firebase" :as firebase]
   ["firebase/auth" :as firebase-auth]
   [localist.re-frame-firebase.core :as core]))


;(def firestore (js/require "firebase/auth"))

(defn- user
  "Extract interesting details from the Firebase JS user object."
  [firebase-user]
  (when firebase-user
    {:uid           (.-uid firebase-user)
     :provider-data (.-providerData firebase-user)
     :display-name  (.-displayName firebase-user)
     :photo-url     (.-photoURL firebase-user)
     :email         (let [provider-data (.-providerData firebase-user)]
                      (when-not (empty? provider-data)
                        (-> provider-data first .-email)))}))

(defn- set-user
  [firebase-user]
  (-> firebase-user
      (user)
      (core/set-current-user)))

(defn- init-auth []
  (.onAuthStateChanged
   (.auth firebase)
   set-user
   (core/default-error-handler))

  (-> (.auth firebase)
      (.getRedirectResult)
      (.then (fn on-user-credential [user-credential]
               (-> user-credential
                   (.-user)
                   set-user)))
      (.catch (core/default-error-handler))))

(defn ^:private sign-in-fns [sign-in-method auth-provider]
  (case sign-in-method
    :popup (memfn signInWithPopup auth-provider)
    :redirect (memfn signInWithRedirect auth-provider)))

(defn- maybe-link-with-credential
  [pending-credential user-credential]
  (when (and pending-credential user-credential)
    (when-let [firebase-user (.-user user-credential)]
      (-> firebase-user
          (.linkWithCredential pending-credential)
          (.catch (core/default-error-handler))))))


(defn debug-js-obj [obj]
  (println obj (type obj))
  (cljs.pprint/pprint (for [prop (js->clj (.getOwnPropertyNames js/Object obj))]
                        [prop (type (aget obj prop))])))

(defn- oauth-sign-in
  [auth-provider opts]
  (let [{:keys [sign-in-method scopes custom-parameters link-with-credential]
         :or {sign-in-method :redirect}} opts]
    (debug-js-obj auth-provider)
    
    (doseq [scope scopes]
      (.addScope auth-provider scope))

    (when custom-parameters
      (.setCustomParameters auth-provider (clj->js custom-parameters)))

    (-> (.auth firebase)
        (.signInWithRedirect auth-provider)
        (.then (partial maybe-link-with-credential link-with-credential))
        (.catch (core/default-error-handler)))
    #_(>evt [(core/default-error-handler)
             (js/Error. (str "Unsupported sign-in-method: " sign-in-method ". Either :redirect or :popup are supported."))])))



(defn google-sign-in
  [opts]
  (let [google-auth-provider (.. firebase -auth -GoogleAuthProvider.)]
    (oauth-sign-in (google-auth-provider.) opts)))


(defn facebook-sign-in
  [opts]
  (let [facebook-auth-provider (.. firebase -auth -FacebookAuthProvider)]
    (oauth-sign-in (facebook-auth-provider.) opts)))

(defn twitter-sign-in
  [opts]
  (oauth-sign-in (.. firebase -auth -TwitterAuthProvider.) opts))


(defn github-sign-in
  [opts]
  (oauth-sign-in (.. firebase -auth -GithubAuthProvider.) opts))


(defn email-sign-in [{:keys [email password]}]
  (-> (.auth firebase)
      (.signInWithEmailAndPassword email password)
      (.then set-user)
      (.catch (core/default-error-handler))))


(defn email-create-user [{:keys [email password]}]
  (-> (.auth firebase)
      (.createUserWithEmailAndPassword email password)
      (.then set-user)
      (.catch (core/default-error-handler))))

(defn send-password-reset-email [{:keys [email on-complete]}]
  (-> (.auth firebase)
      (.sendPasswordResetEmail email)
      (.then on-complete)
      (.catch (core/default-error-handler))))

(defn anonymous-sign-in [opts]
  (-> (.auth firebase)
      (.signInAnonymously)
      (.then set-user)
      (.catch (core/default-error-handler))))


(defn custom-token-sign-in [{:keys [token]}]
  (-> (.auth firebase)
      (.signInWithCustomToken token)
      (.then set-user)
      (.catch (core/default-error-handler))))


(defn init-recaptcha [{:keys [on-solve container-id]}]
  (let [recaptcha (.. firebase -auth -RecaptchaVerifier.
                   container-id
                   (clj->js {:size     "invisible"
                             :callback #(re-frame/dispatch on-solve)}))]
    (swap! core/firebase-state assoc
           :recaptcha-verifier recaptcha)))


(defn phone-number-sign-in [{:keys [phone-number on-send]}]
  (if-let [verifier (:recaptcha-verifier @core/firebase-state)]
    (-> (.auth firebase)
        (.signInWithPhoneNumber phone-number verifier)
        (.then (fn [confirmation]
                 (when on-send
                   (re-frame/dispatch on-send))
                 (swap! core/firebase-state assoc
                        :recaptcha-confirmation-result confirmation)))
        (.catch (core/default-error-handler)))
    (.warn js/console "Initialise reCaptcha first")))


(defn phone-number-confirm-code [{:keys [code]}]
  (if-let [confirmation (:recaptcha-confirmation-result @core/firebase-state)]
    (-> confirmation
        (.confirm code)
        (.then set-user)
        (.catch (core/default-error-handler)))
    (.warn js/console "reCaptcha confirmation missing")))


(defn sign-out []
  (-> (.auth firebase)
      (.signOut)
      (.catch (core/default-error-handler))))
