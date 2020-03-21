(ns localist.views
  (:require
   [re-frame.core :as re-frame]
   [localist.subs :as subs]
   ))

(defn main-panel []
  (let [name (re-frame/subscribe [:name-firestore])]
    [:div
     [:h1 "Hello 2from " @name]
     ]))
