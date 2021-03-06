 (ns mups.cli-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cheshire.core :refer [generate-string]]
            [mups.core :refer :all]
            [mups.libscan :refer :all]
            [mups.lastfm :refer :all]
            [mups.diffgen :refer :all]
            [mups.collection :refer :all]
            [mups.cli :refer :all]
            [mups.data :refer :all]
            [mups.utils :refer :all]
            [mock-clj.core :as mc]))

(defn album-info [track-count & [album-name]]
  (->Album track-count album-name nil nil))

(defn artist-info [albums & [name url]]
  (->Artist name albums url))

(deftest add-author-info-tests
  (testing "add-author to existing authors"
    (is (= (add-author-info {:artist "artist1" :album "album"}
                            {"artist1" (artist-info {"album" (album-info 1)})})
           {"artist1" (artist-info {"album" (album-info 2)})}))
    (is (= (add-author-info {:artist "artist1" :album "album"}
                            {"artist1" (artist-info {"album" (album-info 1)})
                             "artist2" (artist-info {"album3" (album-info 6)})
                             "artist3" (artist-info {"album4" (album-info 8)})})
           {"artist1" (artist-info {"album" (album-info 2)})
            "artist2" (artist-info {"album3" (album-info 6)})
            "artist3" (artist-info {"album4" (album-info 8)})}))
    (is (= (add-author-info {:artist "artist1" :album "album2"}
                            {"artist1" (artist-info {"album" (album-info 1)
                                                     "album2" (album-info 3)})
                             "artist2" (artist-info {"album3" (album-info 6)})
                             "artist3" (artist-info {"album4" (album-info 8)})})
           {"artist1" (artist-info {"album" (album-info 1)
                                    "album2" (album-info 4)})
            "artist2" (artist-info {"album3" (album-info 6)})
            "artist3" (artist-info {"album4" (album-info 8)})})))
  (testing "add-author to non-existing authors"
    (is (= (add-author-info {:artist "artist1" :album "album"}
                            {})
           {"artist1" (artist-info {"album" (album-info 1 "album")}
                                   "artist1")}))
    (is (= (add-author-info {:artist "artist2" :album "album"}
                            {"artist1" (artist-info
                                        {"album" (album-info 1 "album")}
                                        "artist1")})
           {"artist1" (artist-info {"album" (album-info 1 "album")} "artist1")
            "artist2" (artist-info {"album" (album-info 1 "album")} "artist2")})))
  (testing "add-author to authors, without albums"
    (is (= (add-author-info {:artist "artist1" :album "album2"}
                            {"artist1" (artist-info {"album" (album-info 9 "album")}
                                                    "artist1")})
           {"artist1" (artist-info {"album" (album-info 9 "album")
                                    "album2" (album-info 1 "album2")}
                                   "artist1")})))
  (testing "source name should be saved as attribute, with base case"
    (is (= (add-author-info {:artist "SoMe ArTiSt" :album "ALbUmInFo"}
                            {})
           {"some artist" (artist-info {"albuminfo" (album-info 1 "ALbUmInFo")}
                                       "SoMe ArTiSt")})))
  (testing "save first name in library during scan"
    (is (= (add-author-info {:artist "SoMe ArTiSt" :album "ALbUmInFo"}
                         {"some artist" (artist-info {"albuminfo" (album-info 1 "albumINFO")}
                                                     "sOmE aRtIsT")})
           {"some artist" (artist-info {"albuminfo" (album-info 2 "albumINFO")}
                                       "sOmE aRtIsT")}))))

(deftest author-song-count-tests
  (testing "on empty collection"
    (is (= 0 (author-song-count {} "author")))
    (is (= 0 (author-song-count {"author2" {"album2" (album-info 5)}} "author"))))
  (testing "count tests"
    (is (= 5 (author-song-count {"author" (artist-info {"album1" (album-info 5)})}
                                "author")))
    (is (= 5 (author-song-count {"author" (artist-info {"album1" (album-info 2)
                                                        "album2" (album-info 3)})}
                                "author")))))

(deftest build-collection-tests
  (testing "building collection of mp3 info data"
    (is (= (build-collection '({:artist "author2" :album "album"}
                               {:artist "author" :album "album"}
                               {:artist "author" :album "album2"})
                             {})
           {"author" (artist-info {"album" (album-info 1 "album")
                                   "album2" (album-info 1 "album2")}
                                  "author")
            "author2" (artist-info {"album" (album-info 1 "album")}
                                   "author2")}))
    (is (= (build-collection '({:artist "author2" :album "album"}
                               {:artist "author" :album "album"}
                               {:artist "author" :album "album2"})
                             {"author" (artist-info {"album" (album-info 10 "album")}
                                                    "author")})
           {"author" (artist-info {"album" (album-info 11 "album")
                                   "album2" (album-info 1 "album2")}
                                  "author")
            "author2" (artist-info {"album" (album-info 1 "album")}
                                   "author2")}))))

(defn file-mock
  "object with getName and getPath properties, same as path"
  [path filename]
  (proxy [java.io.File] [path]
    (getName [] filename)
    (getPath [] (str path "/" filename))))

(deftest scan-mp3-in-folder-tests
  (let [folder "folder"
        file-list (fn [& args] (map #(file-mock folder %) args))]
   (testing "searching in folder without files"
     (with-redefs [file-seq (constantly (file-list "file1" "file2"))]
       (is (= (get-all-mp3-in-dir ".")
              '())))
     (with-redefs [file-seq (constantly (file-list))]
       (is (= (get-all-mp3-in-dir ".")
              '()))))
   (testing "searching for files"
     (with-redefs [file-seq (constantly (file-list "file1.mp3" "file2.mp3" "file3.mp3" "file4"))]
       (is (= (set (map #(.getName %) (get-all-mp3-in-dir ".")))
              #{"file1.mp3" "file2.mp3" "file3.mp3"}))))))

(deftest build-full-collection-tests
  (testing "passing music-path will result in calling get-all-mp3-tags"
    (mc/with-mock [get-all-mp3-tags-in-dir []]
      (build-full-collection "some-path" nil)
      (is (mc/called? get-all-mp3-tags-in-dir))))
  (testing "passing only cache will result in reading cache"
    (mc/with-mock [get-all-mp3-tags-in-dir []
                   file-exists true
                   read-collection {}]
      (build-full-collection nil "path-to-cache")
      (is (not (mc/called? get-all-mp3-tags-in-dir)))
      (is (mc/called? read-collection))))
  (testing "passing both cache and music path will merge collections"
    (mc/with-mock [get-all-mp3-tags-in-dir [1 2 3]
                   file-exists true
                   read-collection {4 5}
                   build-collection {}]
      (build-full-collection "path-to-music" "path-to-cache")
      (is (mc/called? get-all-mp3-tags-in-dir))
      (is (mc/called? read-collection))
      (is (= (mc/last-call build-collection) [[1 2 3] {4 5}])))))

(deftest build-user-collection-tests
  (testing "uses only-listened authors and remove-ignored, returns same collection"
    (mc/with-mock [build-full-collection
                   {"author" (artist-info {"album" (album-info 15)
                                           "ignored-album" (album-info 10)})}]
      (is (= (build-user-collection "some-path" "other-path" (->IgnoreCollection [] ["ignored-album"] []))
             {"author" (artist-info {"album" (album-info 15)})})))))

(deftest fetch-details-in-scanned-collection-tests
  (testing "fetch downloads detailed info about author and not-ignored albums"
    (mc/with-mock [get-authors-from-lastfm {"author" (artist-info {"album" (album-info 15)
                                                                   "ignored-album" (album-info 10)})}
                   fetch-album-details {}]
      (is (= (fetch-details-in-scanned-collection
              {"author" (artist-info {"album" (album-info 15)})}
              (->IgnoreCollection [] ["ignored-album"] []))
             {}))
      (is (mc/called? get-authors-from-lastfm))
      (is (= (mc/last-call fetch-album-details)
             [{"author" (artist-info {"album" (album-info 15)})}])
          "remove-ignored should have removed ignored-album after it was fetched by get-auhors-form-lastfm"))))

(deftest author-is-listened-tests
  (testing "is-listened?"
    (is (author-is-listened ["author" (artist-info {"a" (album-info 5) "b" (album-info 9)})]))
    (is (not (author-is-listened ["author" (artist-info {"a" (album-info 5)})])))
    (is (author-is-listened ["author" (artist-info {"a" (album-info 1)
                                                    "b" (album-info 1)
                                                    "c" (album-info 1)
                                                    "d" (album-info 1)
                                                    "e" (album-info 1)
                                                    "f" (album-info 1)})]))
    (is (not (author-is-listened ["author"
                                  {:albums
                                   {"a" (album-info 1)
                                    "b" (album-info 2)
                                    "c" (album-info 2)}}]))))
  (testing "same thing on a list"
    (is (= {"author1" {:albums
                       {"a" (album-info 5)
                        "b" (album-info 9)}}
            "author3"
            {:albums
             {"a" (album-info 1)
              "b" (album-info 1)
              "c" (album-info 1)
              "d" (album-info 1)
              "e" (album-info 1)
              "f" (album-info 2)}}}
           (only-listened-authors {"author1" {:albums
                                              {"a" (album-info 5)
                                               "b" (album-info 9)}}
                                   "author2" {:albums {"a" (album-info 5)}}
                                   "author3" {:albums
                                              {"a" (album-info 1)
                                               "b" (album-info 1)
                                               "c" (album-info 1)
                                               "d" (album-info 1)
                                               "e" (album-info 1)
                                               "f" (album-info 2)}}
                                   "author4" {:albums
                                              {"a" (album-info 1)
                                               "b" (album-info 2)
                                               "c" (album-info 1)}}})))))

(deftest cli-args-tests
  (testing "cli-args-values"
    (is (= ["music" "cache" "out" "ignore" "lastfm"]
           (parse-prog-options ["--ignore-path=ignore" "--output=out"
                                "--music-path=music" "--cached-path=cache"
                                "--lastfm=lastfm"])))
    (is (= ["music" "cache" "diff.html" "ignore" nil]
           (parse-prog-options ["--ignore-path=ignore"
                                "--music-path=music"
                                "--cached-path=cache"])))
    (is (=  ["music" nil "diff.html" nil nil]
           (parse-prog-options ["--music-path=music"])))
    (is (validate-args ["music" nil "diff.json" nil nil]))
    (is (validate-args [nil "cache" nil nil nil]))
    (is (not (validate-args [nil nil "diff.json" "ignore.json" nil])))))

(deftest ignore-tests
  (testing "ignore-test"
    (is (= (remove-ignored {"author" (artist-info {"album1" (album-info 1)
                                                   "album2" (album-info 1)
                                                   "album3" (album-info 3)
                                                   "album4" (album-info 4)})
                            "author2" (artist-info {"album1" (album-info 2)
                                                    "album4" (album-info 5)})}
                           (->IgnoreCollection ["author2"] ["album1" "album3"] {"author" ["album2"]}))
           {"author" (artist-info {"album4" (album-info 4)})}))))

(deftest remove-singles-test
  (testing "removing singles"
    (is (= (remove-singles {"author" (artist-info {"s" (album-info 1)
                                                   "s3" (album-info 2)})
                            "author2" (artist-info {"x" (album-info 2)
                                                    "k" (album-info 1)})})
          {"author" (artist-info {"s3" (album-info 2)})
           "author2" (artist-info {"x" (album-info 2)})}))

   (is (= (remove-singles {"author" (artist-info {"s" (album-info 1)
                                                  "s3" nil})
                           "author2" (artist-info {"x" (album-info 2)
                                                   "k" (album-info 1)})})
          {"author" (artist-info {})
           "author2" (artist-info {"x" (album-info 2)})}))
   (is (= (remove-singles {"author" (artist-info {"s[single]" (album-info 12)
                                                  "s3 - single" (album-info 13)})
                           "author2" (artist-info {"x (single)" (album-info 2)
                                                   "k single" (album-info 10)})})
          {"author" (artist-info {})
           "author2" (artist-info {})}))))

(deftest mapping-tests
  (testing "mapping non-existing author"
    (is (= (map-collection {"the author" (artist-info {"some-album" (album-info 5)} "The Author")}
                           (->CollectionMapping {"The Author" "Author"} {}))
           {"author" (artist-info {"some-album" (album-info 5)} "The Author")})))
  (testing "mapping existing author"
    (is (= (map-collection {"the author" (artist-info {"some-album" (album-info 5)} "The Author")
                            "author" (artist-info {"some-album2" (album-info 6)} "Author")}
                           (->CollectionMapping {"The Author" "Author"} {}))
           {"author" (artist-info {"some-album" (album-info 5)
                                   "some-album2" (album-info 6)}
                                  "Author")}))))

(deftest diff-tests
  (testing "find missing albums in one author"
    (is (= (find-author-missing-albums (artist-info {"a" (album-info 1 "a")
                                                     "b" (album-info 1 "b")}
                                                    "artist")
                                       (artist-info {"a" (album-info 1 "a")
                                                     "b" (album-info 1 "b")
                                                     "c" (album-info 1 "c")}
                                                    "artist"))
           (->DiffItem "artist"
                       [(album-info 1 "a") (album-info 1 "b")]
                       []
                       [(album-info 1 "c")])))))

(deftest serialization-tests
  (testing "save-collection"
    (with-local-vars [ file-buf nil]
      (with-redefs [spit (fn [_ str] (var-set file-buf str))]
        (do (save-collection :json {"a" {"b" (album-info 1)}} "some-path")
            (is (= @file-buf
                   "{\n  \"a\" : {\n    \"b\" : {\n      \"song-count\" : 1,\n      \"title\" : null,\n      \"image-url\" : null,\n      \"album-url\" : null\n    }\n  }\n}"))))))
  (testing "read-collection"
    (with-redefs [slurp (fn [_] "{\"a\":[\"b\"]}")]
      (is (= (read-collection :json "a.json")
             {"a" ["b"]}))))
  (testing "saving diff"
    (with-redefs [spit (fn [_ data] data)]
      (is (= (save-diff :json {"author" {"you have" [] "you miss" [] "both have" []}} "")
             "{\n  \"author\" : {\n    \"both have\" : [ ],\n    \"you have\" : [ ],\n    \"you miss\" : [ ]\n  }\n}"))
      (is (= (clojure.string/replace
                             (save-diff :json {"author"
                                               {"you have" [(album-info 4 "b") (album-info 1 "a")]
                                                "you miss" []`
                                                "both have" []}}
                              "")
                             #"\s"
                             "")
           "{\"author\":{\"bothhave\":[],\"youhave\":[{\"song-count\":4,\"title\":\"b\",\"image-url\":null,\"album-url\":null},{\"song-count\":1,\"title\":\"a\",\"image-url\":null,\"album-url\":null}],\"youmiss\":[]}}")))))

(deftest get-author-from-lastfm-tests
  (testing "request-test")
  (testing "url test"
    (is (with-redefs [api-key "someapikey"]
         (re-seq
          #"http://[a-zA-Z.0-9/]+\?method=artist\.gettopalbums&artist=ArtistName&api_key=[a-zA-Z0-9]+&format=json"
          (lastfm-getalbums-url "ArtistName")))))
  (testing "get-authors-from-lastfm returns items with full author-info"
    (let [three-albums {"album" (album-info 1)
                        "album2" (album-info 1)
                        "album3" (album-info 1)}]
     (with-redefs [api-key "someapikey"
                   concur-get
                   (fn [urls]
                     (repeat (count urls)
                             {:body "dummy_response_body"}))
                   author-response->author-info (constantly three-albums)]
      (is (= {"author1" three-albums
              "author2" three-albums
              "author3" three-albums}
             (get-authors-from-lastfm {"author1" {"album" (album-info 12)}
                                       "author2" {"album2" (album-info 3)}
                                       "author3" {"album3" (album-info 5)}}))))))
  (testing "response -> album-info"
    (is (= (albums-from-lastfm {"topalbums" {"album" [{"name" "aLbuM1"
                                                       "artist" {"name" "artist1"}}
                                                      {"name" "aLbuM2"
                                                       "artist" {"name" "artist1"}}]
                                             "@attr" {"artist" "The artistName"}}})
           (artist-info {"album1" (album-info 1 "aLbuM1")
                         "album2" (album-info 1 "aLbuM2")}
                        "The artistName")))
    (is (= (album-response->album-info
               (generate-string {"album"
                                   {"name" "someAlbumName"
                                    "artist" "someArtistName"
                                    "url" "AlbumUrl"
                                    "image" [{"#text" "smallAlbumUrl"
                                              "size" "small"}
                                             {"#text" "largeAlbumUrl"
                                              "size" "large"}]
                                    "tracks" {"track" [1 2 3 4 5 6]}}}))

           (->Album 6 "someAlbumName" "largeAlbumUrl" "AlbumUrl"))))
  (testing "is-error-response"
    (is (is-error-response {"error" 15 "message" "some error message"}))
    (is (not (is-error-response {"topalbums" {"album" [{"name" "album1"}
                                                       {"name" "album2"}]}})))))

(deftest fetch-album-details-test
  (testing "fetch-album-returns-map-from-responses"
    (with-redefs [api-key "some-api-key"
                  concur-get (fn [urls] (map (constantly
                                              (generate-string {"album"
                                                                {"name" "someAlbumName"
                                                                 "artist" "someArtistName"
                                                                 "url" "AlbumUrl"
                                                                 "image" [{"#text" "smallAlbumUrl"
                                                                           "size" "small"}
                                                                          {"#text" "largeAlbumUrl"
                                                                           "size" "large"}]
                                                                 "tracks" {"track" [1 2 3 4 5 6]}}}))
                                            urls))]
      (is (= (fetch-album-details {"author" (artist-info {"album" (album-info 6)} "The Author")})
             {"author"
              (artist-info
               {"album" (->Album 6 "someAlbumName"  "largeAlbumUrl" "AlbumUrl")}
               "The Author")}))

      (is (= (fetch-album-details {"author" (artist-info {"album" (album-info 6)
                                                          "album2" (album-info 6)}
                                                         "The Author")})
             {"author"
              (artist-info
               {"album" (->Album 6 "someAlbumName"  "largeAlbumUrl" "AlbumUrl")
                "album2" (->Album 6 "someAlbumName"  "largeAlbumUrl" "AlbumUrl")}
               "The Author")}))

      (is (= (fetch-album-details {"author" (artist-info {"album" (album-info 6)
                                                          "album2" (album-info 6)}
                                                         "The Author")

                                   "author2" (artist-info {"album3" (album-info 6)
                                                           "album4" (album-info 6)}
                                                          "The Author2")})
             {"author"
              (artist-info
               {"album" (->Album 6 "someAlbumName"  "largeAlbumUrl" "AlbumUrl")
                "album2" (->Album 6 "someAlbumName"  "largeAlbumUrl" "AlbumUrl")}
               "The Author")
              "author2"
              (artist-info
               {"album3" (->Album 6 "someAlbumName"  "largeAlbumUrl" "AlbumUrl")
                "album4" (->Album 6 "someAlbumName"  "largeAlbumUrl" "AlbumUrl")}
               "The Author2")})))))

(deftest html-diff-generation
  (testing "album-info creates correct hiccup structure"
    (is (= (album-info-html (->Album 12 "someAlbumName" "imageUrl" "albumUrl"))
           [:div.album-info
            [:img {:src "imageUrl" :height 120}]
            [:a {:href "albumUrl"} "(12)someAlbumName"]]))
    (is (= (album-info-html (album-info 12 "someAlbumName"))
           [:div.album-info "(12)someAlbumName"]))
    (is (= (album-info-html (->Album nil "someAlbumName" "imageUrl" "albumUrl"))
           [:div.album-info
            [:img {:src "imageUrl" :height 120}]
            [:a {:href "albumUrl"} "(?)someAlbumName"]])))
  (testing "albums-list-html creates unordered list"
    (is (= (albums-list-html [(->Album 12 "album1" "image1Url" "album1Url")
                              (->Album 12 "album2" "image2Url" "album2Url")])
           [:ul.albums-list
            [[:li
               [:div.album-info
                [:img {:src "image1Url" :height 120}]
                [:a {:href "album1Url"} "(12)album1"]]]
             [:li
              [:div.album-info
               [:img {:src "image2Url" :height 120}]
               [:a {:href "album2Url"} "(12)album2"]]]]])))
  (testing "diff-item-html creates summary tag"
    (is (= (diff-item-html "message" [(->Album 12 "album1" "image1Url" "album1Url")])
           [:div.diff-item
            [:details
             [:summary "message(1)"]
             [:ul.albums-list
              [[:li
                [:div.album-info
                 [:img {:src "image1Url" :height 120}]
                 [:a {:href "album1Url"} "(12)album1"]]]]]]])))
  (testing "artist-list-html creates diff with 3 sub-lists"
    (is (= (artist-list-html {"aartist" (->DiffItem "aartist"
                                                    []
                                                    [(->Album nil "album1" "image1Url" "album1Url")]
                                                    [(->Album nil "album2" "image2Url" "album2Url")
                                                     (->Album nil "album3" "image3Url" "album3Url")])
                              "bartist" (->DiffItem "bartist"
                                                    [(->Album nil "album4" "image4Url" "album4Url")]
                                                    []
                                                    [])})
           [[:div.artist
             "aartist"
             [:div.diff-item
              [:details
               [:summary "you have(1)"]
               [:ul.albums-list
                [[:li
                   [:div.album-info
                    [:img {:src "image1Url", :height 120}]
                    [:a {:href "album1Url"} "(?)album1"]]]]]]]
             [:div.diff-item
              [:details
               [:summary "you miss(2)"]
               [:ul.albums-list
                [[:li
                   [:div.album-info
                    [:img {:src "image2Url", :height 120}]
                    [:a {:href "album2Url"} "(?)album2"]]]
                 [:li
                  [:div.album-info
                   [:img {:src "image3Url", :height 120}]
                   [:a {:href "album3Url"} "(?)album3"]]]]]]]
             [:div.diff-item
              [:details [:summary "both have(0)"] [:ul.albums-list ()]]]]
            [:div.artist
             "bartist"
             [:div.diff-item
              [:details [:summary "you have(0)"] [:ul.albums-list ()]]]
             [:div.diff-item
              [:details [:summary "you miss(0)"] [:ul.albums-list ()]]]
             [:div.diff-item
              [:details
               [:summary "both have(1)"]
               [:ul.albums-list
                [[:li
                   [:div.album-info
                    [:img {:src "image4Url", :height 120}]
                    [:a {:href "album4Url"} "(?)album4"]]]]]]]]])))
  (testing "display-name attributes is used to generate html"
    (is (= (artist-list-html {"aartist" (->DiffItem "The Artist" [] [] [])})
           [[:div.artist
             "The Artist"
             [:div.diff-item
              [:details [:summary "you have(0)"] [:ul.albums-list ()]]]
             [:div.diff-item
              [:details [:summary "you miss(0)"] [:ul.albums-list ()]]]
             [:div.diff-item
              [:details [:summary "both have(0)"] [:ul.albums-list ()]]]]])))
  (testing "grouped-artists-list-html groups artists by first letter"
    (with-redefs [artist-list-html identity]
      (is (= (grouped-artists-list-html {"an artist" "an artist description"
                                         "a second artist" "second description"
                                         "third artist" "third description"
                                         "fourth artist" "fourth description"})
             [[:div.artist-list
                [:details
                 [:summary "a"]
                 [["an artist" "an artist description"]
                  ["a second artist" "second description"]]]]
              [:div.artist-list
               [:details
                [:summary "f"]
                [["fourth artist" "fourth description"]]]]
              [:div.artist-list
               [:details [:summary "t"] [["third artist" "third description"]]]]])))))
