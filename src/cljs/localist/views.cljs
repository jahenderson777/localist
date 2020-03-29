(ns localist.views
  (:require
   [re-frame.core :as re-frame :refer [subscribe dispatch]]
   [localist.subs :as subs]
   [localist.style :refer [s]]
   [localist.re-frame-firebase.auth :as firebase-auth]
   [localist.re-frame-firebase.storage :as storage]
   [localist.re-frame-firebase.firestore :as firestore]
   ))

(defn <- [& v]
  (deref (subscribe (vec v))))

(defn ! [& v]
  (dispatch (vec v)))

(defn button [props text]
  [:button (merge {:style (s :pa3 :mb3 :white)}
                  props) 
   text])

(defn login-input [{:keys [id db-key type size on-return]}]
  (let [val (<- :get db-key)
        ref (atom nil)]
    [:div {:style (merge (s :pa2)
                         {:display "block"})}
     [:input {:style (s :f3 :pa2)
              :type type
              :id id
              :name id
              :size (or size 22)
              :value val
              :placeholder id
              :ref #(reset! ref %)
              :on-key-press (fn [e]
                              (when (= 13 (.-charCode e))
                                (.blur @ref)
                                (when on-return
                                  (on-return))))
              :on-change #(re-frame/dispatch-sync [:assoc db-key (-> % .-target .-value)])}]]))

(defn login-create-account []
  [:<>
   #_[:div {:style (s :pl2 :tl :f3 :mb1 :mt2 :pt2 :pb2 :fwb :white :bg-brand0 :o80)}
    "Please login"]
   [:div {:style (s :tc :pt4 )}    
    #_[button {:on-click #(firebase-auth/facebook-sign-in {})}
       "Login with Facebook"]
    #_[:hr]
    
    (if (<- :get :sms-sent)
      [:div
       [:div "Please enter the code we just sent you..."]
       [login-input {:id "SMS Code" :db-key :temp-sms-code :type "phone" 
                     :on-return #(! :phone-confirm-code (<- :get :temp-sms-code))}]
       [button {:on-click #(! :phone-confirm-code (<- :get :temp-sms-code))}
        "Confirm code"]
       [:div {:style (s :pa3)}
        [:a {:on-click #(! :assoc :temp-sms-code nil :sms-sent nil)}
         "abort"]]]
      [:div
       [:p {:style (s :mb3)} "Login with your mobile phone..."]
       [login-input {:id "Phone" :db-key :temp-phone :type "phone" 
                     :on-return #(! :phone-sign-in (<- :get :temp-phone))}]
       [button {:id "phone-sign-in-button"
                :on-click #(! :phone-sign-in (<- :get :temp-phone))}
        "SMS me a login code"]
       (when-let [msg (<- :get :phone-sign-in-msg)]
         [:div msg])])
    [:hr]
    (if (<- :get :show-login-account)
      [:div
       [:p {:style (s :ma4)} "Login with an account you have already created..."]
       [login-input {:id "Email" :db-key :temp-email :type "email" }]
       [login-input {:id "Password" :db-key :temp-password :type "password" }]
       [:div {:style (s :ma3)}
        [button {:on-click #(! :sign-in-by-email)}
         "Login"]
        [:a {:style (s :ml3 {:padding 10 :font-size 14 :text-decoration "underline" :cursor "pointer"}) :on-click #(! :send-password-reset-email)} "forgot password?"]]
       [:div {:style (s :mt3)}
        [:a {:style {:text-decoration "underline" :cursor "pointer"} :on-click #(! :assoc :show-login-account false)} "Create an account with your email address"]]]
      [:div
       [:p {:style (s :ma4)} "Or create an account with your email address..."]

      ;[login-input {:id "Name" :db-key :temp-name :type "text"}]
       [login-input {:id "Email" :db-key :temp-email :type "email"}]
       [login-input {:id "Password" :db-key :temp-password :type "password"}]
       [login-input {:id "Confirm Password" :db-key :temp-password-confirm :type "password"}]
       [:div {:style (s :pt3)} 
        [button {:on-click #(! :create-by-email)}
         "Create account"]]
       [:div {:style (s :mt3)}
        [:a {:style {:text-decoration "underline" :cursor "pointer"} :on-click #(! :assoc :show-login-account true)} "Login with an existing account"]]])]])

(def input-ref (atom nil))

(defn add-form [uid prefix is-edit?]
  (let [submit-event (if is-edit? 
                       :firestore-update-item
                       :firestore-add-item)
        submit-text (if is-edit?
                      "save"
                      "add")
        temp-item (if is-edit?
                    (keyword prefix (str "temp-item-" uid))
                    (keyword prefix (str "temp-item-new-" uid)))]
    [:div {:style (merge (s :flex :bg-status1 :f5 :fdc))}
     [:div {:style (s :flex :fdr
                      {:max-width "100%"
                       :min-width "100%"})}
      [:input {:style (s :f4 :ma2 :fg1 :w4 :pl2)
               :type "text"
               :id "new-item"
               :name "new-item"
               :ref (fn [el]
                      (reset! input-ref el))
               :auto-focus true
               :value (<- :get temp-item)
               :placeholder "enter a new item"
               :on-key-press (fn [e]
                               (when (= 13 (.-charCode e))
                                 (.blur @input-ref)
                                 (! submit-event uid prefix)))
               :on-change #(re-frame/dispatch-sync [:assoc temp-item (-> % .-target .-value)])}]
      [:div
       [:button {:style (s :bg-status0 :white :pa2 :mt2 :mr2 :mb2 :fs0)
                 :on-click #(! submit-event uid prefix)} submit-text]]]
     [:div {:style (s :flex :fdr :jcsa)}
      (when is-edit?
        [:<>
         [:a {:style (s :ma3)
              :on-click #(! :firestore-delete-item uid prefix)}
          "delete"]
         [:a {:style (s :ma3)
              :on-click #(! :assoc :show-menu nil :edit-item nil)}
          "cancel"]])]]))

(defn item-block [uid prefix item checked admin?]
  (let [data (:data item)
        temp-item (keyword prefix (str "temp-item-" uid))
        txt (get data "item")]
    [:div {:style (merge (s :pa2 :bg-status1 :tl :fdr :o80 :jcsb)
                         {:display "flex"
                          :min-height 20
                          :cursor "pointer"})}
     [:div {:style (s :fg1)
            :on-click #(! :assoc :edit-item item temp-item txt)}
      txt]
     (when admin? 
       [:input {:style (s :w2 :h2 :bg-status2)
                :checked (boolean checked)
                :on-change (fn [e]
                             (re-frame/dispatch-sync [:toggle-checked (:id item)]))
                :type "checkbox"}])]))

(defn my-account-field [data field-name editing? & [last?]]
  [:div {:style (s (when-not last? :mr2))}
   [:div {:style (s :tl {:display "inline-block"})}
    [:div {:style (s :pl1 :pr1 :f6 :brand0)}
     (if (= field-name "dropoff")
       "Drop-off place/notes"
       (clojure.string/capitalize field-name))]
    (if editing?
      (let [kw (keyword (str "temp-" field-name))]
        [(if (#{"address" "dropoff" "info"} field-name) 
           :textarea
           :input) {:style (s :f4 :mb2 :mt1 :pl2 
                              {:width 300})
                    :type "text"
                    :id field-name
                    :name field-name
                    :rows 3
                    :value (or (<- :get kw) 
                               (get data field-name))                
                    :on-change #(re-frame/dispatch-sync [:assoc kw (-> % .-target .-value)])}])
      [:div {:style (s  :mb2 :pa1 :f5
                        {:display "inline-block"
                         :min-height 20
                         :min-width 20})}
       [:pre
        (or (get data field-name)
            (and (= field-name "balance") "0.00")
            )]])]])

(defn account-details [uid data admin? community-specific]
  (let [details-complete? (seq (get data "address"))
        my-community (get data "community")
        shop-doc (:data (<- :firestore/on-snapshot {:path-document [:communities my-community :shops uid]}))
         
        has-store? (<- :get :has-store?)
        editing? (or (= uid (<- :get :edit-account))
                     (not details-complete?))
        ;admin? (get data "admin")
        ]
    [:div
     (when-not details-complete?
       [:div {:style (s :status0 :fwb :f3 :pa3 :ma3)}
        "please fill in your details"])
     [:div {:style (s (if editing? :tc :tl) 
                      (when-not editing? :flex) 
                      :jcsb)}
      [:div 
       [my-account-field data "name" editing?]
       [my-account-field data "phone" editing?]
       (when (or (not editing?) admin?) 
         [my-account-field community-specific "balance" editing?])]
      [:div
       [my-account-field data "address" editing?]
       [my-account-field data "postcode" editing?]]
      [:div
       [my-account-field data "dropoff" editing? (not editing?)]
       (when-not editing?
         [:div {:style (s :tc)}
          [:a {:on-click (fn [] (! :assoc :edit-account uid
                                   :temp-name nil :temp-phone nil :temp-postcode nil 
                                   :temp-dropoff nil :temp-balance nil
                                   :temp-shop-name nil :temp-opening-times nil :temp-info nil
                                   :has-store? (boolean shop-doc)))}
           "edit"]])]
      (when editing?
        [:div {:style (s :mb3 :mt3)}
         [:label {:style (s :mr2 :pb2 {:position "relative"
                                       :top -5})
                  :for "has-store"} "Do you want to a shop listing?"]
         [:input {:style (s :w2 :h2 :bg-status2)
                  :checked (boolean has-store?)
                  :name "has-store"
                  :id "has-store"
                  :on-change (fn [e]
                               (re-frame/dispatch-sync [:assoc :has-store? (not has-store?)]))
                  :type "checkbox"}]])]
     (when (and has-store? editing?)
       [:div
        [my-account-field shop-doc "shop-name" editing?]
        [my-account-field shop-doc "opening-times" editing?]
        [my-account-field shop-doc "info" editing?]])
     (when editing?
       [:<> 
        [:div
         [:button {:style (s :bg-status0 :white :pa2 :mt3 :mb3 :fs0)
                   :on-click (fn [] 
                               (when (and (not has-store?) shop-doc))(! :firestore-delete-shop uid)
                               (! :account-edit-save uid))} "save"]]
        (when details-complete?
          [:div {:style (s :ma3)} 
           [:a {:on-click #(! :assoc :edit-account false)}
            "cancel"]])])]))


(defn item-list [uid prefix text docs admin?]
  (let [me (<- :get :user :uid)
        checked-items (<- :get :checked-items)]
   [:div
    [:div {:style (s :f3 :pb3 :pt3 :fwb :ui0 :o80 ;:btw1 :b-ui0 {:border-top "1px solid grey"}
                     )}
     text]
    (when (and (seq docs)
               (= uid me))
      [:p {:style (s :f6 :brand0)} "[click an item to edit/delete]"])
    [:div {:style (s :pb1)}
     (doall (for [item docs]
              ^{:key (:id item)}
              [:div {:style (s :mt2 :mb1)}
               (if (= (:id item)
                      (:id (<- :get :edit-item)))
                 [add-form uid prefix true]
                 [item-block uid prefix item (checked-items (:id item)) admin?])]))]
    [add-form uid prefix false]]))


(defn add-credit [uid]
  [:div {:style (s :mb3)}
   "£ "
   [:input {:style (s :w5 :f3)
            :type "number"
            :default-value 0
            :step 1
            :min 0
            :value (or (<- :get :credit-amount)
                       0)
            :on-change #(re-frame/dispatch-sync [:assoc :credit-amount (-> % .-target .-value)])}]
   [:button {:style (s :pa2 :ml2 :white)
             :on-click #(! :assoc :popup
                           [:div {:style (s :tl :bg-ui1 :pa4 {:max-width 400
                                                              :margin "auto"})}
                            [:div {:style (s :mb3)}
                             "So that we can avoid all transaction fees we use PayPal transfers to manage account balances."]
                            [:div {:style (s :mb3)}
                             "Please be sure to mark your transfer with your name and phone number."]
                            [:div "We need to manually match your transfer to your account so it will take some time for your balance to update."]
                            [:div {:style (s :ma4 :tc)}
                             [:a {:style (s :pa2 :white
                                            {:background-color "cornflowerblue"
                                             :text-decoration "none"})

                                  :href (str "https://paypal.me/jahenderson777/" (<- :get :credit-amount) "GBP")}
                              "Continue to PayPal"]]])}
    "Add Credit"]
   
   #_[:form {:action "https://europe-west2-localist-e864f.cloudfunctions.net/pay" :method "POST" :id "paypal-form"}
      [:input {:type "hidden" :name "uid" :value uid}]
    ;[:input {:type "text" :name "price" :value "0.01" :pattern "-?[0-9]*(\\.[0-9]+)?"}]
      [:select {:style (s :pa2)
                :id "price" :name "price"}
       [:option {:value "0.01"} "£0.01"]
       [:option {:value "5.00"} "£5.00"]
       [:option {:value "10.00"} "£10.00"]
       [:option {:value "20.00"} "£20.00"]
       [:option {:value "40.00"} "£40.00"]
       [:option {:value "60.00"} "£60.00"]]
      [:button {:style (s :pa2 :ml2 :white)
                :form "paypal-form" :type "submit" :value "Add Credit"}
       "Add Credit"]]])

(defn image-uploader [uid]
  (let [ref (atom nil)
        upload-progress (<- :get :upload-progress)]
    [:div {:style (s :pa2)}
     (if upload-progress
       [:div (str "uploading " (int upload-progress) "%")]
       [:div
        [:input {:style (s :w6)
                 :type "number"
                 :default-value 0
                 :step 0.01
                 :value (or (<- :get :transaction-amount)
                            "0.00")
                 :on-change #(re-frame/dispatch-sync [:assoc :transaction-amount (-> % .-target .-value)])}]
        [:input {:style (s :fg1 :f5 {:max-width 200})
                 :type "file" 
                 :ref (fn [el]
                        (reset! ref el))
                 :accept "image/*"}]
        [:button {:style (s :white :pa2 :ma2)
                  :on-click (fn []
                              (let [f (aget (.-files @ref) 0)]
                                (when f
                                  (! :upload {:file f
                                              :on-complete (fn [url]
                                                             ;(println url)
                                                             (! :assoc :upload-progress nil)
                                                             (! :firestore-add-transaction uid url))
                                              :on-progress #(! :assoc :upload-progress %)}))))}
         "upload receipt"]])]))


(defn transaction-list [uid items]
  (let [transactions (:docs (<- :firestore/on-snapshot {:path-collection [:users uid :transactions]
                                                        :order-by [[:timestamp :asc]]}))]
    
    [:div
     [:div {:style (s :f3 :pb3 :pt3 :fwb :ui0 :o80 ;:btw1 :b-ui0 {:border-top "1px solid grey"}
                      )}
      "Receipts"]
     (if (seq transactions) 
       [:div {:style (s :pb1 :tl)}
        (doall (for [tr transactions]
                 ^{:key (:id tr)}   
                 [:div {:style (s :mt2 :mb1 :ml2)}
                  [:a {:style (s )
                       :on-click #(! :assoc :popup
                                     
                                     [:img {:style {:height "100%"
                                                    :width "100%"
                                                    :object-fit "contain"}
                                            :src (get (:data tr) "receipt-url")}])}
                   (when-let [ts (get (:data tr) "timestamp")]
                     (.toLocaleString (.toDate ts)))]
                  [:span {:style (s :fwb)}
                   (str " £" (get (:data tr) "transaction-amount") " ")]
                  (for [i (get items (:id tr))]
                    ^{:key (:id i)}
                    [:span ;{:style (s :ma2)}
                     (str (get (:data i) "item") "; ")])]))]
       [:div {:style (s :tl :ui0 :pa2 :o80)}
        "Receipts will appear here when we've done some shopping for you. We will deduct the value from your account balance and include photos of any receipts."])]))

(defn account-block-me [user admin? shopping-items]
  ;(println "user" user)
  (let [{:keys [id data]} user
        details-complete? (seq (get data "address"))
        my-community (get data "community")
        community-specific (:data (<- :firestore/on-snapshot {:path-document [:communities my-community :users id]}))
        ;shopping-items (group-by #(get-in % [:data "receipt-transaction-id"]) items)
        ]
    ;(cljs.pprint/pprint shopping-items)
    [:<>
     [account-details id data admin? community-specific]
     (when details-complete?
       [:<> 
        [add-credit id data]
        [item-list id :shopping "Shopping List" (get shopping-items nil) admin?]
               ;[item-list id :surplus "Surplus"]
        (when admin? [image-uploader id])
        [transaction-list id shopping-items]])]))

(defn account-block [user admin? shopping-items volunteers]
  (let [{:keys [id data]} user
        selected-uid (<- :get :selected-uid)
        my-community (get data "community")

        community-specific (:data (<- :firestore/on-snapshot {:path-document [:communities my-community :users id]}))
        locked? (get community-specific "locked-by")
;shopping-items (group-by #(get-in % [:data "receipt-transaction-id"]) items)
        ]
    ;(cljs.pprint/pprint shopping-items)
    [:<>
     [:div {:style (s :pl2 :tl :mb2 :mt2 :pt2 :pb2 :white :bg-brand0 :o80 :flex :jcsb)
            :on-click (fn [] 
                        (! :assoc :selected-uid id))}
      [:span {:style (s :fwb :f3)} 
       (str (when locked?
              "🔒")
        (get (:data user) "name"))]
      [:div {:style (s :tr
                       {:display "inline-block"})}
       (map-indexed (fn [idx item]
                      ^{:key idx}
                      [:span {:style (s :f5)}
                       (get-in item [:data "item"])
                       "; "])
                    (get shopping-items nil))]]
     (when (= selected-uid id)
       [:<>
        [account-details id data admin? community-specific]
        [:div {:style (s :flex :fdr :jcsa)}
         [:div ;{:style (s :ma2)}
          (if (volunteers id)
            [:a {:on-click #(! :volunteer-demote id)}
             "demote from picker"]
            [:a {:on-click #(! :volunteer-promote id)}
             "promote to picker"])]
         
         [:div ;{:style (s :ma2)}
          (if (get community-specific "locked-by")
            [:a {:on-click #(! :unlock-order id)}
             "unlock order"]
            [:a {:on-click #(! :lock-order id)}
             "lock order for picking"])]]
        
        [item-list id :shopping "Shopping List" (get shopping-items nil) admin?]
               ;[item-list id :surplus "Surplus"]
        [image-uploader id]
        [transaction-list id shopping-items]])]))

#_(:docs (<- :firestore/on-snapshot {:path-collection [:users id :shopping]
                                   :order-by [[:timestamp :asc]]}))
(defn logged-in []
  (let [my-uid (<- :get :user :uid)
        selected-uid (or (<- :get :selected-uid) my-uid)
        me (<- :firestore/on-snapshot {:path-document [:users my-uid]})
        me-data (:data me)
        
        my-community (get me-data "community")
        community (:data (<- :firestore/on-snapshot {:path-document [:communities my-community]}))
        volunteers (into #{} (get community "volunteers"))
        volunteer? (volunteers my-uid)
        admin? (or volunteer? (= my-uid (get community "admin")))
        ;_ (println "volunteers " (get community "volunteers"))
        shops (:docs (<- :firestore/on-snapshot {:path-collection [:communities my-community :shops]}))
        items (:docs (<- :firestore/on-snapshot
                         (merge {:path-collection [:communities my-community :items]}
                                (if admin?
                                  {:order-by [[:timestamp :asc]]}
                                  {:where [["user" :== my-uid]]
                                   :order-by [[:timestamp :asc]]}))))
        items-grouped (->> items
                           (group-by #(get-in % [:data "user"]))
                           (map (fn [[k v]]
                                  [k (group-by #(get-in % [:data "receipt-transaction-id"]) v)]))
                           (into {}))
        users (if admin?
                (:docs (<- :firestore/on-snapshot {:path-collection [:users]
                                                   :where [["community" :== my-community]]}))
                [])]
    [:div
     [:<>
      [:div [:div {:style (s :f3 :pb3 :pt3 :fwb :ui0 :o80)}
             (get community "name")]
       [:pre {:style (s :tl :pa3)}
        (get community "info")]
       (when-not (pos? (get me-data "balance"))
         [:div {:style (s :tl :pa3)}
          "Add some credit to your account first."])]
      (when (seq shops)
        [:<>
         [:div {:style (s :pl2 :tl :f3 :mb1 :mt2 :pt2 :pb2 :fwb :white :bg-brand0 :o80)}
          "Shop info"]
         (doall (for [{:keys [id data]} shops]
                  ^{:key id}
                  [:div {:style (s :tc :mb4)}
                   [:div {:style (s :tl :pa2
                                    {:max-width 500
                                     :margin "auto"
                                     :border-bottom "1px solid grey"})}
                    [:div {:style (s :tc :f3 :pb2 :pt3 :fwb :ui0 :o80)}
                     (get data "shop-name")]
                    [:div {:style (s :f6 :brand0 :mb2)} "Opening times"]
                    [:div  (get data "opening-times")]
                    [:div {:style (s :f6 :brand0 :mb2 :mt3)}  "Info"]
                    [:div  (get data "info")]]]))])]
     [:div
      [:div {:style (s :pl2 :tl :f3 :mb2 :mt2 :pt2 :pb2 :fwb :white :bg-brand0 :o80)
             :on-click #(! :assoc :selected-uid my-uid)}
       "My Account"]
      (when (and (seq me)
                 (= selected-uid my-uid))
        [account-block-me me admin? (get items-grouped my-uid)])]
     (doall (for [{:keys [id] :as user} users
                  :when (not= id my-uid)]
              ^{:key id}
              [:div
               [account-block user admin? (get items-grouped id) volunteers]]))
     [:hr]
     [:div {:style (s :pa3 :mt5 {:display "block"})}
      
      [button {:on-click #(! :sign-out)} "logout"]
      [:div {:style (s :ma4)}
       [:a {:on-click #(! :firestore-set-community nil :selected-uid nil)}
        "leave community"]]]]))


(defn select-community []
  (let [communities (:docs (<- :firestore/on-snapshot {:path-collection [:communities]}))]
    ;(cljs.pprint/pprint communities)
    [:div [:div {:style (s :f3 :pa3)}
           "Please select a community"
           (doall (for [community communities
                        :let [{:keys [id data]} community]]
                    ^{:key id}
                    [:div {:style (s :ma3)}
                     [:a {:on-click #(! :firestore-set-community id)}
                      (get data "name")]]))]
     [:div
      {:style (s :tc {:margin "auto"
                      :max-width 500})}
      "To create a new community please email Jonathan on jahenderson777@gmail.com or phone 07912097088"]]))

(defn main-panel []  
  (let [my-uid (<- :get :user :uid)
        me (when my-uid (<- :firestore/on-snapshot {:path-document [:users my-uid]}))
        me-data (when me (:data me))
        my-community (when me-data (get me-data "community"))        
        _ (println "my community=" my-community)]
    [:<>
     [:div {:style (s :fg1)}]
     [:div {:style (merge (s  :bg-ui1 :pb7 :fg1)
                          {:max-width 700
                           :min-height "100vh"
                           :display "block"
                           :margin "0 0"})}
      [:div {:style (s :flex :fdr :tl :jcsb :bg-ui0)}
       [:div 
        [:p {:style (s :f4 :pl2 :pt2 :fwb :white)} "LocaList"]
        [:p {:style (s :pl2 :pb2 :status1)}  
         my-community]]
       [:p {:style (s :f5 :pa2 :tr :ui1)} "Corona Community" [:br] "Response"]]
      [:p {:style (s :f6  :tc :pt1 :pb2 :bg-brand0 :ui1)} "supporting volunteer home delivery groups and local communities"]
      (if (<- :get :user)
        (if my-community
          [logged-in]
          [select-community])
        [login-create-account])
      (when-let [popup (<- :get :popup)]
        [:div {:style (s :tc :w100 :h100 :bg-ui1
                         {:background-color "rgba(0, 0, 0, 0.7)"
                          :position "fixed"
                          :top 0
                          :left 0})}
         [:div {:style (s :ma3)}
          [:a {:style (s :white)
               :on-click #(! :assoc :popup nil)}
           "close"]]
         popup])]
     
     [:div {:style (s :fg1)}]]))
