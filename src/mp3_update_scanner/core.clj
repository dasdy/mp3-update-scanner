(ns mp3-update-scanner.core
     (:gen-class)
     (:use mp3-update-scanner.libscan
           mp3-update-scanner.lastfm)
     (:require [clojure.tools.cli :refer [parse-opts]]
               [clojure.data.json :as json]
               [clojure.java.io :refer [file]]
               [mp3-update-scanner.lastfm :as lastfm]))

(defn save-collection [collection path]
  (spit path (json/write-str
              (into (sorted-map)
                    (map (fn [[k v]] [k (sort (keys v))])
                         collection))))
  collection)

(defn read-collection [path]
  (json/read-str (slurp path)))

(defn remove-trailing-0 [string]
  (apply str (remove #(= (int %) 0) string)))

(def cli-options
  [["-m" "--music-path PATH" "Path to your music library"
    :default nil]
   ["-c" "--cached-path PATH" "Path to collection if you have already scanned library"
    :default nil]
   ["-o" "--output PATH" "Path to output (results of music scan). Should default to path of cached-path or, if not given, to out.json"
    :default "out.json"]
   ["-i" "--ignore-path PATH" "Path to ignore file"
    :default nil]])

(defn parse-prog-options [args]
  (let [{:keys [music-path cached-path output ignore-path]}
        (:options (parse-opts args cli-options))]
    [music-path cached-path output ignore-path]))

(defn validate-args [[mpath cachepath _ _ :as args]]
  (if (not (or mpath cachepath))
    (println (str "You must specify at least one of --music-path or --cached-path options"))
    true))

(defn -main [& args]
  (let [[mpath cachepath outputpath ignorepath :as parsed-args] (parse-prog-options args)
        ignored-stuff (when (and ignorepath (.exists (file ignorepath)))
                           (read-collection ignorepath))]
   (when (validate-args parsed-args)
     (-> (if mpath (get-all-mp3-tags-in-dir mpath) [])
         (build-collection (if (and cachepath (.exists (file cachepath)))
                             (read-collection cachepath)
                             {}))
         (only-listened-authors)
         (remove-ignored ignored-stuff)
         (save-collection cachepath)
         (get-authors-from-lastfm)
         (remove-ignored ignored-stuff)
         (save-collection outputpath)))))
