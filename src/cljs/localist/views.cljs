(ns localist.views
  (:require
   [re-frame.core :as re-frame :refer [subscribe dispatch]]
   [localist.subs :as subs]
   [localist.style :refer [s]]
   [localist.re-frame-firebase.auth :as firebase-auth]
   [localist.re-frame-firebase.storage :as storage]
   ))

(defn <- [& v]
  (deref (subscribe (vec v))))

(defn ! [& v]
  (dispatch (vec v)))

(defn button [props text]
  [:button (merge {:style (s :pa3 :mb3 :white)}
                  props) 
   text])

(defn login-input [{:keys [id db-key type size]}]
  (let [val (<- :get db-key)]
    [:div {:style (merge (s :pa2)
                         {:display "block"})}
     [:input {:style (s :f3 :pa2)
              :type type
              :id id
              :name id
              :size (or size 23)              
              :default-value val
              :placeholder id
              :on-change #(! :assoc db-key (-> % .-target .-value))}]]))

(defn login-create-account []
  [:div {:style (s :tc :pt4 )}    
   [button {:on-click #(firebase-auth/facebook-sign-in {})}
    "Login with Facebook"]
   [:hr]
   (if (<- :get :show-login-account)
     [:div
      [:p {:style (s :ma4)} "Login with an account you have already created..."]
      [login-input {:id "Email" :db-key :temp-email :type "email" :size 24}]
      [login-input {:id "Password" :db-key :temp-password :type "password" :size 24}]
      [:div {:style (s :ma3)}
       [button {:on-click #(! :sign-in-by-email)}
        "Login"]
       [:a {:style (s :ml3 {:padding 10 :font-size 14 :text-decoration "underline" :cursor "pointer"}) :on-click #(! :send-password-reset-email)} "forgot password?"]]
      [:div {:style (s :mt3)}
       [:a {:style {:text-decoration "underline" :cursor "pointer"} :on-click #(! :assoc :show-login-account false)} "Create an account with your email address"]]]
     [:div
      [:p {:style (s :ma4)} "Don't have Facebook? Create an account with your email..."]

      ;[login-input {:id "Name" :db-key :temp-name :type "text"}]
      [login-input {:id "Email" :db-key :temp-email :type "email"}]
      [login-input {:id "Password" :db-key :temp-password :type "password"}]
      [login-input {:id "Confirm Password" :db-key :temp-password-confirm :type "password"}]
      [:div {:style (s :pt3)} 
       [button {:on-click #(! :create-by-email)}
        "Create account"]]
      [:div {:style (s :mt3)}
       [:a {:style {:text-decoration "underline" :cursor "pointer"} :on-click #(! :assoc :show-login-account true)} "Login with an existing account"]]])])

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
               :on-change #(! :assoc temp-item (-> % .-target .-value))}]
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
                             (! :toggle-checked (:id item)))
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
        [(if (#{"address" "dropoff"} field-name) 
           :textarea
           :input) {:style (s :f4 :mb2 :mt1 :pl2 
                              {:width 300})
                    :type "text"
                    :id field-name
                    :name field-name
                    :rows 3
                    :value (or (<- :get kw) 
                               (get data field-name))                
                    :on-change #(! :assoc kw (-> % .-target .-value))}])
      [:div {:style (s  :mb2 :pa1 :f5
                        {:display "inline-block"
                         :min-height 20
                         :min-width 20})}
       [:pre
        (or (get data field-name)
            (and (= field-name "balance") "0.00")
            )]])]])

(defn account-details [uid data & [admin?]]
  (let [details-complete? (seq (get data "phone"))
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
       (when (or (not editing?) admin?) [my-account-field data "balance" editing?])]
      [:div
       [my-account-field data "address" editing?]
       [my-account-field data "postcode" editing?]]
      [:div
       [my-account-field data "dropoff" editing? (not editing?)]
       (when-not editing?
         [:div {:style (s :tc)}
          [:a {:on-click #(! :assoc :edit-account uid)}
           "edit"]])]]
     (when editing?
       [:<> 
        [:div
         [:button {:style (s :bg-status0 :white :pa2 :mt3 :mb3 :fs0)
                   :on-click #(! :account-edit-save uid)} "save"]]
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
  [:div 
   [:form {:action "https://europe-west2-localist-e864f.cloudfunctions.net/pay" :method "POST" :id "paypal-form"}
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
                 :step 0.01}]
        [:input {:style (s :fg1 :f5 {:max-width 200})
                 :type "file" 
                 :ref (fn [el]
                        (reset! ref el))
                 :accept "image/*"}]
        [:button {:style (s :white :pa2)
                  :on-click (fn []
                              (let [f (aget (.-files @ref) 0)]
                                (when f
                                  (! :upload {:file f
                                              :on-complete (fn [url]
                                                             (println url)
                                                             (! :assoc :upload-progress nil)
                                                             (! :firestore-add-transaction uid url))
                                              :on-progress #(! :assoc :upload-progress %)}))))}
         "upload"]])]))


(defn transaction-list [uid items]
  (let [transactions (:docs (<- :firestore/on-snapshot {:path-collection [:users uid :transactions]
                                                        :order-by [[:timestamp :asc]]}))]
    (when (seq transactions)
      [:div
       [:div {:style (s :f3 :pb3 :pt3 :fwb :ui0 :o80 ;:btw1 :b-ui0 {:border-top "1px solid grey"}
                        )}
        "Receipts"]
       [:div {:style (s :pb1 :tl)}
        (doall (for [tr transactions]
                 ^{:key (:id tr)}   
                 [:div {:style (s :mt2 :mb1)}
                  [:a {:style (s :pa2)
                       :on-click #(! :assoc :popup
                                     
                                     [:img {:style {:height "100%"
                                                    :width "100%"
                                                    :object-fit "contain"}
                                            :src (get (:data tr) "receipt-url")}])}
                   (when-let [ts (get (:data tr) "timestamp")]
                     (.toLocaleString (.toDate ts)))]
                  (for [i (get items (:id tr))]
                    ^{:key (:id i)}
                    [:span {:style (s :pa2)}
                     (get (:data i) "item")])]))]])))

(defn account-block-me [user admin?]
  ;(println "user" user)
  (let [{:keys [id data]} user
        details-complete? (seq (get data "phone"))
        shopping-items (:docs (<- :firestore/on-snapshot {:path-collection [:users id :shopping]
                                                          :order-by [[:timestamp :asc]]}))
        shopping-items (group-by #(get-in % [:data "receipt-transaction-id"]) shopping-items)]
    [:<>
     [account-details id data admin?]
     (when details-complete?
       [:<> 
        [add-credit id]
        [item-list id :shopping "Shopping List" (get shopping-items nil) admin?]
               ;[item-list id :surplus "Surplus"]
        (when admin? [image-uploader id])
        [transaction-list id shopping-items]])]))

(defn account-block [user admin?]
  (let [{:keys [id data]} user
        shopping-items (:docs (<- :firestore/on-snapshot {:path-collection [:users id :shopping]
                                                          :order-by [[:timestamp :asc]]}))
        shopping-items (group-by #(get-in % [:data "receipt-transaction-id"]) shopping-items)]
    [:<>
     [account-details id data admin?]
     [item-list id :shopping "Shopping List" (get shopping-items nil) admin?]
               ;[item-list id :surplus "Surplus"]
     [image-uploader id]
     [transaction-list id shopping-items]]))

(defn logged-in []
  (let [my-uid (<- :get :user :uid)
        selected-uid (or (<- :get :selected-uid) my-uid)
        me (<- :firestore/on-snapshot {:path-document [:users my-uid]})
        me-data (:data me)
        admin? (get me-data "admin")
        users (if admin?
                (:docs (<- :firestore/on-snapshot {:path-collection [:users]
                                                 ;:where [["shopper" :== "test"]]
                                                 ;:order-by [[:timestamp :asc]]
                                                   }))
                [])]
    (cljs.pprint/pprint me-data )
    [:div
     #_(when-let [name (:display-name (<- :get :user))]
         [:p {:style (s :pb3 :pt3)} (str "Welcome " name)])
     [:div
      [:div [:div {:style (s :f3 :pb3 :pt3 :fwb :ui0 :o80 ;:btw1 :b-ui0 {:border-top "1px solid grey"}
                             )}
             "Ashburton"]
       "Some blurb about ashburton, not done yet. Some blurb about ashburton, not done yet.Some blurb about ashburton, not done yet.Some blurb about ashburton, not done yet.Some blurb about ashburton, not done yet.Some blurb about ashburton, not done yet.Some blurb about ashburton, not done yet.Some blurb about ashburton, not done yet."
       [:div {:style (s :f3 :pb3 :pt3 :fwb :ui0 :o80 ;:btw1 :b-ui0 {:border-top "1px solid grey"}
                        )}
        "Shops"]
       "Each shop will have a section here. Each shop will have a section here. Each shop will have a section here. Each shop will have a section here."
       ]
      [:div {:style (s :pl2 :tl :f3 :mb2 :mt2 :pt2 :pb2 :fwb :white :bg-brand0 :o80)
             :on-click #(! :assoc :selected-uid my-uid)}
       "My Account"]
      (when (and (seq me)
                 (= selected-uid my-uid))
        [account-block-me me admin?])
      #_(when (= selected-uid my-uid)
          [:<>
           [account-details my-uid me]
           [add-credit my-uid]
           [item-list my-uid :shopping "Shopping List"]
           [transaction-list my-uid shopping-items]])
               ;[item-list id :surplus "Surplus"]
      ]
     (doall (for [{:keys [id] :as user} users
                  :when (not= id my-uid)]
              [:div
               [:div {:style (s :pl2 :tl :f3 :mb2 :mt2 :pt2 :pb2 :fwb :white :bg-brand0 :o80)
                      :on-click #(! :assoc :selected-uid id)}
                (get (:data user) "name")]
               (when (= selected-uid id)
                 [account-block user admin?])]))
     [:div {:style (s :pa3 :mt5 {:display "block"})}
      [button {:on-click #(! :sign-out)} "logout"]]]))

(defn main-panel []  
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
       "Ashburton"]]
     [:p {:style (s :f5 :pa2 :tr :ui1)} "Corona Community" [:br] "Response"]]
    [:p {:style (s :f6  :tc :pt1 :pb2 :bg-brand0 :ui1)} "supporting volunteer home delivery groups and local communities"]
    (if (<- :get :user)
      [logged-in]
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
   
   [:div {:style (s :fg1)}]])
