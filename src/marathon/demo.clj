;;this is a demonstration of how to do a marathon
;;run, to replicate the results submitted.
(ns marathon.demo
  (:require [marathon         [analysis :as a]]
            [marathon.ces     [core :as core]]
            [marathon.visuals [patches :as patches]]
            [spork.util       [io :as io]]))

(def ep "C:\\Users\\1143108763.C\\srm\\notionalbase.xlsx")

(defn build-patches
  "Given a path to a processed run directory, renders the 
   processed history into a stylized HTML file.  The styling 
   is compatible with web browsers, and can be imported to 
   Excel.  The visualization looks like a unit 'patch chart'."
  [rootpath]
  (do (println [:building-patches (str rootpath "patches.htm")])
      (patches/locations->patches rootpath)))

(defn do-run
  "Given two paths to folders - a path to a marathon project 
   [from-path], and a path to post results to [target-path] - 
   computes the marathon history for the run, producing results 
   into the target path.  Default outputs will be derived from 
   the simulation history, including a compressed history."
  [from-path target-path]
  (do (a/spit-history! (a/marathon-stream from-path) target-path)
      (build-patches target-path)))

(def root "C:\\Users\\1143108763.C\\Documents\\srm\\cleaninput\\runs\\")
(def root "C:\\Users\\tspoon\\Documents\\srm\\tst\\notionalv2\\")

(def srm "srmbase.xlsx")
(def arf "arfbase.xlsx")
(defn strip [x] (first (clojure.string/split x #"\.")))

;;this is probably our primary api.
(defn run-cases
  "Given a sequence of paths to case files, processes each case in turn per
   do-run.  Assumes that the output directory is intended to be a subdirectory 
   identical to the name of the file being processed.  Thus, blah.xlsx would 
   have its output in ./blah/... "
  ([folder xs]
   (doseq [x xs]
     (println [:running x])
     (let [nm  (strip    x)
           in  (str folder x)
           out (str folder nm "\\")
           _   (io/hock (str out "timestamp.txt") (System/currentTimeMillis))]
       (do-run in out))))
  ([xs] (run-cases root xs)))

(defn examine-project
  "Given a path to a valid MARATHON project, will load the project into a 
   simulation context, and present a tree-based view of the initial state.
   Useful for exploring the simulation state data structure, and debugging."
  [path]
  (core/visualize-entities 
   (a/load-context path)))

(defn audit-project
  ""
  [path]
  )

(comment ;testing
  (do-run ep "C:\\Users\\1143108763.C\\srm\\newtest\\")
  (def h
    (a/load-context "C:\\Users\\1143108763.C\\Documents\\srm\\cleaninput\\runs\\srmbase.xlsx"))

;;This is just a helper to translate from craig's encoding for
;;srm policy positions.
(def translation
  {"MA, DA" "MA_DA_C1"
   "MD, DA" "MD_DA_C1"
   "MA, NDA" "MA_NDA_C1"
   "Ready"   "R_C2"
   "PB"      "PB_C3"
   "MP, NDA"  "MP_NDA_C3"
   "PT"       "PT_C4"
   "MP, DA"   "MP_DA_C1"})
  )

