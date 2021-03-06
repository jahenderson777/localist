(ns localist.events
  (:require
   [clojure.string :as str]
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
 (fn [_ _] 
   {:firebase/facebook-sign-in {:sign-in-method :popup}}))

(reg-event-fx
 :sign-out
 (fn [{:keys [db]} _] 
   {:db (dissoc db :my-community :selected-uid)
    :firebase/sign-out nil}))

(reg-event-fx
 :send-password-reset-email
 (fn [{:keys [db]} _] 
   (let [{:keys [temp-email]} db] 
     {:firebase/send-password-reset-email {:email temp-email
                                           :on-complete #(js/alert "Password reset email sent")}})))

(reg-event-fx
 :set-user
 (fn [{:keys [db]} [_ user]]
   (let [uid (:uid user)
         {:keys [community-from-url]} db]
     (println :path-document [:users uid])
     (merge
      {:db (assoc db :user user)}
      (when uid
        {:firestore/on-snapshot {:path-document [:users uid]
                                 :on-next (fn [doc]
                                            (re-frame/dispatch [:received-user-data (:data doc)]))}})))))

(reg-event-fx
 :received-user-data
 (fn [{:keys [db]} [_ user-data]]
   (let [my-community (get user-data "community")
         my-uid (get-in db [:db :uid])
         address (get user-data "address")
         db-my-community (:my-community db)
         {:keys [community-from-url]} db]
     (println "received user data: " community-from-url my-community db-my-community)
     (merge
      (if (and community-from-url 
               (empty? my-community)
               (empty? db-my-community))
        {:dispatch [:firestore-set-community community-from-url]}
        {:db (assoc db 
                    :user-data user-data
                    :my-community my-community
                    :edit-account (when (empty? address)
                                    my-uid))})))))

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
   (let [{:keys [user my-community]} db
         temp-item-kw (keyword prefix (str "temp-item-new-" uid))
         temp-item (get db temp-item-kw)]
     (println [:users uid prefix])
     {:firestore/add {:path [:communities my-community :items]
                      :data {:timestamp (firestore/server-timestamp)
                             :user uid ;(firestore/clj->DocumentReference ["users" uid])
                             ;:type prefix
                             :item temp-item}
                      :on-success [:assoc temp-item-kw nil]
                      :on-failure #(prn "Error:" %)}
      :db (dissoc db :edit-item temp-item-kw :show-menu)})))

(reg-event-fx
 :firestore-update-item
 (fn [{:keys [db]} [_ uid prefix]]
   (let [{:keys [user edit-item my-community]} db
         temp-item-kw (keyword prefix (str "temp-item-" uid))
         temp-item (get db temp-item-kw)]
     {:firestore/update {:path [:communities my-community :items (:id edit-item)]
                         :data (assoc (:data edit-item)
                                      :item temp-item)
                         :on-success [:assoc temp-item-kw nil :edit-item nil]
                         :on-failure #(prn "Error:" %)}})))



(reg-event-fx
 :account-edit-save
 (fn [{:keys [db]} [_ uid]]
   (let [{:keys [edit-account temp-name temp-address temp-phone temp-dropoff temp-postcode temp-balance
                 has-store? temp-shop-name temp-opening-times temp-info my-community]} db

         my-account-data (merge (when temp-name {"name" temp-name})
                                (when temp-address {"address" temp-address})
                                (when temp-phone {"phone" temp-phone})
                                (when temp-dropoff {"dropoff" temp-dropoff})
                                (when temp-postcode {"postcode" temp-postcode}))
         my-account-update [:firestore/set {:path [:users uid]
                                            :data my-account-data
                                            :set-options {:merge true}}]

         my-community-specific-data (merge (when temp-balance {"balance" (js/Number.parseFloat temp-balance)}))
         my-community-specific-update [:firestore/set {:path [:communities my-community :users uid]
                                                       :data my-community-specific-data
                                                       :set-options {:merge true}}]

         my-shop-data (doto (merge (when temp-shop-name {"shop-name" temp-shop-name})
                                   (when temp-opening-times {"opening-times" temp-opening-times})
                                   (when temp-info {"info" temp-info}))
                        println)
         my-shop-update [:firestore/set {:path [:communities my-community :shops uid]
                                         :data my-shop-data
                                         :set-options {:merge true}}]]
     {:firestore/write-batch
      {:operations (remove nil? [(when my-account-data my-account-update)
                                 (when my-community-specific-data my-community-specific-update)
                                 (when (and has-store? my-shop-data) my-shop-update)
                                 ])
       :on-success #(re-frame/dispatch [:assoc :edit-account nil])
       :on-failure #(prn "Error:" %)}})))


(reg-event-fx
 :edit-community-save
 (fn [{:keys [db]} [_ community]]
   (let [{:keys [temp-community-name temp-info temp-paypal]} db
         data (merge (when temp-community-name {"name" temp-community-name})
                     (when temp-info {"info" temp-info})
                     (when temp-paypal {"paypal" temp-paypal}))]
     (when (seq data)
       {:firestore/set {:path [:communities community]
                        :data data
                        :set-options {:merge true}
                        :on-success #(re-frame/dispatch [:assoc :edit-community nil])}}))))


(reg-event-fx
 :firestore-set-community
 (fn [{:keys [db]} [_ community-id]]
   (let [{:keys [user]} db
         my-id (get user :uid)]
     {:firestore/set {:path [:users my-id]
                      :data {"community" community-id}
                      :set-options {:merge true}}})))

(reg-event-fx
 :volunteer-promote
 (fn [{:keys [db]} [_ uid]]
   (let [{:keys [my-community]} db]
     {:firestore/set {:path [:communities my-community]
                      :data {"volunteers" (firestore/array-union uid)}
                      :set-options {:merge true}}})))

(reg-event-fx
 :volunteer-demote
 (fn [{:keys [db]} [_ uid]]
   (let [{:keys [my-community]} db]
     {:firestore/set {:path [:communities my-community]
                      :data {"volunteers" (firestore/array-remove uid)}
                      :set-options {:merge true}}})))

(reg-event-fx
 :lock-order
 (fn [{:keys [db]} [_ uid]]
   (let [{:keys [my-community user]} db
         my-uid (get user :uid)]
     {:firestore/set {:path [:communities my-community :users uid]
                      :data {"locked-by" my-uid}
                      :set-options {:merge true}}})))

(reg-event-fx
 :unlock-order
 (fn [{:keys [db]} [_ uid]]
   (let [{:keys [my-community user]} db
         my-uid (get user :uid)]
     {:firestore/set {:path [:communities my-community :users uid]
                      :data {"locked-by" nil}
                      :set-options {:merge true}}})))

(reg-event-fx
 :create-user
 (fn [{:keys [db]} [_]]
   (let [{:keys [my-community temp-new-user]} db]
     {:db (dissoc db :temp-new-user)
      :firestore/add {:path [:users]
                      :data {"name" temp-new-user
                             "community" my-community}}})))

(reg-event-fx
 :delete-user
 (fn [{:keys [db]} [_ uid]]
   (let [{:keys []} db]
     {:db (dissoc db :popup)
      :firestore/delete {:path [:users uid]
                         :on-failure #(prn "Error:" %)}})))


(reg-event-fx
 :firestore-delete-item
 (fn [{:keys [db]} [_ uid prefix]]
   (let [{:keys [user temp-item edit-item my-community]} db]
     {:firestore/delete {:path [:communities my-community :items (:id edit-item)]
                         ;:on-success [:assoc :temp-item nil :edit-item nil]
                         :on-failure #(prn "Error:" %)}
      :db (dissoc db :edit-item :temp-item)})))

(reg-event-fx
 :firestore-delete-shop
 (fn [{:keys [db]} [_ uid]]
   (let [{:keys [user temp-item edit-item my-community]} db]
     {:firestore/delete {:path [:communities my-community :shops uid]                         
                         ;:on-success [:assoc :temp-item nil :edit-item nil]
                         :on-failure #(prn "Error:" %)}})))

(reg-event-fx
 :firestore-get
 (fn [{:keys [db]} [_ query]]
   {:firestore/get query}))

(reg-event-fx
 :upload
 (fn [{:keys [db]} [_ opts]]
   {:firebase/upload opts}))

(reg-event-fx
 :firestore-mark-transaction
 (fn [{:keys [db]} [_ {:keys [checked-items selected-uid transaction-path transaction-amount]}]]
   (let [;{:keys [checked-items selected-uid]} db
         ;temp-item-kw (keyword prefix (str "temp-item-new-" uid))
         ;temp-item (get db temp-item-kw)
         {:keys [my-community]} db]
     (println "transaction-path" (last transaction-path) transaction-path my-community)
     {:firestore/write-batch
      {:operations
       (conj
        (for [item-id checked-items]
          (do (println [:users selected-uid item-id])
              [:firestore/update {:path [:communities my-community :items item-id]
                                  :data {:receipt-transaction-id (last transaction-path)}}]))
        [:firestore/update {:path [:communities my-community :users selected-uid]
                            :data {:balance (firestore/increment-field (* -1 transaction-amount))}}])
       :on-success #(re-frame/dispatch [:assoc :checked-items #{}])
       :on-failure #(js/alert "Error setting receipt against items:" %)}
      :db (dissoc db :transaction-amount)})))

(reg-event-fx
 :firestore-add-transaction
 (fn [{:keys [db]} [_ uid receipt-url]]
   (let [{:keys [checked-items selected-uid transaction-amount]} db
         ;temp-item-kw (keyword prefix (str "temp-item-new-" uid))
         ;temp-item (get db temp-item-kw)
         ]
     {:firestore/add {:path [:users uid :transactions]
                      :data {:timestamp (firestore/server-timestamp)
                             :receipt-url receipt-url
                             :transaction-amount (* -1 transaction-amount)
                             ;:user (firestore/clj->DocumentReference ["users" uid])
                             ;:type prefix
                             ;:item temp-item
                             }
                      :on-success #(re-frame/dispatch 
                                    [:firestore-mark-transaction {:checked-items checked-items
                                                                  :selected-uid selected-uid
                                                                  :transaction-amount transaction-amount
                                                                  :transaction-path %}])
                      :on-failure #(prn "Error:" %)}
      ;:db (dissoc db :edit-item temp-item-kw :show-menu)
      })))


#_(reg-event-fx
 :firestore-listen-me
 (fn [{:keys [db]} [_]]
   (let [{:keys [user]} db
         {:keys [uid]} user]
     {:firestore/on-snapshot {:path-document [:users uid]
                              :on-next 1}})))

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
 (fn [{:keys [db]} [_ & v]]
   (let [{:keys [user]} db]
     (println "error")
     (cljs.pprint/pprint v)
     (when user
       (js/alert (.toString (first v)))))))


(re-frame/reg-event-fx
 :init-captcha
 (fn [_ _]
   {:firebase/init-recaptcha {:on-solve [:assoc :captcha-msg "Welcome Human"]
                              :container-id "Phone"}}))


(re-frame/reg-event-fx
 :phone-sign-in
 (fn [_ [_ phone]]
   (let [phone (str/replace-first phone #"^[0]+" "+44")]
     {:firebase/phone-number-sign-in {:phone-number phone
                                      :on-send [:assoc :sms-sent true]}})))


(re-frame/reg-event-fx
 :phone-confirm-code
 (fn [_ [_ code]]
   {:firebase/phone-number-confirm-code {:code code}}))

