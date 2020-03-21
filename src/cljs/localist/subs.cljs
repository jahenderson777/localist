(ns localist.subs
  (:require
   [re-frame.core :as re-frame :refer [reg-sub]]))

(reg-sub
 :name
 (fn [db]
   (:name db)))

(reg-sub
 :get
 (fn [db [_ & path]]
   (get-in db path)))

#_(reg-sub
 :name-firestore
 :<- [:get :user :uid]
 (fn [{:keys [user]} _]
   (let [{:keys [uid]} user]
     (re-frame/subscribe [:firestore/on-snapshot {:path-document [:users uid :my-list]}])))
 (fn [value _]
   (with-out-str (cljs.pprint/pprint (:data value)))))