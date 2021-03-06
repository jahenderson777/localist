;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns localist.re-frame-firebase.database
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [re-frame.loggers :refer [console]]
   [reagent.ratom :as ratom :refer [make-reaction]]
   [iron.re-utils :refer [<sub >evt event->fn sub->fn]]
   [iron.utils :as utils]
   ["firebase" :as firebase-app]
   ["firebase/database" :as firebase-database]
   [localist.re-frame-firebase.helpers :refer [js->clj-tree success-failure-wrapper]]
   [localist.re-frame-firebase.core :as core]
   [localist.re-frame-firebase.specs :as specs]))

;(def firestore (js/require "firebase/database"))

(s/def ::cache (s/nilable (s/keys)))

(defn- fb-ref [path]
  {:pre [(utils/validate ::specs/path path)]}
  (.ref (js/firebase.database)
        (str/join "/" (clj->js path))))

(defn- setter [{:keys [path value on-success on-failure]}]
  (.set (fb-ref path)
        (clj->js value)
        (success-failure-wrapper on-success on-failure)))

(def write-effect setter)

(defn- updater [{:keys [path value on-success on-failure]}]
  (.update (fb-ref path)
           (clj->js value)
           (success-failure-wrapper on-success on-success)))

(def ^:private update-effect updater)

(defn push-effect [{:keys [path value on-success on-failure] :as all}]
  (let [key (.-key (.push (fb-ref path)))]
    (setter (assoc all
              :on-success #((event->fn on-success) key)
              :path (conj path key)))))

(defn once-effect [{:keys [path on-success on-failure]}]
  (.once (fb-ref path)
         "value"
         #((event->fn on-success) (js->clj-tree %))
         #((event->fn on-failure) %)))

(defn on-value-sub [app-db [_ {:keys [path on-failure]}]]
  (if path
    (let [ref (fb-ref path)
          ;; [TODO] Potential bug alert:
          ;;        We are caching the results, keyed only by path, and we clear
          ;;        the cache entry in :on-dispose.  I can imagine situations
          ;;        where this would be problematic if someone tried watching the
          ;;        same path from two code locations. If this becomes an issue, we
          ;;        might need to add an optional disambiguation argument to the
          ;;        subscription.
          ;;        Note that firebase itself seems to guard against this by using
          ;;        the callback itself as a unique key to .off.  We can't do that
          ;;        (modulo some reflection hack), since we use the id as part of
          ;;        the callback closure.
          id path
          callback #(>evt [::on-value-handler id (js->clj-tree %)])]
      (.on ref "value" callback (event->fn (or on-failure (core/default-error-handler))))
      (make-reaction
       (fn [] (get-in @app-db [::cache id] []))
       :on-dispose #(do (.off ref "value" callback)
                        (>evt [::on-value-handler id nil]))))
    (do
      (console :error "Received null Firebase on-value request")
      (make-reaction
       (fn []
         ;; Minimal dummy response, to avoid blowing up caller
         nil)))))

(re-frame/reg-event-db
 ::on-value-handler
 (fn [app-db [_ id value]]
   (if value
     (assoc-in app-db [::cache id] value)
     (update app-db ::cache dissoc id))))
