;;Deploying entities requires operating on multiple systems.  This namespace
;;builds on the services provided by supply, demand, and policy, to coordinate 
;;changes to the simulation context necessary to physically allocate supply to 
;;demand - or to execute deployments.  
;;Primarily used by __marathon.sim.fill__ .
(ns marathon.ces.deployment
  (:require [marathon.demand [demanddata :as d]]
            [marathon.supply [unitdata :as udata]]
            [marathon.ces    [core :as core] [demand :as dem] 
                             [policy :as policy] [supply :as supply] 
                             [unit :as u]]
            [marathon.data   [protocols :as protocols]]
            [spork.entitysystem.store :as store]
            [spork.sim       [simcontext :as sim]]
            [spork.util      [tags :as tag]]))

;;#Functions for Deploying Supply
;; (defn get-max-bog [unit policystore]
;;   (let [bog-remaining (udata/unit-bog-budget unit)
;;         p             (:policy unit)
;;         p             (if (protocols/policy? p) p
;;                           (policy/get-policy    (-> unit :policy :name) policystore))
;;         ]
;;     (- bog-remaining  (protocols/overlap p))))

;;Demand effect categories map values of associated
;;with the :category of the demand to a set of
;;interpreted effects, if any.  These provide a
;;flexible form of location-based policy that allows
;;us to express overrides of default policy-based
;;behavior.  One simple idea is to override the
;;behavioral defaults, i.e. the statemap,
;;for the unit's state.  This provides an
;;easy way to rewire common states.
(def demand-effect-categories
  {"NonBOG" {:wait-time   999999
             :wait-state  :waiting #_#{:waiting :non-bogging}}})


(defn location-based-policy? [d]
  (and (:StartState d) (:EndState d)))

(defn wait-based-policy? [d]
  (when-let [fx (demand-effect-categories (:category d))]
    (assoc fx :demand d)))

;;These seem like lower level concerns.....
;;Can we push this down to the unit entity behavior?
;;Let that hold more of the complexity?  The unit can be responsible
;;for the bulk of the implementation detail of what a
;;deployment entails...
;;Since units have access to the simulation context, like
;;every other system, they could apply all the updating necessary.
;;Now, we move the updates into a behavior function, where
;;we can more efficiently handle the state updates...
;;For instance, we can perform bulk updates with the same
;;or a simular behavior context.....this is more appealing.
(defn deploy!  [followon? unit demand t ctx]
  (let [supply (core/get-supplystore ctx)
        newlocation (:name demand)
        effects (wait-based-policy? demand)
        ]
    (cond (location-based-policy? demand)  (u/location-based-deployment unit demand ctx) ;;allow location to override policy.
          effects               (u/pseudo-deploy unit effects t ctx) ;;nonbog and the like.
          followon?    (let [newctx  (supply/record-followon supply unit newlocation ctx)
                             newunit (store/get-entity newctx (:name unit))] ;;we've updated the unit at this point...               
                         (u/re-deploy-unit  newunit  demand t newctx))
          :else 
          (u/deploy-unit unit demand  t ctx))))

(defn check-first-deployer!   [store unitname ctx]
  (let [unit (supply/get-unit store unitname)]  
    (if (supply/first-deployment? unit store)
      (->> (core/set-supplystore ctx (supply/tag-as-deployed unit store))
           (supply/first-deployment! store unit)
           (supply/adjust-max-utilization! store unit)))))

(def last-deploy (atom nil))
(defn non-bog? [d] (= (:category d) "NonBOG"))
;;TODO# fix bog arg here, we may not need it.  Also drop the followon?
;;arg, at least make it non-variadic..
(defn deploy-unit
  "Deploys a unit entity, registered in supply, at time t, to demand.  The 
   expected length of stay, bog, will determine when the next update is 
   scheduled for the unit.  Propogates logging information about the context 
   of the deployment."
  ([ctx unit t demand   followon?]
   (if (not  (u/valid-deployer? unit nil (non-bog? demand) (:policy unit)))
     (do (reset! last-deploy [unit ctx])
         (throw (Exception. (str [:unit (:name unit) :invalid-deployer "Must have bogbudget > 0, 
     cycletime in deployable window, or be eligible or a followon  deployment"]))))
      
    (core/with-simstate [[supplystore parameters policystore demandstore fillstore] ctx]
      (let [fillcount     (count (:fills fillstore))         
            unitname      (:name unit)
            ;;This may be a little problematic.  We're pre-loading the move.
            ;;It may be more idiomatic to delegate the move to the entity behavior system.
            from-location (:locationname    unit) ;may be extraneous
            from-position (:positionpolicy unit);
            to-location   (:name demand)
            ;;Some demands have a special position associated with them...
            ;;removed external modification of positionpolicy, since this
            ;;screws with our behavior implementation.  delegate changes to
            ;;behavior.
            ;to-position   :deployed
            unit-delta    {;:position-policy to-position
                           :dwell-time-when-deployed (udata/get-dwell unit)}
            unit          (merge unit ;MOVE THIS TO A SEPARATE FUNCTION? 
                                 unit-delta)
            ;;TODO - rip out all stuff relate to fencing....no longer necessary.
            supplystore   (assoc supplystore :tags  (supply/drop-fence (:tags supplystore)
                                                                       unitname))
            ]
        ;;Ideally, we don't store the entire unit under units-assigned, only the name.
        (->> ;;modified to just store unitname.
             (store/updatee ctx to-location :units-assigned assoc unitname unitname) ;need to update this in ctx..  ;;estore version.       
             (sim/merge-entity {unitname     unit-delta   ;;REFACTOR - operate on unit directly...
                                ;;TODO - rip out all stuff relate to fencing....no longer necessary.
                                :SupplyStore {:tags (:tags supplystore)} ;(supply/add-unit supplystore unit)
                                })                       
             (deploy! followon?  unit demand t)  ;;apply state changes.
             )))))
  ([ctx unit t demand]
    (deploy-unit  ctx unit t demand (core/followon? unit))))

;;another option here..is to use the system approach.  We look for entities that have a
;;pending deployment component, and process them.  A pending deployment component could
;;include the name of the target demand, so we can lookup information we need for it.

(defn deploy-units [ctx us d]
  (let [t      (core/get-time ctx)
        period (:name (policy/get-active-period (core/get-policystore ctx)))
        ;cnt   (atom (store/gete ctx :SupplyStore :deployment-count 0))
        ]
    ;(->
     (reduce (fn [acc u]
                   (let [res (deploy-unit acc u t d ;period
                                          ;@cnt
                                          )]
                         ;_ (swap! cnt unchecked-inc)]
                     res))
                 ctx us)
         ;(store/assoce :SupplyStore :deployment-count @cnt)
     ))
;)
(comment   
           
           
           (u/deploy-unit unit t (supply/get-next-deploymentid supplystore))
           (check-first-deployer! supplystore unitname) ;THIS MAY BE OBVIATED.
           (supply/update-deployability unit false false) 
           (supply/log-deployment! t from-location demand unit fillcount filldata 
                                   deploydate (policy/find-period t policystore))
           (supply/supply-update! supplystore unit nil))
