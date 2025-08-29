(ns pod-flow-content.core
  (:require [clj-http.client :as http]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [config.core :refer [env]]))

(defn fetch-rss []
  (let [rss-stream (:body (http/get (:rss-url env) {:as :stream}))
        parsed (xml/parse (io/input-stream rss-stream))]
    (->> (xml-seq parsed)
         (filter #(= :item (:tag %))))))

(defn extract-episode-info [item]
  (let [find-tag (fn [tag]
                   (->> (:content item)
                        (filter #(= tag (:tag %)))
                        first))
        title-el (find-tag :title)
        enclosure-el (find-tag :enclosure)
        title (-> title-el :content first)
        url (get-in enclosure-el [:attrs :url])]
    {:title title
     :url url}))

(defn sanitize [s]
  (-> s
      (str/replace #"[^a-zA-Z0-9\- ]" "")
      (str/replace #" " "_")))

(defn download-mp3 [{:keys [title url]}]
  (let [filename (str "downloads/" (sanitize title) ".mp3")]
    (println "Downloading:" title)
    (with-open [in (io/input-stream url)
                out (io/output-stream filename)]
      (io/copy in out))
    (println "Saved to:" filename)))

(defn run []
  (doseq [item (fetch-rss)]
    (-> item
        extract-episode-info)))
        ;download-mp3)))

(defn write-feed [feed] (with-open [w (clojure.java.io/writer "data/rss-feed.txt")]
                          (doseq [line feed]
                            (.write w (str line "\n")))))

(defn write-feed!
  [feed filepath]
  (with-open [w (io/writer filepath)]
    (doseq [el feed]
      (xml/emit el w)
      (.write w "\n")))) ; optional newline between elements

(comment
    (:rss-url env)
    (write-feed! (fetch-rss) "data/rss-feed.txt")
    (doseq [item (fetch-rss)]
      (-> item
          extract-episode-info))
    (run)
    (require '[config.core :refer [env]])
    (println env)
    (slurp "resources/config.edn")
    (-> (fetch-rss) first prn)



    nil)

