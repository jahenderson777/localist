(ns localist.re-frame-firebase.storage
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [reagent.ratom :as ratom :refer [make-reaction]]
   [iron.re-utils :as re-utils :refer [<sub >evt event->fn sub->fn]]
   [iron.utils :as utils]
   ["firebase" :as firebase]
   ["firebase/firestore" :as firestore]
   [cljs-uuid-utils.core :as uuid]
   [localist.re-frame-firebase.core :as core]
   [localist.re-frame-firebase.specs :as specs]
   [localist.re-frame-firebase.helpers :refer [promise-wrapper]]))


(defn resize-image [file max-width on-complete]
  (let [max-width (or max-width 800)
        reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [readerEvent]
            (let [image (js/Image.)]
              (set! (.-onload image)
                    (fn [imageEvent]
                      (let [canvas (js/document.createElement "canvas")
                            width (.-width image)
                            height (.-height image)
                            factor (if (> width max-width)
                                     (/ max-width width)
                                     1)
                            _ (aset canvas "width" (* factor width))
                            _ (aset canvas "height" (* factor height))
                            ctx  (.getContext canvas "2d")
                            _ (.drawImage ctx image 0 0 (* factor width) (* factor height))]
                        (.toBlob canvas (fn [blob]
                                          (on-complete blob))))))
              (set! (.-src image) (.. readerEvent -target -result)))))
    (.readAsDataURL reader file)))

(defn upload* [{:keys [filename file on-progress on-error on-complete]}]
  (let [root-ref (.. firebase storage ref)
        new-file-ref (.child root-ref filename)
        upload-task (.put new-file-ref file)]
    (.on upload-task "state_changed"
         (fn [snapshot]
           (let [progress (* 100 (/ (.-bytesTransferred snapshot) (.-totalBytes snapshot)))]
             (if on-progress
               (on-progress progress)
               (println "upload progress:" progress))))
         (fn [error]
           (if on-error 
             (on-error error)
             (js/alert error)))
         (fn []
           (.then (.. upload-task -snapshot -ref getDownloadURL)
                  (fn [downloadURL]
                    (if on-complete
                      (on-complete downloadURL)
                      (println "file available at " downloadURL))))))))

(defn upload [{:keys [file max-width] :as opts}]
  (let [ext (last (str/split (.-name file) "."))
        filename (str "images/" (uuid/uuid-string (uuid/make-random-uuid)) "." ext)]
    (resize-image file max-width
                  (fn [blob]
                    (upload* (assoc opts 
                                    :file blob
                                    :filename filename))))))