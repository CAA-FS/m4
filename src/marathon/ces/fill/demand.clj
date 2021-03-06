;;Functions for coordinating the filling of demands.  Specifically, we deal 
;;with the querying and selection of demands.

(ns marathon.ces.fill.demand
  (:require [marathon.ces    [core :as core] 
                             [supply :as supply]
                             [demand :as dem]
                             [fill :as fill]]           
            [spork.sim       [simcontext :as sim]]
            [spork.ai.core   :refer [debug]]
            [spork.entitysystem.store :as store]))

;Since we allowed descriptions of categories to be more robust, we now abstract
;out the - potentially complex - category entirely.  This should allow us to 
;fill using 99% of the same logic.
;What we were doing using fill-followons and fill-demands is now done in 
;fill-category. In this case, we supply a more robust description of the 
;category of demand we're trying to fill. To restrict filling of demands that 
;can take advantage of existing followon supply, we add the followon-keys to the 
;category.  This ensures that find-eligible-demands will interpret the 
;[category followon-keys] to mean that only demands in a demand-group contained by 
;followon-keys will work.  Note: we should extend this to arbitrary tags, since 
;demandgroup is a hardcoded property of the demand data.  Not a big deal now, 
;and easy to extend later.

;For each independent set of prioritized demands (remember, we partition based 
;on substitution/SRC keys) we can use this for both the original fill-followons 
;and the fill-normal demands. The difference is the stop-early? parameter.  If 
;it's true, the fill will be equivalent to the original normal hierarchical 
;demand fill. If stop-early? is falsey, then we scan the entire set of demands, 
;trying to fill from a set of supply.

;1.  The result of trying to fill a demand should be a map with context
;    we can be more flexible here, maybe pass info on the success of the fill.
;2.  Incorporate fill results.
;3.  If we fail to fill a demand, we have no feasible supply, thus we leave it 
;    on the queue, and stop filling. Note, the demand is still on the queue, we
                                        ;    only "tried" to fill it. No state changed .

;;Potential garbage leak here.
(def lastfill (atom nil))


;;this is [cat|src|rule groups|buckets], or ["somesrc" #{"SomeDemandGroup"}]
;;the simple translation is, fill the demands useing these buckets.
(defn fill-category [demandstore category ctx & {:keys [stop-early? pending] 
                                                 :or   {stop-early? true}}]
  ;We use our UnfilledQ to quickly find unfilled demands. 
  (loop [pending   (or pending
                       (dem/find-eligible-demands demandstore category ctx))
         ctx       (dem/trying-to-fill! demandstore category ctx)]
    (if-not (seq pending) ctx ;no demands to fill!      
      (let [
            demandstore (core/get-demandstore ctx)
            demand      (second (first pending)) 
            demandname  (:name demand)           ;try to fill the topmost demand
            ctx         (dem/request-fill! demandstore category demand ctx)
            ;_           (reset! lastfill [demand ctx]) ;;potential garbage point.
            [fill-status fill-ctx]  (fill/satisfy-demand demand category ctx);1)
            can-fill?   (= fill-status :filled)
            _           (debug [:fill-status fill-status])
            next-ctx    (if (= fill-status :unfilled) fill-ctx 
                          (->> fill-ctx 
                               (dem/demand-fill-changed! demandstore demand) ;2)
                               (core/merge-entity               ;UGLY 
                                 {:DemandStore 
                                  (dem/register-change  (core/get-demandstore fill-ctx) demandname)})))
            newstore    (core/get-demandstore next-ctx)
            _ (debug [:demand    (:name demand)
                      :pre-fill  (keys (:units-assigned demand))
                      :post-fill (keys (:units-assigned (dem/get-demand newstore demandname)))
                        ])                        
             ]
        (if (and stop-early? (not can-fill?)) ;stop trying if we're told to...
          next-ctx                                                           ;3)
          ;otherwise, continue filling!
          (recur
           (rest  pending)     ;(dem/pop-priority-map      pending) ;advance to the next unfilled demand
           (->> (dem/sourced-demand!  newstore demand next-ctx);notification
                ((fn [ctx] (debug [:should-be-popping demandname]) ctx))
                (dem/update-fill      newstore demandname)  ;update unfilledQ.
                (dem/can-fill-demand! newstore demandname))))))));notification


;;Tom Hack 26 May 2016
;;We're going to wire in the fact that SRM demands are special, and that
;;any other category is okay to fill...
;;fill/satisfy-demand will make accomodations for special categories,
;;namely SRM...
#_(defn fill-category [demandstore category ctx & {:keys [stop-early? pending] 
                                                 :or   {stop-early? true}}]
  ;We use our UnfilledQ to quickly find unfilled demands. 
  (loop [pending   (or pending
                       (dem/find-eligible-demands demandstore category ctx))
         ctx       (dem/trying-to-fill! demandstore category ctx)]
    (if (empty? pending) ctx ;no demands to fill!      
      (let [
            demandstore (core/get-demandstore ctx)
            demand      (second (first pending)) 
            demandname  (:name demand)           ;try to fill the topmost demand
            ctx         (dem/request-fill! demandstore category demand ctx)
            _           (reset! lastfill [demand ctx])
            [fill-status fill-ctx]  (fill/satisfy-demand demand category ctx);1)
            ;_           (println [fill-status demandname])
            can-fill?   (= fill-status :filled)
            _           (debug [:fill-status fill-status])
            next-ctx    (if (= fill-status :unfilled) fill-ctx 
                          (->> fill-ctx 
                               (dem/demand-fill-changed! demandstore demand) ;2)
                               (core/merge-entity               ;UGLY 
                                 {:DemandStore 
                                  (dem/register-change  (core/get-demandstore fill-ctx) demandname)})))
            newstore    (core/get-demandstore next-ctx)
            _ (debug [:demand    (:name demand)
                      :pre-fill  (keys (:units-assigned demand))
                      :post-fill (keys (:units-assigned (dem/get-demand newstore demandname)))
                        ])                        
             ]
        (if (and stop-early? (not can-fill?)) ;stop trying if we're told to...
          next-ctx                                                           ;3)
          ;otherwise, continue filling!
          (recur
           (rest  pending)     ;(dem/pop-priority-map      pending) ;advance to the next unfilled demand
           (->> (dem/sourced-demand!  newstore demand next-ctx);notification
                ((fn [ctx] (debug [:should-be-popping demandname]) ctx))
                (dem/update-fill      newstore demandname)  ;update unfilledQ.
                (dem/can-fill-demand! newstore demandname))))))));notification

;NOTE...since categories are independent, we could use a parallel reducer here..
;filling all unfilled demands can be phrased in terms of fill-category...

;;#High Level Demand Fill

;;Note: in this connotation, categories are actually
;;srcs....

;;Higher-order function for filling demands.
;;note: dem/unfilled-categories returns a sequence of
;;"some src", in this context, the category of
;;demand is a demand for an SRC.
(defn fill-demands-with [f ctx]
  (reduce (fn [acc c]
            (do (debug [:filling (type acc) c])
                (f (core/get-demandstore acc) c acc)))
      ctx (dem/unfilled-categories (core/get-demandstore ctx))))

;;Implements the default hierarchal, stop-early fill scheme.
;;NOTE: With the advent of compo-specific and other constraints (e.g.
;;modernization limits for mod demands), we introduced a subtle
;;bug by violating the long-standing assumptions of our hierarchical
;;fill process...

;;Assuming full access to supply, we could prove - for a given
;;category of demand such as Rotational, SRC A, that if we couldn't
;;fill with "that input" then every lower priority demand wouldn't
;;get filled either.  This works great so long as there are no
;;constraints on supply "between" demands.  If a higher priority
;;demand is filling with a relatively tight constraint, and can't
;;be filled, this does not preclude the possibility that a "lower"
;;priority demand of a different category with relaxed constraints
;;cannot be filled.  This leads to us stopping trying to fill demand
;;due to effectively artificial constraints on lower priority demands.
;;This problem first manifested visibly in the modernization case,
;;where high-priority mod demands - with compo-specific constraints -
;;were successful in modernizing the supply.  Eventually, once
;;no non-modern supply existed and the demand couldn't be filled,
;;the "normal" but lower priority demands  were effectively truncated.
;;This created the appearance of missing gobs of demands from an
;;instant forward, despite seemingly having plenty of supply (and
;;modernized too!).

;;Solutions:

;;1 - do not optimize the fill process, and set the :stop-early? default to
;;    false. This will incur costs during filling, but they may be negligible in
;;    the grand scheme. Need profiling to determine.

;;2 - Attempt to fill as in 1, except maintain a map of categories that have
;;    been unsuccesfully fillled. If we fail to fill a category, we add it to
;;    the empties and continue to scan through the demands. If we run into a
;;    lower-priority demand of the same category, we can prune it (by ignoring
;;    it) since we've proven it unfillable based on the objective hierarchy.

;;3 - Fill as in 2, but maintain the empties map between fills (absent
;;    changes in supply.  This an additional optimization, but requires
;;    managing state and book-keeping that could be error prone.

;;for now (5 August 2018), we go with option 1 to expidite results.

(defn fill-hierarchically [ctx]
  (fill-demands-with
   (fn exhaustive-fill [store category ctx]
     (fill-category store category ctx :stop-early? false)) ctx))

;;Implements the try-to-fill-all-demands, using only follow-on-supply scheme.
;;get-followon-keys returns a set of "buckets" in the supply that correspond
;;to units tagged as follown-eligible  supply for demands witha corresponding
;;followon-key, where the default is the demandgroup.  We want to pass this
;;information on as a set of followon-tags to use when screening supply
;;for a particular demand.  Since each demand in the category has a
;;demandgroup, it stands to reason that we use the groups to filter
;;on only supply with compatible demand.
(defn fill-followons [ctx]
  (if-let [groups (core/get-followon-keys ctx)]   
    (fill-demands-with
     (fn [store category ctx] 
           (fill-category store [category groups] ctx :stop-early? false)) ctx)
  ctx))

;; (defn fill-srm [ctx]
;;   (fill-demands-with
;;    (fn [store category ctx] 
;;      (fill-category store [category #{:SRM}] ctx)) ctx)
;;   ctx)

;; ;;This is a really quick hack to implement demand fill for SRM.
;; ;;I don't care about tieing in to the original categorization stuff,
;; ;;since right now we have enough differences between SRM and the old
;; ;;to merit something utterly different.
;; ;;We piggy-back off of the SRM demands 
;; (defn fill-others [ctx]
;;   (let [srm-demands
;;         (->> (store/gete ctx :DemandStore :activedemands)
;;              (filter #(srm-demand? ctx   %))
;;              (map #(store/get-entity ctx %))
;;              (group-by :SRC))        
;;         sorted-groups   (for [[SRC ds] srm-demands]
;;                           [SRC (sort-by :Priority ds)])]
;;         (reduce (fn [acc [SRC pending]]
;;                   (fill-category (core/get-demandstore acc)
;;                                  [SRC :SRM]
;;                                  acc
;;                                  :pending pending))
;;                  ctx sorted-groups)))
        
    

;Note -> we're just passing around a big fat map, we'll use destructuring in the 
;signatures to pull the args out from it...the signature of each func is 
;state->state

;;__fill-demands__ is a high-level hook for the engine API that takes the 
;;place of what used to be a demand-manager object, with an method of the same 
;;name.  It exists here, due to the need to resolve cyclic dependencies, but 
;;the organization is subpar.  We probably need to move it elsewhere, ideally 
;;in a high-level, centralized protocol for "marathon".

;;TOM Note 20 May 2013 -> the t arg may not be necessary, since we can derive it
;;from context.  
(defn fill-demands
  "Default fill order for demands.  Performs a prioritized fill of demands, 
   hierarchically filling demands using followon supply, then using the rest of 
   the supply."
  [t ctx]
  (->> ctx    
    (fill-followons)
    (supply/manage-followons t)
    (fill-hierarchically) ;;goes by categories.
    ))

;;Well, we can go the dbag route on this....
;;Fill-demands could be executed much more poorly than it is.
;;Specifically, every time we have a change in supply, we try
;;to fill demand brute-force like.
;;Go through all the deployers, find 
