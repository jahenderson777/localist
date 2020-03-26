(ns localist.events
  (:require
   [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx]]
   [localist.re-frame-firebase.firestore :as firestore]
   [localist.db :as db]
   ))

(reg-event-db
 :initialize-db
 (fn [_ _]
   db/default-db))

(reg-event-db :assoc
              (fn [db [_ & kvs]]
                (apply assoc db kvs)))

(reg-event-fx
 :sign-in-facebook
 (fn [_ _] {:firebase/facebook-sign-in {:sign-in-method :popup}}))

(reg-event-fx
 :sign-out
 (fn [_ _] {:firebase/sign-out nil}))

(reg-event-fx
 :send-password-reset-email
 (fn [{:keys [db]} _] 
   (let [{:keys [temp-email]} db] 
     {:firebase/send-password-reset-email {:email temp-email
                                           :on-complete #(js/alert "Password reset email sent")}})))

(reg-event-db
 :set-user
 (fn [db [_ user]]
   (assoc db :user user)))

(reg-event-db
 :toggle-checked
 (fn [db [_ id]]
   (update db :checked-items (fn [checked-items]
                               (if (contains? checked-items id)
                                 (disj checked-items id)
                                 (conj checked-items id))))))

(reg-event-fx
 :create-by-email
 (fn [{:keys [db]} _]
   (let [{:keys [temp-email temp-password temp-password-confirm]} db]
     {:firebase/email-create-user {:email temp-email :password temp-password}
      :db (dissoc db :temp-email :temp-password :temp-password-confirm :temp-name)})))


(reg-event-fx
 :sign-in-by-email
 (fn [{:keys [db]} [_]]
   (let [{:keys [temp-email temp-password]} db]
     {:firebase/email-sign-in {:email temp-email :password temp-password}
      :db (dissoc db :temp-email :temp-password :temp-password-confirm :temp-name)})))


(reg-event-fx
 :firestore-add-item
 (fn [{:keys [db]} [_ uid prefix]]
   (let [{:keys [user]} db
         temp-item-kw (keyword prefix (str "temp-item-new-" uid))
         temp-item (get db temp-item-kw)]
     (println [:users uid prefix])
     {:firestore/add {:path [:users uid prefix]
                      :data {:timestamp (firestore/server-timestamp)
                             ;:user (firestore/clj->DocumentReference ["users" uid])
                             ;:type prefix
                             :item temp-item}
                      :on-success [:assoc temp-item-kw nil]
                      :on-failure #(prn "Error:" %)}
      :db (dissoc db :edit-item temp-item-kw :show-menu)})))

(reg-event-fx
 :firestore-update-item
 (fn [{:keys [db]} [_ uid prefix]]
   (let [{:keys [user edit-item]} db
         temp-item-kw (keyword prefix (str "temp-item-" uid))
         temp-item (get db temp-item-kw)]
     {:firestore/update {:path [:users uid prefix (:id edit-item)]
                      :data (assoc (:data edit-item)
                                   :item temp-item) 
                      :on-success [:assoc temp-item-kw nil :edit-item nil]
                      :on-failure #(prn "Error:" %)}})))

(reg-event-fx
 :account-edit-save
 (fn [{:keys [db]} _]
   (let [{:keys [edit-account temp-name temp-address temp-phone temp-dropoff temp-postcode]} db]
     {:firestore/set {:path [:users edit-account]
                         :data (merge (when temp-name {"name" temp-name})
                                      (when temp-address {"address" temp-address})
                                      (when temp-phone {"phone" temp-phone})
                                      (when temp-dropoff {"dropoff" temp-dropoff})
                                      (when temp-postcode {"postcode" temp-postcode})
                                      )
                         :on-success [:assoc :edit-account nil]
                         :on-failure #(prn "Error:" %)}})))

(reg-event-fx
 :firestore-delete-item
 (fn [{:keys [db]} [_ uid prefix]]
   (let [{:keys [user temp-item edit-item]} db]
     {:firestore/delete {:path [:users uid prefix (:id edit-item)]
                         ;:on-success [:assoc :temp-item nil :edit-item nil]
                         :on-failure #(prn "Error:" %)}
      :db (dissoc db :edit-item :temp-item)})))

(reg-event-fx
 :firestore-get
 (fn [{:keys [db]} [_ query]]
   {:firestore/get query}))

(reg-event-fx
 :upload
 (fn [{:keys [db]} [_ opts]]
   {:firebase/upload opts}))

#_(reg-event-fx
 :firestore-delete-item
 (fn [{:keys [db]} [_ id]]
   (let [{:keys [user]} db
         {:keys [uid]} user]
     {:firestore/delete {:path [:users uid :my-list id]                       
                      ;:on-success [:assoc :temp-item nil]
                      :on-failure #(prn "Error:" %)}})))

(reg-event-fx
 :firebase-error
 (fn [_ [_ & v]]
   (println "error")
   (cljs.pprint/pprint v)
   (js/alert v)))