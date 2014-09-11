;;Rotational policy data definitions.  Both atomic and composite policies are 
;;represented here.
(ns marathon.policy.policydata
  (:use [spork.util.record :only [defrecord+]])
  (:require [marathon.data [protocols :as core]]
            [spork.cljgraph [core :as graph]]))

;__TODO__ Extend core/IRotationPolicy protocol to policy and
;policycomposite..

;a structure for unit entity policies.
(defrecord+ policy [[name "BlankPolicy"]
                    [cyclelength :inf]
                    [mindwell 0]
                    [maxdwell :inf]
                    [maxbog :inf]
                    [maxMOB :inf]
                    [recovery  90]
                    [startdeployable 0]
                    [stopdeployable :inf]
                    [positiongraph  graph/empty-graph]
                    [startstate :spawn]
                    [endstate :spawn]
                    [overlap 45]]
  core/IRotationPolicy 
  (atomic-name         [p] name)
  (bog-budget          [p] maxbog)
  (get-active-policy   [p] p)
  (get-policy          [p period] p)
  (policy-name         [p] name)
  (next-position       [p position] (first (graph/sinks positiongraph)))
  (overlap             [p] overlap) 
  (get-position-graph  [p] positiongraph)  
  (previous-position    [p position] (first (graph/sources positiongraph)))
  (start-deployable     [p] startdeployable)
  (stop-deployable      [p] stopdeployable)
  (start-state          [p] startstate)
  (transfer-time    [p start-position end-position] (graph/arc-weight positiongraph start-position end-position))

  (cycle-length     [p] cyclelength)
  (deployable?      [p position] (core/deployable-state? (graph/get-node positiongraph position)))
  (end-state        [p] endstate)
  (get-cycle-time   [p position] (if (= position startstate) 0 
                                     (-> (graph/depth-first-search positiongraph startstate position)
                                         (get :distance)
                                         (get position))))                                          
  (get-policy-type  [p] :atomic)
  (get-position     [p cycletime] (loop [pos startstate
                                         t   0]
                                    (if-let [nxt (first (graph/sinks positiongraph pos))]
                                      (let [tnxt (+ t (graph/arc-weight pos nxt))]
                                        (if (>= tnxt cycletime) acc
                                            (recur nxt tnxt)))
                                      (throw (Excption. "Cycletime exceeds policy!")))))                                    
  (get-state        [p position]   (graph/get-node positiongraph position))
  (deployable?      [p cycletime]  (core/deployable? (.get-position p cycletime)))
  (dwell?           [p position]   (core/dwell?  (graph/get-node positiongraph position)))
  (max-bog          [p]            maxbog)
  (max-dwell        [p]            maxdwell)
  (max-mob          [p]            maxmob)
  (min-dwell        [p]            mindwell)
  (get-locations    [p]            (graph/get-node-labels positiongraph))
  core/IAlterablePolicy
  (set-deployable       [p tstart tfinal] (-> p 
                                              (core/insert-modifier tstart :name :deployable)
                                              (core/insert-modifier tfinal  :name :non-deployable)))
  (set-deployable-start [p cycletime]  (core/insert-modifier p cycletime :name :deployable))
  (set-deployable-stop  [p cycletime]  (core/insert-modifier p cycletime :name :non-deployable))
  (set-position-graph   [p g] (assoc p :positiongraph g)) 
  (add-position         [p name state] (assoc p :positiongraph (graph/conj-node positiongraph name state)))
  (add-route            [p start destination transfer-time] (assoc p :positiongraph (graph/conj-arc start destination transfer-time))))

(def empty-policy (make-policy))

;;Boiler plate reduction stuff.  This lets us provide shell
;;implementations in the composite policy, and expands out into the
;;actual error being thrown.  Should not be necessary, but hey, be defensive.
(defmacro atomic-mod-err []
  `(throw (Exception. "Atomic policies may not be modified inside of composite policies!")))

;policies defined by more than one atomic policy.
;;Debate turning policies away from a map....so we can support more
;;than one policy composition type based off of data structure used to 
;;contain the policies.
(defrecord+ policymap [name
                       ^marathon.data.protocols.IRotationPolicy activepolicy 
                       activeperiod
                       [policies {}]]
  core/IRotationPolicy 
  (atomic-name         [p] (.atomic-name activepolicy))
  (bog-budget          [p] (.bog-budget activepolicy))
  (get-active-policy   [p] activepolicy)
  (get-policy          [p period] (get policies period))
  (policy-name         [p] name)
  (next-position       [p position] (.next-position activepolicy position))
  (overlap             [p] (.overlap activepolicy))
  (get-position-graph  [p] (.get-position-graph activepolicy))
  (previous-position    [p position] (.previous-position activepolicy position))
  (start-deployable     [p] (.start-deployable activepolicy))
  (stop-deployable      [p] (.stop-deployable activepolicy))
  (start-state          [p] (.start-state activepolicy))
  (transfer-time    [p start-position end-position] (.transfer-time activepolicy start-position end-position))
  (cycle-length     [p] (.cycle-length activepolicy))
  (deployable?      [p position] (.deployable? activepolicy position))
  (end-state        [p] (.end-state active-policy))
  (get-cycle-time   [p position] (.get-cycle-time activepolicy position))     
  (get-policy-type  [p] :composite)
  (get-position     [p cycletime] (.get-position activepolicy))                                    
  (get-state        [p position]  (.get-state activepolicy))
  (deployable?      [p cycletime]  (.deployable? activepolicy cycletime))
  (dwell?           [p position]   (.dwell?  activepolicy position))
  (max-bog          [p]            (.max-bog activepolicy))
  (max-dwell        [p]            (.max-dwell activepolicy))
  (max-mob          [p]            (.max-mov activepolicy))
  (min-dwell        [p]            (.min-dwell activepolicy))
  (get-locations    [p]            (.get-locations activepolicy))
  core/IPolicyContainer
  (add-policy       [p period policy]   (policymap. activepolicy activeperiod (assoc policies period policy)))
  (add-policy       [p keyval] (if (coll? keyval) 
                                 (let [[k v] keyval] (add-policy p k v))
                                 (throw (Exception. "Expected a [period policy] pair for arity 2 add-policy on a policymap")))))

(def empty-policymap (make-policymap))


;;Defines a policy that scripts a sequence of policies, starting from
;;a root policy.  We might want to have a policy offset...depends on
;;how I'm using this in the code.  I think unit behavior was
;;interpreting policy, keeping track of its current state in the policy.
(defrecord+ policyseq [name
                       ^marathon.data.protocols.IRotationPolicy rootpolicy   
                       [idx 0]
                       [policies []]]
  core/IRotationPolicy 
  (atomic-name         [p] (.atomic-name rootpolicy))
  (bog-budget          [p] (.bog-budget rootpolicy))
  (get-active-policy   [p] rootpolicy)
  (get-policy          [p period] (get policies period))
  (policy-name         [p] name)
  (next-position       [p position] (.next-position rootpolicy position))
  (overlap             [p] (.overlap rootpolicy))
  (get-position-graph  [p] (.get-position-graph rootpolicy))
  (previous-position   [p position] (.previous-position rootpolicy position))
  (start-deployable    [p] (.start-deployable rootpolicy))
  (stop-deployable     [p] (.stop-deployable rootpolicy))
  (start-state         [p] (.start-state rootpolicy))
  (transfer-time       [p start-position end-position] (.transfer-time rootpolicy start-position end-position))
  (cycle-length        [p] (.cycle-length rootpolicy))
  (deployable?         [p position] (.deployable? rootpolicy position))
  (end-state           [p] (.end-state active-policy))
  (get-cycle-time      [p position] (.get-cycle-time rootpolicy position))     
  (get-policy-type     [p] :composite)
  (get-position        [p cycletime]  (.get-position rootpolicy))                                    
  (get-state           [p position]   (.get-state rootpolicy))
  (deployable?         [p cycletime]  (.deployable? rootpolicy cycletime))
  (dwell?              [p position]   (.dwell?  rootpolicy position))
  (max-bog             [p]            (.max-bog rootpolicy))
  (max-dwell           [p]            (.max-dwell rootpolicy))
  (max-mob             [p]            (.max-mov rootpolicy))
  (min-dwell           [p]            (.min-dwell rootpolicy))
  (get-locations       [p]            (.get-locations rootpolicy))
  core/IPolicyContainer
  (add-policy       [p period policy]   (add-policy p policy))
  (add-policy       [p policy]  (assert (satisfies? core/IRotationPolicy policy) 
                                        "expected a rotation policy for add-policy arity 2 on a policyseq")
                                (policyseq. name (or rootpolicy policy) idx  (conj policies policy))))

(def empty-policyseq (make-policyseq))
