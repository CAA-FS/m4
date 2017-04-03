;;Ns for convenient vnv operations for comparing
;;marathon runs and general scripting.
(ns marathon.vnv
  (:require [marathon
             [analysis :as a]
             [demo :refer :all]
             [run :as run]]
            [marathon.ces     [core :as core]]
            [spork.util       [io :as io] [table :as tbl]]
            [proc.example :as proc]))

(def threepath (hpath "\\Documents\\marv\\vnv\\m3v6\\"))
(def fourpath  (hpath "\\Documents\\marv\\vnv\\m4v6\\"))

(def taa-ints
  {:BCTS    ["BCTS"
             ["47112K000"
              "77202K000"
              "77202K100"
              "87312K000"]]
   :GSAB    ["GSAB"
             ["01225K000"]] 
   :CAB     ["CAB"
             ["01302K000"]]   
   :CSSB    ["CSSB"
             ["63426K000"]] 
   :QMSUPP  ["QM Supply Co"
             ["10473R100"]] 
                                        ;     :QMWATER ["QM Water Supp Co"
                                        ;              ["10460R000"]] 
   :QMPOL   ["QM Pol"
             ["10527RA00"
              "10527RC00"
              "10527RF00"
              "10527RG00"]] 
   :PATRIOT ["ADA Patriot"
             ["44635K000"]] ;in scope
                                        ;  :AVENGER ["ADA Avenger"
                                        ;            ["44615R600"]
   }) ;in scope

(defn src [& xs] (select-keys taa-ints (vec xs)))

(defn sample-charts [path & {:keys [interests]
                             :or   {interests taa-ints}}]
  (do (proc/run-sample! path :interests interests)
      (proc/do-charts-from path :interests interests)))

(comment 
(in-ns 'proc.util)
(defn read-tsv-dataset
  [path]
  (let [fields (atom nil)
        rs     (into []
                 (spork.util.table/tabdelimited->records
                  path))]
    (incanter.core/dataset (vec (keys (first rs))) rs)))
        
(in-ns 'marathon.vnv)
)

(in-ns 'marathon.ces.engine)
(defn partial-step
  "Primary state transition function for Marathon.  Threads the next day and
   an initial state through a series of transfer functions that address 
   high-level state transfers for supply, policy, demand, filling, and more. 
   Computes the resulting state - either the final state, or the initial state
   for the next step."
  ([day ctx]
   (->> ctx 
        (begin-day        day)  ;Trigger beginning-of-day logic and notifications.
        (manage-supply    day)  ;Update unit positions and policies.
        (manage-policies  day)  ;Apply policy changes, possibly affecting supply.
        (manage-demands   day)  ;Activate/DeActiveate demands, handle affected units.      
        ;(fill-demands     day)  ;Try to fill unfilled demands in priority order. 
        ;(manage-followons day)  ;Resets unused units from follow-on status. 
        ;(end-day day)           ;End of day logic and notifications.
        ))
  ([ctx] (partial-step (sim/get-time ctx) ctx)))
(in-ns 'marathon.vnv)

(defn sample-charts [path & {:keys [interests]
                             :or   {interests taa-ints}}]
  (do (proc/run-sample! path    :interests interests)
      (proc/do-charts-from path :interests interests)))

(defn re-run4 []
  (run/do-audited-run (str fourpath "testdata-v6.xlsx") fourpath)
  (proc/run-sample! fourpath :interests (src :BCTS)))

(defn re-run3 []
  (proc/run-sample! threepath :interests (src :BCTS)))

(defn re-run [] (do (re-run4) (re-run3)))

(defn compare-bcts []
  (proc/do-charts-from  threepath  :interests   (src :BCTS))
  (proc/do-charts-from  fourpath   :interests   (src :BCTS)))