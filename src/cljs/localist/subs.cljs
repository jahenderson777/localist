(ns localist.subs
  (:require
   [localist.re-frame-firebase.firestore :as firestore]
   [re-frame.core :as re-frame :refer [reg-sub subscribe]]))

(reg-sub
 :name
 (fn [db]
   (:name db)))

(reg-sub
 :get
 (fn [db [_ & path]]
   (get-in db path)))

#_(reg-sub
 :shopping2
 :<- [:firestore/on-snapshot {:path-collection ["communities" "ashburton" "items"]}]
 (fn [query-result _]
   (let [docs (:docs query-result)
         docs (map :data docs)
         docs (map (fn [doc] (update doc "user" #(second (firestore/PathReference->clj %))))
                   docs)]
     (into (sorted-map) (group-by (fn [doc] [(get doc "user")
                                             (get doc "type")]) docs)))))


#_(reg-sub
 :shopping-all
 (fn [db _])
 :<- [:firestore/on-snapshot {:path-collection [:users id :shopping]
                              :order-by [[:timestamp :asc]]}])

