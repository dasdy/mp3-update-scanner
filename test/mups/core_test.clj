(ns mups.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [mups.core :refer :all]
            [mups.libscan :refer :all]
            [mups.lastfm :refer :all]))

(defn album-info [track-count & [album-name]]
  (let [res {"song-count" track-count}]
        (if album-name
          (assoc res "title" album-name)
          res)))

(deftest add-author-info-tests
  (testing "add-author to existing authors"
    (is (= (add-author-info {:artist "artist1" :album "album"}
                            {"artist1" {"album" (album-info 1)}})
           {"artist1" {"album" (album-info 2)}}))
    (is (= (add-author-info {:artist "artist1" :album "album"}
                            {"artist1" {"album" (album-info 1)}
                             "artist2" {"album3" (album-info 6)}
                             "artist3" {"album4" (album-info 8)}})
           {"artist1" {"album" (album-info 2)}
            "artist2" {"album3" (album-info 6)}
            "artist3" {"album4" (album-info 8)}}))
    (is (= (add-author-info {:artist "artist1" :album "album2"}
                            {"artist1" {"album" (album-info 1)
                                        "album2" (album-info 3)}
                             "artist2" {"album3" (album-info 6)}
                             "artist3" {"album4" (album-info 8)}})
           {"artist1" {"album" (album-info 1)
                       "album2" (album-info 4)}
            "artist2" {"album3" (album-info 6)}
            "artist3" {"album4" (album-info 8)}})))
  (testing "add-author to non-existing authors"
    (is (= (add-author-info {:artist "artist1" :album "album"}
                            {})
           {"artist1" {"album" (album-info 1)}}))
    (is (= (add-author-info {:artist "artist2" :album "album"}
                            {"artist1" {"album" (album-info 1)}})
           {"artist1" {"album" (album-info 1)}
            "artist2" {"album" (album-info 1)}})))
  (testing "add-author to authors, without albums"
    (is (= (add-author-info {:artist "artist1" :album "album2"}
                            {"artist1" {"album" (album-info 9)}})
           {"artist1" {"album" (album-info 9)
                       "album2" (album-info 1)}}))))

(deftest author-song-count-tests
  (testing "on empty collection"
    (is (= 0 (author-song-count {} "author")))
    (is (= 0 (author-song-count {"author2" {"album2" (album-info 5)}} "author"))))
  (testing "count tests"
    (is (= 5 (author-song-count {"author" {"album1" (album-info 5)}} "author")))
    (is (= 5 (author-song-count {"author" {"album1" (album-info 2)
                                           "album2" (album-info 3)}} "author")))))

(deftest build-collection-tests
  (testing "building collection of mp3 info data"
    (is (= (build-collection '({:artist "author2" :album "album"}
                               {:artist "author" :album "album"}
                               {:artist "author" :album "album2"})
                             {})
         {"author" {"album" (album-info 1) "album2" (album-info 1)}
          "author2" {"album" (album-info 1)}}))
    (is (= (build-collection '({:artist "author2" :album "album"}
                               {:artist "author" :album "album"}
                               {:artist "author" :album "album2"})
                             {"author" {"album" (album-info 10)}})
         {"author" {"album" (album-info 11) "album2" (album-info 1)}
          "author2" {"album" (album-info 1)}}))))

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

(deftest author-is-listened-tests
  (testing "is-listened?"
    (is (author-is-listened ["author" {"a" (album-info 5) "b" (album-info 9)}]))
    (is (not (author-is-listened ["author" {"a" (album-info 5)}])))
    (is (author-is-listened ["author" {"a" (album-info 1)
                                        "b" (album-info 1)
                                        "c" (album-info 1)
                                         "d" (album-info 1)
                                         "e" (album-info 1)
                                         "f" (album-info 1)}]))
    (is (not (author-is-listened ["author" {"a" (album-info 1)
                                            "b" (album-info 2)
                                            "c" (album-info 2)}]))))
  (testing "same thing on a list"
    (is (= {"author1" {"a" (album-info 5) "b" (album-info 9)}
            "author3" {"a" (album-info 1)
                       "b" (album-info 1)
                       "c" (album-info 1)
                       "d" (album-info 1)
                       "e" (album-info 1)
                       "f" (album-info 2)}}
           (only-listened-authors {"author1" {"a" (album-info 5) "b" (album-info 9)}
                                   "author2" {"a" (album-info 5)}
                                   "author3" {"a" (album-info 1)
                                              "b" (album-info 1)
                                              "c" (album-info 1)
                                              "d" (album-info 1)
                                              "e" (album-info 1)
                                              "f" (album-info 2)}
                                   "author4" {"a" (album-info 1)
                                              "b" (album-info 2)
                                              "c" (album-info 1)}})))))

(deftest cli-args-tests
  (testing "cli-args-values"
    (is (= ["music" "cache" "out" "ignore" "lastfm"]
           (parse-prog-options ["--ignore-path=ignore" "--output=out"
                                "--music-path=music" "--cached-path=cache"
                                "--lastfm=lastfm"])))
    (is (= ["music" "cache" "diff.json" "ignore" "lastfm.json"]
           (parse-prog-options ["--ignore-path=ignore"
                                "--music-path=music"
                                "--cached-path=cache"])))
    (is (= ["music" nil "diff.json" nil "lastfm.json"]
           (parse-prog-options ["--music-path=music"])))
    (is (validate-args ["music" nil "diff.json" nil nil]))
    (is (validate-args [nil "cache" nil nil nil]))
    (is (not (validate-args [nil nil "diff.json" "ignore.json" nil])))))

(deftest ignore-tests
  (testing "ignore-test"
    (is (= (remove-ignored {"author" {"album1" (album-info 1)
                                      "album2" (album-info 1)
                                      "album3" (album-info 3)
                                      "album4" (album-info 4)}
                            "author2" {"album1" (album-info 2)
                                       "album4" (album-info 5)}}
                           {"authors" ["author2"]
                            "albums" ["album1" "album3"]
                            "author_albums" {"author" ["album2"]}})
           {"author" {"album4" (album-info 4)}}))
    (is (= (remove-singles {"author" {"s" (album-info 1) "s3" (album-info 2)}
                            "author2" {"x" (album-info 2) "k" (album-info 1)}})
           {"author" {"s3" (album-info 2)}
            "author2" {"x" (album-info 2)}}))

    (is (= (remove-singles {"author" {"s" (album-info 1) "s3" nil}
                            "author2" {"x" (album-info 2) "k" (album-info 1)}})
           {"author" {}
            "author2" {"x" (album-info 2)}}))))

(deftest diff-tests
  (testing "find missing albums in one author"
    (is (= (find-author-missing-albums {"a" (album-info 1) "b" (album-info 1)}
                                       {"a" (album-info 1) "b" (album-info 1) "c" (album-info 1)})
           {"you have" {},
            "you miss" [(album-info 1 "c")],
            "both have" [(album-info 1 "a") (album-info 1 "b")]}))))

(deftest serialization-tests
  (testing "save-collection"
    (with-local-vars [ file-buf nil]
      (with-redefs [spit (fn [path str] (var-set file-buf str))]
        (do (save-collection :json {"a" {"b" (album-info 1)}} "some-path")
            (is (= @file-buf "{\n  \"a\" : [\n    \"b\"\n  ]\n}"))))))
  (testing "read-collection"
    (with-redefs [slurp (fn [_] "{\"a\":[\"b\"]}")]
      (is (= (read-collection :json "a.json")
             {"a" ["b"]}))))
  (testing "saving diff"
    (with-redefs [spit (fn [path data] data)]
      (is (= (save-diff :json {"you have" {} "you miss" {} "both have" {}} "")
            "{\n  \"both have\" : { },\n  \"you have\" : { },\n  \"you miss\" : { }\n}")))))

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
    (is (= (albums-from-lastfm {"topalbums" {"album" [{"name" "album1"
                                                       "artist" {"name" "artist1"}}
                                                      {"name" "album2"
                                                       "artist" {"name" "artist1"}}]}})
           {"album1" (album-info 1) "album2" (album-info 1)})))
  (testing "is-error-response"
    (is (is-error-response {"error" 15 "message" "some error message"}))
    (is (not (is-error-response {"topalbums" {"album" [{"name" "album1"}
                                                       {"name" "album2"}]}})))))