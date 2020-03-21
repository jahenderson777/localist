(ns localist.events
  (:require
   [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx]]
   [localist.db :as db]
   ))

(reg-event-db
 :initialize-db
 (fn [_ _]
   db/default-db))

(reg-event-db :assoc
              (fn [db [_ & kvs]]
                (apply assoc db kvs)))

;;; Simple sign-in event. Just trampoline down to the re-frame-firebase
;;; fx handler.
(reg-event-fx
 :sign-in
 (fn [_ _] {:firebase/google-sign-in {:sign-in-method :popup}}))


;;; Ditto for sign-out
(reg-event-fx
 :sign-out
 (fn [_ _] {:firebase/sign-out nil}))


;;; Store the user object
(reg-event-db
 :set-user
 (fn [db [_ user]]
   (assoc db :user user)))

;;; Create a new user
(reg-event-fx
 :create-by-email
 (fn [_ [_ email pass]]
   {:firebase/email-create-user {:email email :password pass}}))


;;; Sign in by email
(reg-event-fx
 :sign-in-by-email
 (fn [_ [_ email pass]]
   {:firebase/email-sign-in {:email email :password pass}}))

