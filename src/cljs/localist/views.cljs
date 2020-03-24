(ns localist.views
  (:require
   [re-frame.core :as re-frame :refer [subscribe dispatch]]
   [localist.subs :as subs]
   [localist.style :refer [s]]
   [localist.re-frame-firebase.auth :as firebase-auth]
   ))

(defn <- [& v]
  (deref (subscribe (vec v))))

(defn ! [& v]
  (dispatch (vec v)))

(defn button [props text]
  [:button (merge {:style (s :pa3 :mb3)}
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
      
      [:div [button {:on-click #(! :sign-in-by-email)}
             "Login"]
       [:a {:style {:padding 10 :font-size 14 :text-decoration "underline" :cursor "pointer"} :on-click #(! :send-password-reset-email)} "forgot password?"]]
      [:div {:style (s :mt3)}
       ;[:div {:style (s :mb3)} "or"]
       [:a {:style {:text-decoration "underline" :cursor "pointer"} :on-click #(! :assoc :show-login-account false)} "Create an account with your email address"]]]

     [:div
      [:p {:style (s :ma4)} "Don't have Facebook? Create an account with your email..."]

      [login-input {:id "Name" :db-key :temp-name :type "text"}]
      [login-input {:id "Email" :db-key :temp-email :type "email"}]
      [login-input {:id "Password" :db-key :temp-password :type "password"}]
      [login-input {:id "Confirm Password" :db-key :temp-password-confirm :type "password"}]
      [:div {:style (s :pt3)} 
       [button {:on-click #(! :create-by-email)}
        "Create account"]]
      [:div {:style (s :mt3)}
       ;[:div {:style (s :mb3)} "or"]
       [:a {:style {:text-decoration "underline" :cursor "pointer"} :on-click #(! :assoc :show-login-account true)} "Login with an existing account"]]])])


(def input-ref (atom nil))

(defn add-form [is-edit?]
  (let [submit-event (if is-edit? 
                       :firestore-update-item
                       :firestore-add-item)
        submit-text (if is-edit?
                      "save"
                      "add")
        temp-item (if is-edit?
                    :temp-item
                    :temp-item-new)]
    [:div {:style (merge (s :flex :bg-status1 :f5 :fdc)
                         {;:max-width 700
                          ;:position "fixed"
                          ;:width "95%"
                          ;:bottom 0
                          })}
     
     [:div {:style (s :flex :fdr
                      {:max-width "100%"
                       :min-width "100%"})}
      [:input {:style (s :f5 :ma2 :fg1 :w4 :pl2)
               :type "text"
               :id "new-item"
               :name "new-item"
               :ref (fn [el]
                      (reset! input-ref el))
               :auto-focus true
               :value (<- :get temp-item)
               :placeholder "enter a new item"
               :on-key-press (fn [e]
                               (js/console.log e)
                               (when (= 13 (.-charCode e))
                                 (.blur @input-ref)
                                 (! submit-event)))
               :on-change #(! :assoc temp-item (-> % .-target .-value))}]
      [:div
       [:button {:style (s :bg-status0 :white :pa2 :mt2 :mr2 :mb2 :fs0)
                 :on-click #(! submit-event)} submit-text]]]
     [:div {:style (s :flex :fdr :jcsa)}
      (when is-edit?
        [:<>
         [:a {:style (s :ma3)
              :on-click #(! :firestore-delete-item)}
          "delete"]
         [:a {:style (s :ma3)
              :on-click #(! :assoc :show-menu nil :edit-item nil)}
          "cancel"]])]]))

(defn item-block [item]
  (let [data (:data item)
        id (:id item)
        txt (get data "item")]
    [:div {:style (merge (s :pa2 :bg-status1 :tl :fdr :o80)
                         {:display "flex"
                          :min-height 20
                          :cursor "pointer"})
           :on-click #(! :assoc :edit-item item :temp-item txt)}
     [:div {:style (s )}
      txt]]))

(defn my-account-field [data field-name editing? & [last?]]
  [:div {:style (s (when-not last? :mr2))}
   [:div {:style (s :tl {:display "inline-block"})}
    [:div {:style (s :pl1 :f7 :fwb :brand0)}
     (clojure.string/capitalize field-name)]
    (if editing?
      (let [kw (keyword (str "temp-" field-name))]
        [(if (#{"address" "dropoff"} field-name) 
           :textarea
           :input) {:style (s :f5 :mb2 :mt1 :pl2 
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
        (get data field-name)]])]])

(defn my-account []
  (let [data (:data (<- :firestore/on-snapshot {:path-document [:users (<- :get :user :uid)]}))
        name (get data "name")
        phone (get data "phone")
        address (get data "address")
        postcode (get data "postcode")
        editing? (<- :get :edit-account)]
    [:div 
     [:div {:style (s :f3 :pb3 :pt3 :fwb :ui0 :o80)}
      "My account"]

     [:div {:style (s (if editing? :tc :tl) 
                      (when-not editing? :flex) 
                      :jcsb)}
      [:div 
       [my-account-field data "name" editing?]
       [my-account-field data "phone" editing?]]
      [:div
       [my-account-field data "address" editing?]
       [my-account-field data "postcode" editing?]]
      [:div
       [my-account-field data "dropoff" editing? (not editing?)]
       (when-not editing?
         [:div {:style (s :tc)}
          [:a {:on-click #(! :assoc :edit-account true)}
           "edit"]])]
      ]
     
     (when editing?
       [:<> 
        [:div
         [:button {:style (s :bg-status0 :white :pa2 :mt3 :mb3 :fs0)
                   :on-click #(! :account-edit-save)} "save"]]
        [:div {:style (s :ma3)} 
         [:a {:on-click #(! :assoc :edit-account false)}
          "cancel"]]]
       )
     ]))

(defn logged-in []
  [:div
   (when-let [name (:display-name (<- :get :user))]
     [:p {:style (s :pb3 :pt3)} (str "Welcome " name)])
   [my-account]
   [:div {:style (s :f3 :pb3 :pt3 :fwb :ui0 :o80)}
    "My shopping list"]
   [:p {:style (s :f6 :brand0)} "[click an item to edit/delete]"]
   [:div {:style (s :pb1)}
    (doall (for [item (:docs (<- :firestore/on-snapshot {:path-collection [:users (<- :get :user :uid) :my-list]
                                                         :order-by [[:timestamp :asc]]}))]
             ^{:key (:id item)}
             [:div {:style (s :mt2 :mb1)}
              (if (= (:id item)
                     (:id (<- :get :edit-item)))                
                [add-form true]
                [item-block item])]))]
   [add-form false]
   
   
   [:div {:style (s :pa3 :mt5 {:display "block"})}
    [button {:on-click #(! :sign-out)} "logout"]]])

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
      [login-create-account])]
   [:div {:style (s :fg1)}]])
