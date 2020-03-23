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
  [:div {:style (s :tc)}    
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
      [button {:on-click #(! :create-by-email)}
       "Create account"]
      [:div {:style (s :mt3)}
       ;[:div {:style (s :mb3)} "or"]
       [:a {:style {:text-decoration "underline" :cursor "pointer"} :on-click #(! :assoc :show-login-account true)} "Login with an existing account"]]])])


(def input-ref (atom nil))

(defn add-form []
  (let [is-edit? (<- :get :edit-item)
        submit-event (if is-edit? 
                       :firestore-update-item
                       :firestore-add-item)
        submit-text (if is-edit?
                      "save"
                      "add")]
    [:div {:style (merge (s :bg-status0 :f4 :w100)
                         {:max-width 600
                          :position "fixed"
                          :bottom 0})}
     
     [:div {:style (merge (s :fdr)
                          {:max-width "100%"
                           :min-width "100%"})}
      [:input {:style (merge (s :f4 :ma2 :fg1 :w4)
                             {})
               :type "text"
               :id "new-item"
               :name "new-item"
               :ref (fn [el]
                      (reset! input-ref el))
               :auto-focus true
               :value (<- :get :temp-item)
               :placeholder "enter a new item"
               :on-key-press (fn [e]
                               (js/console.log e)
                               (when (= 13 (.-charCode e))
                                 (.blur @input-ref)
                                 (! submit-event)))
               :on-change #(! :assoc :temp-item (-> % .-target .-value))}]
      [:div
       [:button {:style (s :bg-brand0 :pa2 :ma2 :fs0)
                 :on-click #(! submit-event)} submit-text]]]
     [:div {:style (s :fdr :jcsa)}
      (when is-edit?
        [:a {:style (s :ma3)
             :on-click #(! :firestore-delete-item)}
         "delete"])
      [:a {:style (s :ma3)
           :on-click #(! :assoc :show-menu nil :edit-item nil)}
       "close"]]]))

(defn item-block [item]
  (let [data (:data item)
        id (:id item)
        txt (get data "item")]
    [:div {:style (merge (s :mb1 :pa2 :bg-status1 :tl :fdr)
                         {:display "flex"})}
     [:div {:style (s :fg1)
            :on-click #(! :assoc :edit-item item :temp-item txt)}
      txt]
     #_[:a {:style (s :f6)
          :on-click #(! :firestore-delete-item id)}
      "remove"]]))

(defn logged-in []
  [:div
   (when-let [name (:display-name (<- :get :user))]
     [:p {:style (s :pb3 :pt3)} (str "Welcome " name)])
   [:div {:style (s :f3 :pb3 :pt3 :fwb)}
    "My shopping list"]
   [:div
    (doall (for [item (:docs (<- :firestore/on-snapshot {:path-collection [:users (<- :get :user :uid) :my-list]
                                                         :order-by [[:timestamp :asc]]}))]
             ^{:key (:id item)}
             [:div [item-block item]]))]
   
   [button {:on-click #(! :sign-out)} "logout"]
   (if (or (<- :get :show-menu)
           (<- :get :edit-item))
     [add-form]
     [:div {:style (merge (s :bg-status0 :f3 :pa2)
                          {:position "fixed"
                           :bottom 0
                           :right 0})
            :on-click #(! :assoc :show-menu true :edit-item nil :temp-item nil)}
      "add" [:br] "to list"])
   #_[:div {:style (merge (s :w100)
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
                       {:max-width 600
                        :min-height "100vh"
                        :margin "0 auto"})}
   [:p {:style (s :f2 :pb2)} "LocaList  " (<- :get :profile)]
   [:p {:style (s :f3 :pb2)} "Corona Community Response"]
   [:p {:style (s :f4 :pb2)} "Supporting volunteer home delivery groups and local communities"]
   (if (<- :get :user)
       [logged-in]
       [login-create-account])])
