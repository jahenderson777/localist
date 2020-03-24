 #_(if (or (<- :get :show-menu)
           (<- :get :edit-item))
     [add-form]
     [:div {:style (merge (s :bg-status0 :f3 :pa2)
                          {:position "fixed"
                           :bottom 0
                           ;:right 0
                           })
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
       "close"]])]


[:a {:style (s :f6)
     :on-click #(! :firestore-delete-item id)}
 "remove"]