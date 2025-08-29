(ns pod-flow-content.core
  (:require [clj-http.client :as http]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [config.core :refer [env]]
            [clojure.edn :as edn])
  (:import (java.io PushbackReader)))

(defn fetch-rss [url]
  (let [rss-stream (:body (http/get url {:as :stream}))
        parsed (xml/parse (io/input-stream rss-stream))]
    (->> (xml-seq parsed)
         (filter #(= :item (:tag %))))))

(defn extract-episode-info [item]
  (let [find-tag (fn [tag]
                   (->> (:content item)
                        (filter #(= tag (:tag %)))
                        first))
        title-el (find-tag :title)
        title (-> title-el :content first)
        slug-el (find-tag :episodeUrl)
        slug (-> slug-el :content first)
        enclosure-el (find-tag :enclosure)
        audio-url (get-in enclosure-el [:attrs :url])
        image-el (find-tag :image)
        image-url (get-in image-el [:attrs :href])]
    {:title     title
     :slug      slug
     :audio-url audio-url
     :image-url image-url}))


(defn download-mp3 [{:keys [slug url]}]
  (let [filename (str "data/" slug ".mp3")]
    (println "Downloading:" slug)
    (with-open [in (io/input-stream url)
                out (io/output-stream filename)]
      (io/copy in out))
    (println "Saved to:" filename)))

(defn rss->edn [filepath url]
  (->> url
      (fetch-rss)
      (mapv extract-episode-info)
      (pr-str)
      (spit filepath)))

(defn read-edn-file [filepath]
  (with-open [r (io/reader filepath)]
    (edn/read (PushbackReader. r))))

(defn read-episode-info [filepath slug]
  (->> filepath
    (read-edn-file)
    (filter #(= slug (:slug %)))
    (first)))

(defn write-feed!
  "dev utility function to see what the feed structure is like"
  [feed filepath]
  (with-open [w (io/writer filepath)]
    (doseq [el feed]
      (xml/emit el w)
      (.write w "\n"))))                                    ; optional newline between elements

(comment
  (write-feed! (fetch-rss) "data/rss-feed.txt")
  (rss->edn "data/rss-feed.edn" (:rss-url env))
  (read-edn-file "data/rss-feed.edn")
  (read-episode-info "data/rss-feed.edn" "mientras-preparamos-las-proximas-temporadas")
  nil)

