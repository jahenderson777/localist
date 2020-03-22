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
              :size (or size 35)              
              :default-value val
              :placeholder id
              :on-change #(! :assoc db-key (-> % .-target .-value))}]]))

(defn login-create-account []
  [:div {:style (s :tc)}    
   [button {:on-click #(firebase-auth/facebook-sign-in {})}
    "Login with Facebook3"]
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
      [button {:on-click #(! :create-by-email)}
       "Create account"]
      [:div {:style (s :mt3)}
       ;[:div {:style (s :mb3)} "or"]
       [:a {:style {:text-decoration "underline" :cursor "pointer"} :on-click #(! :assoc :show-login-account true)} "Login with an existing account"]]])])



(defn logged-in []
  [:div
   [:p (str "Welcome " (:display-name (<- :get :user)))]
   [:div
    (doall (for [item (:docs (<- :firestore/on-snapshot {:path-collection [:users (<- :get :user :uid) :my-list]
                                                         :order-by [[:timestamp :asc]]}))]
             ^{:key (:id item)}
             [:div (get-in item [:data "item"])]))]
   
   [button {:on-click #(! :sign-out)} "logout"]
   [:div {:style (merge (s :w100)
                        {:position "fixed"
                         :top 0})}
    [:div {:style (merge (s :fdr :w100)
                         {:display "flex"})}
     [:input {:style (s :f3 :pa2 :w100)
              :type "email"
              :id "new-item"
              :name "new-item"
              :value (<- :get :temp-item)
              :placeholder "enter a new item"
              :on-change #(! :assoc :temp-item (-> % .-target .-value))}]
     
     [:button {:style (s :bg-brand0)
               :on-click #(! :firestore-add-item)} "add"]
     [:a.border-menu {:style (s :f2 :ml2)
                      :on-click #(! :assoc :show-menu true)}]]
    (when (<- :get :show-menu)
      [:div {:style (merge (s :bg-status0)
                           {:height "50vh"})}
       [:a {:on-click #(! :assoc :show-menu nil)}
        "close"]])
    ]])

(defn main-panel []  
  [:div {:style (merge (s  :bg-ui1 :pb7)
                       {:max-width 800
                        :margin "auto"})}
   [:p {:style (s :f1 :pb4)} "LocaList  " (<- :get :profile)]
   [:p {:style (s :f2 :pb4)} "Corona Community Response"]
   [:p {:style (s :f3 :pb4)} "Supporting volunteer home delivery groups and local communities"]
   (if (<- :get :user)
       [logged-in]
       [login-create-account])])
