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

(reg-sub
 :name-firestore
 (fn [_ _]
   (re-frame/subscribe [:firestore/on-snapshot {:path-document [:users :test1]}]))
 (fn [value _]
   (with-out-str (cljs.pprint/pprint (:data value)))))