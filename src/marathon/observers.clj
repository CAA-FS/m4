;;Collection of observation functions and
;;samplers that capture events...
;;From the legacy implementation, we
;;have these as logging deployments and
;;things like that.
;;It may make sense to go back to the old style of
;;defining global events, at least at the
;;Marathon level of detail.  Perhaps they can
;;be shadowed in a global event namespace.
(ns marathon.observers
  (:require [marathon.data      [protocols :as generic]]
            [marathon.ces       [core :as core]]
            [spork.entitysystem [store :as store]]
            [spork.sim.pure     [network :as simnet]]
            [spork.sim          [data :as sim]]))

;;utility schlock
;;===============
;;a cell for holding ephemeral values.
;;We tend to only care about the initial state and the
;;final state of an identity over time.  For this reason,
;;we can use cells to capture the end points, and provide
;;a simple structure that can be updated pretty quickly with
;;new right-ends.
(defrecord Cell [init current])
(defn ->cell
  ([init] (Cell.  init  init))
  ([init current] (Cell. init current)))
(defn push-cell [^Cell cl v] (Cell. (.init cl) v))


;;identical to proc....copied from there...
;;deployment records
(def deprecordschema
  {:ID	                :int
   :DeploymentID	:int
   :Location	        :text
   :Demand	        :text
   :DwellBeforeDeploy   :int
   :BogBudget	        :int
   :CycleTime	        :int
   :DeployInterval	:int
   :DeployDate	        :text
   :FillType	        :text
   :FillCount	        :int
   :UnitType	        :text
   :DemandType	        :text
   :DemandGroup	        :text
   :Unit	        :text
   :Policy	        :text
   :AtomicPolicy	:text
   :Component	        :text
   :Period	        :text
   :FillPath	        :text
   :PathLength	        :int
   :FollowOn	        :boolean
   :FollowOnCount	:int
   :DeploymentCount	:int
   :Category	         :text
   :DwellYearsBeforeDeploy   :text
   :OITitle	             :text})


;;location changes...I don't think this matters for us anymore,
;;although if we can synthesize it from history, that's great.
;;LEGACY
(def locschema {:T :int 
                :EntityFrom :text
                :EntityTo :text
                :EventName :text
                :Msg :text})

;;DemandRecords  ;;DEPRECATED , new SRM stuff trumps this.
(def drecordschema {"Type"         :text
                    "Enabled"      :boolean
                    "Priority"	   :int
                    "Quantity"	   :int
                    "DemandIndex"  :int	
                    "StartDay"	   :int
                    "Duration"	   :int
                    "Overlap"	   :int
                    "SRC"          :text
                    "SourceFirst"  :text	
                    "DemandGroup"  :text	
                    "Vignette"	   :text
                    "Operation"	   :text
                    "Category" 	   :text
                    "Title 10_32"  :text	
                    "OITitle"      :text})

;;DemandTrends...
(def demand-trend-schema
  {:t  	        :int
   :Quarter	        :int
   :SRC	        :text
   :TotalRequired	:int
   :TotalFilled	:int
   :Overlapping	:int
   :Deployed	        :int
   :DemandName	:text
   :Vignette	        :text
   :DemandGroup	:text
   :ACFilled	        :int
   :RCFilled	        :int
   :NGFilled	        :int
   :GhostFilled	:int
   :OtherFilled	:int})

;;CycleRecords...
(def cycleschema 
  {"tstart"	      :int
   "tfinal"	      :int
   "Deployments"      :int
   "BOG"	      :int
   "MOB"	      :int
   "Dwell"            :int	
   "Duration"         :int 	
   "BDR"	      :float
   "BDR 1:X"	      :float
   "BOGExpected"      :int
   "DwellExpected"    :int 
   "DurationExpected" :int})

;;FillRecords, which are produced....

;Tom gave craig this schema on 7/8/15:
 (def fillrecord {:Unit        :text
                  :category    :text
                  :DemandGroup :text
                  :SRC         :text
                  :FillType    :text
                  :FollowOn    :boolean
                  :name :text
                  :Component :text
                  :operation :text
                  :start :int
                  :DeploymentID :int
                  :duration :int
                  :dwell-plot? :boolean
                  :DwellYearsBeforeDeploy :float
                  :DeployDate :text
                  :FollowOnCount :int
                  :AtomicPolicy :text
                  :Category :text
                  :DeployInterval :int
                  :fill-type :text
                  :FillPath :text
                  :Period :text
                  :unitid :int
                  :deltat :int
                  :Demand :text
                  :PathLength :int
                  :OITitle :text
                  :BogBudget :int
                  :CycleTime :int
                  :DeploymentCount :int
                  :DemandType :text
                  :quantity :int
                  :end :int
                  :FillCount :int
                  :Location :text
                  :location :text
                  :compo :text
                  :DwellBeforeDeploy :int
                  :Policy :text
                  :sampled :boolean
                  })

(def deprecordchema
  {:Unit         :text
   :DemandGroup  :text
   :FillType     :text
   :FollowOn     :text
   :UnitType     :text
   :Component    :text
   :DeploymentID :text
   :DeployDate    :text
   :FollowOnCount :text
   :AtomicPolicy  :text
   :DeployInterval :text
   :FillPath      :text
   :Period        :text
   :Demand        :text
   :PathLength    :int
   :BogBudget     :int
   :CycleTime     :int
   :DeploymentCount :int
   :DemandType      :text
   :FillCount       :int
   :Location        :text
   :DwellBeforeDeploy :int
   :Policy            :text})

;;should match the deprecord schema.
(defn new-deployment
  ([{:keys [unit fromloc demand fill fillcount t deploydate period]}]
    (new-deployment unit fromloc demand fill fillcount t deploydate period))
  ([unitfrom locationfrom demandto fill fillcount t deploydate period]
   (let [{:keys [bogbudget followons deployments dwell]} (:CurrentCycle unitfrom)
         pol (:policy unitfrom)
         ;_ (println [:deployment! (:name unitfrom) t])
         ]
     {:Unit          (:name unitfrom)
      :DemandGroup   (or (:demandgroup demandto) "UnGrouped")
      :FillType      (:quality  fill)
      :FollowOn      (:followon fill)
      :UnitType      (:src unitfrom)
      :Component     (:component unitfrom)
      :DeploymentID  (:deploymentindex unitfrom)
      :DeployDate    deploydate
      :FollowOnCount followons
      :AtomicPolicy  (generic/atomic-name pol)
      :DeployInterval t
      :FillPath      (:fillpath fill)
      :Period        period
      :Demand        (:name       demandto)
      :PathLength    (:pathlength fill)
      :BogBudget     bogbudget
      :CycleTime     (:cycletime unitfrom)
      :DeploymentCount deployments
      :DemandType    (:primaryunit demandto)
      :FillCount     fillcount
      :Location      locationfrom
      :DwellBeforeDeploy dwell
      :Policy        (generic/policy-name pol)}))
  )




;;handlers are functions of the form
;;(ctx  -> edata -> name -> ctx)

;;Deployments
;;===========
;;Recording deployments with benevolent side-effects.
;;You know, we could just attach a deployment component
;;to each entity that had a deployment on said day....and
;;then wipe the deployments afterward.  That's an idea...
;;handle "events" by attaching components.  Keeps everything
;;in data.  The other approach is maintaing ephemeral
;;logs like these...

;;I like the approach of tagging entities with eventful
;;data...
;;Like, if we fire an event at an entity, we attach
;;the event to the entity.  That way, if we
;;look at the entity's history at said point in time,
;;it'll have a an event component with all of the
;;events for said day, i.e. the instantaneous events.

;;right now, on update, we're just firing off this
;;event...so we get event-based processing with
;;the pure functional approach...


;;maybe there's a deployments entity with unit components...
;;we conj onto it...
;;I like the data-driven approach a bit better...
;;inside of log-deployment, we can conj into the deployments
;;component for the unit.

;;deployments push records onto a transient vector inside of
;;an atom at :deployment-watch/:new-deployments 
(defn record-deployment [ctx edata _]
  (->>  (:data edata)
        (new-deployment)
        (core/conj-ephemeral ctx :deployment-watch :new-deployments)))

(defn commit-deployments! [ctx edata  _]
  (if-let [deployments (core/some-ephemeral ctx :deployment-watch :new-deployments)]
      (-> ctx
          (store/assoce :deployment-watch :deployments  (persistent! @deployments))
          (store/drop-domain :new-deployments)
          ;(core/reset-ephemeral :deployment-watch :new-deployments (transient []))
          )
    ctx))

(defn drop-deployments! [ctx _ _]
  (store/dissoce ctx :deployment-watch :deployments))

;;we seem to have a pattern emerging for event handlers...
;;we'd like a function that dispatches based on the type of data.
;;The event handlers maintain ephemeral data, and are responsible
;;for cleaning up or not between eventful days.
;;So, one pattern that's emerging is that we commit 

;;maintaing ephemeral storage is nice...
;;we have flexibility over where the ephems live though...

;;if we have known policy trajectories...then we can alter position, velocity, etc.
;;we'd like to ensure that entities who move are recorded.
;;In our case, it's enough to tag a movement component onto the entities.
;;We make sure to drop the movement domain when we begin a new day..
;;can we establish a form of one-way binding here?
;;so, (bind-> name :position-delta :PositionUnit)
;;    (bind-> name :movement-delta :unitMoved)

;;if we recognize :PositionUnit as an event....
;;then we can map to it and stuff.
;;Specifically, if we use channels and pub/sub, we already have all that
;;junk setup.

;;Maybe we abstract this later...

;;We attach a position-delta to the entity directly...
;;Note, we can drop position-delta and movement-delta
;;from the domains on cleanup...

;;So....a general system could be...
;;(process-deltas position-delta movement-delta) =>
;;  for each domain,
;;    for each entity, broadcast its initial-value and its current-value.
;;    remove the domain from the store.

;;So, process-deltas could either directly handle the changes, or fire off
;;notifications.


;;position and location deltas are associated with specific entities, computed
;;daily.


;;Notice these two handlers are identical:
;;they update a stored value in response to an event.

;;The problem here is that we have one concept of event
;;handling (intermediate functions that compute new context
;;from old), and another concept (pulling things off channels
;;and doing stuff with them).

;;so, these are effectively properties...
;;We'll see if it makes sense to use defproperty or binding
;;as defined above, maybe.  For now, we only have two...

;;Let's just go with the legacy implementation and move on from there...
;;It'd be nice to say..
;;  (on-event entity event handler)....

;;Entity Telemetry (position location state)
;;=========================================

(def telemetry-components #{:position-delta
                            :location-delta
                            :state-delta})
;;(defproperty position)
(defn position-handler [ctx edata _]
  (let [[name frompos topos] (:data edata)
        [ctx storage]        (core/get-ephemeral ctx name :position-delta nil)]
    (do (if @storage
          (swap! storage  #(push-cell % topos))            
          (reset! storage (->cell frompos topos)))
        ctx)))

;;(defproperty movement)
(defn movement-handler [ctx edata _] 
  (let [[name fromloc toloc] (:data edata)
        [ctx storage]        (core/get-ephemeral ctx  name :location-delta nil)]
    (do (if @storage
          (swap! storage  #(push-cell % toloc))            
          (reset! storage (->cell fromloc toloc)))
        ctx)))

;;(defproperty state)
(defn state-handler [ctx edata _] 
  (let [[name fromloc toloc] (:data edata)
        [ctx storage]        (core/get-ephemeral ctx  name :state-delta nil)]
    (do (if @storage
          (swap! storage  #(push-cell % toloc))            
          (reset! storage (->cell fromloc toloc)))
        ctx)))

;;If the ephemeral value is a transient (for example new-deployments),
;;we'd like to persist it, otherwise we return the val.
(defn try-freeze! [coll]
  (if (instance? clojure.lang.ITransientCollection coll)
    (persistent! coll)
    coll))

;;update all of the persistent crud 
(defn commit!
  [ctx xs]
  (reduce (fn [ctx c]
            (store/map-component ctx  c (fn [x] (try-freeze! @x))))
            ctx xs))

;;at the end of day, we want to commit changes.
;;We can maintain another datastore that's just for ephemeral data...
;;When we go to commit, we can define a specific commit event...
(defn commit-telemetry! [ctx _ _] (commit! ctx telemetry-components))
(defn drop-telemetry!   [ctx _ _]
                                        ;(store/drop-domains ctx telemetry-components)
  ctx
  )

;;The cool thing is, since we're using functions,
;;we can integrate these into the function-based
;;system worldview, or we can keep with the decoupled
;;"event-driven" approach... The system-based
;;view makes everything more explicit.

;;by default, we watch all this crap...
;;This is almost identical to the pub/sub setup...
;;We have multiple observer-functions processing
;;events (serially...)
;;in the ECS parlance, these are actually 
(def default-routes
  ;;this system processes deployments, and records new deployments
  ;;each day.
  {:deployment-watch {:begin-day     drop-deployments!   ;;ensure no deployments exist for today.
                      :deploy        record-deployment   ;;record a deployment in new-deployments 
                      :end-of-day    commit-deployments!} ;;store new-deployments in deployments
   ;;this system will ingest events 
   :telemetry-watch   {:begin-day    drop-telemetry!     ;;ensure no telemetry components exist
                       :positionUnit position-handler    ;;record unit policy position changes.
                       :unitMoved    movement-handler    ;;record unit location changes                   
                       :end-of-day   commit-telemetry!   ;;freeze initial and final values for telemtry components.
                       }})

(defn register-default-observers [ctx]
  (simnet/register-routes  default-routes ctx))

;;We can map these into the requisite notifications for the animation/visualzation
;;stuff.  that way, we get our deltas necessary for animation, and it ends up
;;very easy to compare past to previous by diffing.

;;we want to track policy changes.

;;Is there a higher-level concept here?
;;Can we define entity-level events....
;;and then apply our higher-order constructs for mapping and
;;stuff?

;;At the implementation level, the event is a component that's added at
;;an instant in time.
;;Multiple events overwrite (or do we buffer?)

;;We get an event history that way.
;;Also, possibly retain a shitload of events and event messages (may not
;;want to do that).

;;I'd like to say "as the unit moves, record all pairwise
;;movements..."
;;If we go with channels/transducers, we get this kind of stuff.
;;So, if we have an initial channel, we can transduce over it to
;;get the event history..
;;Channels are just buffers in the end. 
;;"ala (->> (:unitMoved) (partition 2 1) 


;;interested in positioning of units...we have an event for that.
;;policy changes...
;:PositionUnit


;;unit movement events...location changes..
;:unitMoved

;For dwell stats, we typically report a proxy record for units 
;not utilized during a simulation period.  To do this, we have to sample the 
;entire unit population, which this event handler does.  This is a specific bit
;of functionality that we should probably move out of here at some point.

(comment ;pulled from ces.engine
(defn sample-unit-cycles
  "This is a special event handler where the simulation context is notified of a 
   need to sample all units in the supply.  This typically happens when a period 
   change occurs, or some other sweeping event requires a synchronization of 
   all the units."
  [t ctx quarterly units]    
  (->> (if (and quarterly (zero? (quot (- t 1) 90)))
           (sim/add-time (+ t 90) ctx)
           ctx)
       (supply/update-all)
       (sim/trigger-event :get-cycle-samples :Engine :Engine
                          "Sample all UIC Cycles.  This is a hack." 
                          {:t t :uics units})))
)

;;Notes on rendering using the prototype quilsample scheme:
;;Currently, we can view the protoype as a function of
;;step->notifications.
;;Basically, at every frame, we take a step.
;;The step results in computing a new context.
;;During the computation of the new context, we end up firing off
;;events, specifically about things that changed (via notify!).
;;notifications are piped to an event channel currently.
;;The notifications we care about are
;;:state-change    : [e state] => color-change? 
;;:color-change    
;;:location-change : [e location destination]  => policy-velocity-change, ?position-change
;;:deployed : deployment {entity component state dwell bog}
;;:reset

;;to more closely mirror what marathon's pushing out...
;;marathon notifies       Hud expects
;;positionChange      ==> state-change, color-change?
;;unitMoved           ==  location-change
;;deploy              ==  deployment
;;CycleCompleted      ==  reset

;;Hud gets                 hud does
;;color-change       ==>  alters statistics...dunno (resamples)
;;state-change       ==>  alters statistics...      (resamples), policy-velocity-change!
;;location-change    ==>  alters statistics 
;;reset              ==>  location-change!, state-change!, etc...
;;deployment         ==>  alters statistics

;;So, we have a general notion of hudmotion....
;;changes in the state motivate changes in the hud.
;;It might be best to explicitly compute changes...rather than
;;side-effect everything.  Once changes are computed,
;;we can fire notifications for them, or choose to formalize
;;the update...

;;With the hud, at any given time, we have a snapshot of the
;;simulation state.  Portions of it don't change, but
;;bits that pertain to the hud (and only the hud) are updated
;;on a per-frame basis....

;;So...in general, we have our visualization that wraps the
;;simulation.

;;If we have a current state, we can render it.
;;If we view everything as immediate-mode, then all rendering
;;is done on a daily basis (that's the current mechanism in hud).
;;At a minimum, if the simulation determines policy-velocity
;;[dwelldx deploydx], policy-position [x y] = [dwell deploy],
;;and location, then our existing framework is
;;compatble.

;;Whenever there is a discrete change, which we know because of
;;the simulated history, we can compute how long we have
;;until the next discrete change.
;;Basically, increment real-world time by sub-steps until we
;;hit the event-step.
;;During these sub-steps, the time-step framework takes hold.
;;We update our time-step statistics from our time-step
;;components.

;;In effect, we have "Two" histories....
;;The macro event-step history,
;;and the micro, time-step history which occurs at a lower level.
;;Another way to look it is this:
;;If we compute the time delta between steps, we have a physical
;;simulation that can step along with us.  All we do
;;is insist on a minimum step-size (i.e. 1).
;;If the next event is farther away than the minimum step-size,then
;;we interpolate.

;;So, the interpolated history is - typically - going to be
;;a partial function of an initial state.  Most everything is
;;staying the same, but some systems (interpolatable? time-varyable?
;;non-discrete? continuous?)  are able to step.

;;In this case, we have the constant state from the previous
;;step (things like policy and the like), while the dynamic
;;state is updated according to a finite number of steps.
;;In fact, we can compute the steps until the next event...

;;So, what we want is (possibly) a layer of indirection here...
;;Another option is to just capture all the changes and
;;interpret the changes appropriately...

;;Then, all we'd need is a change->something
;;Additionally, we can save the changes as part of the compressed history.

;;Rather than cache all of our changes, we instantaneously notify
;;interested parties.  Note: we could cache our changes...
;;It'd be much easier to go from cached changes to notifications...
;;The way we're set up, however, there's a protocol...

;;we have immediate and discrete changes...
;;discrete  changes retain their previous value unless a change occurs.
;;immediate changes are recalculated every step (like immediate-mode rendering).

;;All of our trends are classified as discrete...

;;


;;This is a potentially intriguing idea...
;;We maintain special classes of components that
;;are known to be recepticals for state changes.
;;They act as "properties" in that their state
;;changes are tracked, at least between the
;;initial value of the property and the current.
;;Assuming every component is registered somewhere,
;;we can traverse our store to see if any
;;components exist as properties.
;;If so, we can process them in one fell swoop.
;;Alternately, we just handle the properties separately.
;;Assuming we elevate property definition to the
;;language level, we get the ability to process
;;property components with the library helping out.

;;registry of properties we've defined.
;;This will allow us to use a simplified property-change
;;system, which will grab entities with any of these properties
;;in turn, and commit their changes.  We can handle
;;the update in one fell swoop.
(comment 
(def properties       (atom #{}))
;;maybe we have a convention?  maybe get-ephemeral
;;actually sets up property tracking for us...
(defmacro defproperty [name]
  (let [component-name (keyword (str name "-delta"))]
    `(do (defn ~name [ctx# edata# _]
           (let [[id# old# new#] (:data edata#)
                 [ctx# storage#] (core/get-ephemeral ctx# id# ~component-name nil)]
             (do (if @storage#
                   (swap! storage# #(push-cell % new#))
                   (reset! storage# (->cell old# new#)))
                 ctx#)))
         (swap! ~'marathon.observers/properties conj ~component-name)
         ~name)))
)
;; (defn commit-properties
;;   [ctx]
;;   (if-let [comps @properties] ;;set of keywords that correspond to properties.
;;     (reduce (fn [ctx component]
;;               (let [
;;Can we get all the mileage we need from atoms here?
;; (defmacro property-change! [ctx pname id old new]
;;   `(let [[ctx# storage#] (core/get-ephemeral ~ctx id# ~pname nil)]
;;     (do (if @storage#
;;           (swap! storage# #(push-cell % ~new))
;;           (reset! storage# (->cell ~old ~new)))
;;         ctx#)))
