(ns marathon.sim.policyops)

'The functions in this library focus on quickly deriving policies from existing templates.  There are a
'multitude of hard-coded templates in here that describe canonical policies, which are used in
'the PolicyCreation module to easily derive new policies from the template.  This is more or less a
'policy library.  Note, all of these policies, as of 12 Sep 2012, have been documented in a serialized
'format.  Technically, the use of policy templates does not require VBA code, and will be pushed outside
'of VBA or the host platform in the near future.  By exposing the templates as data, it makes it easier
'to add new templates to the library, which end users can stitch together and derive from.  Additionally,
'pushing the specification outside of VBA should allow for specialized, graphical tools to build policies,
'which are then imported into Marathon during pre-processing.
Private Enum lengthcase
    equiv 'scale is 1.0
    infplus
    infminus
    Normal
End Enum
Option Explicit
'Tom change 24 Sep 2012 -> added to deal with infinite cycle transitions.  Provides a projection function to determine
'how far a unit will project, proportionally, onto a cycle it's changing to.
Public Function computeProportion(ByVal cycletimeA As Single, ByVal cycleLengthA As Single, ByVal cyclelengthB As Single) As Single
Select Case lengthType(cycletimeA, cycleLengthA, cyclelengthB)
    Case Normal
        computeProportion = cycletimeA / cycleLengthA  'produces (ctA * clB)/Cla
    Case equiv
        computeProportion = -1 'cycletimeA / cyclelengthB '(ctA / clB)  * clB = ctA  this is identical to infminus
    Case infplus
        computeProportion = 0.9  '= 0.90 * clB  'scaling factor is arbitrary.....
    Case infminus
        computeProportion = -1 'cycletimeA / cyclelengthB '(ctA  / clB) * clB = ctA this is identical to equiv
End Select

End Function
'TOM Change 27 Sep 2012
Public Function projectCycleTime(ByVal proportionA As Single, ByVal cycletimeA As Single, ByVal cyclelengthB As Single) As Single

Select Case proportionA
    Case Is = -1
        projectCycleTime = cycletimeA
    Case Is <= 1
        projectCycleTime = proportionA * cyclelengthB
    Case Else
        Err.Raise 101, , "UnKnown case"
End Select
        

End Function
'helper function.  Determines the type of cycle transform.
Private Function lengthType(ByVal cycletimeA As Single, ByVal cycleLengthA As Single, ByVal cyclelengthB As Single) As lengthcase
'treat large cycles as effectively infinite for this function.
If cycleLengthA > 365# * 100# Then cycleLengthA = inf
If cyclelengthB > 365# * 100# Then cyclelengthB = inf

If (cycleLengthA = cyclelengthB) Or (cyclelengthB = inf) Then 'equivalent cycle lengths or transitioning to infinite cycle
    lengthType = equiv 'position = cycleTimeA / cyclelengthB
Else
    If (cycleLengthA <> inf) And (cyclelengthB <> inf) Then
        lengthType = Normal
    ElseIf cycleLengthA = inf Then
        If cycletimeA < cyclelengthB Then
            lengthType = infminus 'cyclelength is infinite, but cycletime is effectively less than targetted cycle.
        Else
            lengthType = infplus 'cyclelength is infinite, cycletime is > than targetting cycle.
        End If
    Else
        Err.Raise 101, , "unknown case"
    End If
End If
        
    
        
End Function

Private Function getdelta(position As String, deltas As Dictionary) As Long
getdelta = 0
If Not (deltas Is Nothing) Then
    If deltas.exists(position) Then
        getdelta = deltas(position)
    End If
End If

End Function

'TOM Hack 24 July 2012-> This is a good idea.
'Describe a base policy with recovery built in.
Public Function recoverableTemplate(Optional recoverytime As Single) As TimeStep_Policy
Set recoverableTemplate = New TimeStep_Policy

If recoverytime = 0 Then recoverytime = 90

With recoverableTemplate
    .name = "Recoverable"
    .AddPosition recovery, Recovering, Recovered, Recovered
    .AddRoute recovery, Recovered, recoverytime
End With

End Function
'template for AC policies, we use the parameters to grow and shrink pools
Public Function AC12Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set AC12Template = New TimeStep_Policy
With AC12Template
    .overlap = overlap
    '.AlterPositions ("AC")
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"
    .AddRoute reset, train, 182 + getdelta(available, deltas)
    .AddRoute train, ready, 183 + getdelta(train, deltas)
    .AddRoute ready, available, 365 + getdelta(ready, deltas)
    .AddRoute available, reset, 365 + getdelta(available, deltas)
    .AddRoute deployed, Overlapping, 365 - overlap + getdelta(deployed, deltas)
    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
End With
'policyname, from, to, time, state
End Function
Public Function AC12defaults(overlap As Long) As Dictionary
Set AC12defaults = _
    newdict(reset, 182, train, 183, ready, 365, available, 365, _
                deployed, 365 - overlap, Overlapping, overlap)
End Function

'These are default policy routes for AC entities in arforgen.
'Used as scaffolding for templates.
Public Function ACRoutes(overlap As Long, Optional deltas As Dictionary) As Collection
Set ACRoutes = _
    list(list("Reset", "Train", maxFloat(1, getDeltas("Available", deltas))), _
         list("Train", "Ready", maxFloat(1, getDeltas("Train", deltas))), _
         list("Ready", "Available", maxFloat(1, getDeltas("Ready", deltas))), _
         list("Available", "Reset", maxFloat(1, getDeltas("Available", deltas))), _
         list("Deployed", "Overlapping", maxFloat(1, getDeltas("Deployed", deltas) - overlap)), _
         list("Overlapping", "Reset", maxFloat(1, getDeltas("Overlapping", deltas))))
End Function

'These are default policy routes for RC entities in arforgen.
'Used as scaffolding for templates.
Public Function RCRoutes(overlap As Long, demob As Single, Optional deltas As Dictionary) As Collection
Set RCRoutes = _
   list(list("Reset", "Train", maxFloat(1, getDeltas("Reset", deltas))), _
    list("Train", "Ready", maxFloat(1, getDeltas("Train", deltas))), _
    list("Ready", "Available", maxFloat(1, getDeltas("Ready", deltas))), _
    list("Available", "Reset", maxFloat(1, getDeltas("Available", deltas))), _
    list("Deployed", "Overlapping", maxFloat(1, getDeltas("Deployed", deltas) - overlap)), _
    list("Overlapping", "DeMobilization", maxFloat(1, overlap + getDeltas("Ready", deltas))), _
    list("DeMobilization", "Reset", maxFloat(1, demob + getDeltas("Ready", deltas))))
End Function

'These are default routes for ghost entities. they are significantly different than AC/RC
'Used as scaffolding for templates.
Public Function GhostRoutes(Optional bog As Long, Optional overlap As Long, Optional deltas As Dictionary)

Set GhostRoutes = _
    list(list("Spawning", "Deployable", 0), _
         list("Deployable", "Waiting", 0), _
         list("Waiting", "Deploying", inf), _
         list("Deploying", "NotDeployable", 0), _
         list("NotDeployable", "Deployed", 0), _
         list("Deployed", Overlapping, bog - overlap), _
         list(Overlapping, "ReturnToDeployable", overlap), _
         list("ReturnToDeployable", "Deployable", 0))
End Function


Public Function AC13Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set AC13Template = New TimeStep_Policy
With AC13Template
    .overlap = overlap
    '.AlterPositions ("AC")
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"
    .AddRoute reset, train, 182 + getdelta(reset, deltas)
    .AddRoute train, ready, 183 + getdelta(train, deltas)
    .AddRoute ready, available, 460 + getdelta(ready, deltas)
    .AddRoute available, reset, 270 + getdelta(available, deltas)
    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
End With
End Function

Public Function AC13defaults(overlap As Long) As Dictionary
Set AC13defaults = _
    newdict(reset, 182, train, 183, ready, 460, available, 270, _
                deployed, 270 - overlap, Overlapping, overlap)
End Function



Public Function AC11Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set AC11Template = New TimeStep_Policy
With AC11Template
    .overlap = overlap
    '.AlterPositions ("AC")
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"
    .AddRoute reset, train, 182 + getdelta(reset, deltas)
    .AddRoute train, available, 183 + getdelta(train, deltas)
    .AddRoute available, reset, 365 + getdelta(available, deltas)
    .AddRoute deployed, Overlapping, 365 - overlap + getdelta(deployed, deltas)
    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
End With
End Function

Public Function AC11defaults(overlap As Long) As Dictionary
Set AC11defaults = _
    newdict(reset, 182, train, 183, available, 365, _
                deployed, 365 - overlap, Overlapping, overlap)
End Function

Public Function RC14Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set RC14Template = New TimeStep_Policy
With RC14Template
    .MaxMOB = 95
    .overlap = overlap
    '.AlterPositions ("RC")
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", _
                 deployed, "Bogging", Overlapping, "Overlapping", demobilization, "DeMobilizing"
    .AddRoute reset, train, 365 + getdelta(reset, deltas)
    .AddRoute train, ready, 365 + getdelta(train, deltas)
    .AddRoute ready, available, 730 + getdelta(ready, deltas)
    .AddRoute available, reset, 365 + getdelta(available, deltas)
    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
    
    'TOM Change 13 July 2011
    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
End With
End Function
Public Function RC14Defaults(overlap As Long) As Dictionary
Set RC14Defaults = _
    newdict(reset, 365, train, 365, ready, 730, available, 365, _
                deployed, 270 - overlap, Overlapping, overlap, demobilization, 95)
End Function

'TOM Note 21 Mar 2011 -> Double check the lengths on these policies...
Public Function RC15Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set RC15Template = New TimeStep_Policy
With RC15Template
    .MaxMOB = 95
    .overlap = overlap
    '.AlterPositions ("RC")
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
        demobilization, "DeMobilizing"
    .AddRoute reset, train, 730 + getdelta(reset, deltas)
    .AddRoute train, ready, 365 + getdelta(train, deltas)
    .AddRoute ready, available, 730 + getdelta(ready, deltas)
    .AddRoute available, reset, 365 + getdelta(ready, deltas)
    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
        'TOM Change 13 July 2011
    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
End With
End Function

Public Function RC15Defaults(overlap As Long) As Dictionary
Set RC15Defaults = _
    newdict(reset, 730, train, 365, ready, 730, available, 365, _
                deployed, 270 - overlap, Overlapping, overlap, demobilization, 95)
End Function


Public Function RC11Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set RC11Template = New TimeStep_Policy
With RC11Template
    .MaxMOB = 95
    .overlap = overlap
    '.AlterPositions ("RC")
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
        demobilization, "DeMobilizing"
    .AddRoute reset, train, 182 + getdelta(reset, deltas)
    .AddRoute train, available, 183 + getdelta(train, deltas)
    .AddRoute available, reset, 365 + getdelta(ready, deltas)
    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
        'TOM Change 13 July 2011
    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
End With
End Function

Public Function RC11Defaults(overlap As Long) As Dictionary
Set RC11Defaults = _
    newdict(reset, 182, train, 183, available, 365, _
                deployed, 270 - overlap, Overlapping, overlap, demobilization, 95)
End Function

Public Function RC12Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set RC12Template = New TimeStep_Policy

With RC12Template
    .MaxMOB = 95
    .overlap = overlap
    '.AlterPositions ("RC")
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
        demobilization, "DeMobilizing"
    .AddRoute reset, train, 365 + getdelta(reset, deltas)
    .AddRoute train, available, 365 + getdelta(train, deltas)
    .AddRoute available, reset, 365 + getdelta(available, deltas)
    
    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
    
    'TOM Change 13 July 2011
    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
End With

End Function

Public Function RC12Defaults(overlap As Long) As Dictionary
Set RC12Defaults = _
    newdict(reset, 365, train, 365, available, 365, _
                deployed, 270 - overlap, Overlapping, overlap, demobilization, 95)
End Function

Public Function GhostTemplate(name As String, bog As Single, overlap As Single) As TimeStep_Policy
Set GhostTemplate = New TimeStep_Policy
With GhostTemplate
    .overlap = overlap
    '.AlterPositions ("Ghost")
    .name = name
    .AddPosition "Spawning", "Spawning", "Deployable", "Deployable", "Waiting", "Nothing", _
                "Deploying", "Deploying", "NotDeployable", "NotDeployable", _
                    deployed, "Bogging", Overlapping, "Overlapping", _
                            "BehaviorChange", "BehaviorChange", "ReturnToDeployable", "Nothing"
    .AddRoute "Spawning", "Deployable", 0
    .AddRoute "Deployable", "Waiting", 0
    .AddRoute "Waiting", "Deploying", inf
    .AddRoute "Deploying", "NotDeployable", 0
    .AddRoute "NotDeployable", deployed, 0
    .AddRoute deployed, Overlapping, bog - overlap
    .AddRoute Overlapping, "ReturnToDeployable", overlap
    .AddRoute "ReturnToDeployable", "Deployable", 0
    .startstate = "Deployable"
    .endstate = "ReturnToDeployable"
    .cyclelength = inf
End With

End Function

Public Function GhostDefaults(bog As Long, overlap As Long) As Dictionary
Set GhostDefaults = newdict("Spawning", 0, _
                            "Deployable", 0, _
                            "Waiting", inf, _
                            "Deploying", 0, _
                            "NotDeployable", 0, _
                            deployed, bog - overlap, _
                            Overlapping, overlap, _
                            "ReturnToDeployable", 0)
End Function

'template for RC policies with a remob time, we use the parameters to grow and shrink pools
'Allows 2 deployments.  Recovery time dictates the amount of time spent in between bogs.
Public Function RC14ReMobTemplate(name As String, overlap As Long, Optional deltas As Dictionary, _
                                    Optional recoverytime As Long, Optional bogbudget As Long) As TimeStep_Policy
Set RC14ReMobTemplate = New TimeStep_Policy
If recoverytime = 0 Then recoverytime = 365
If bogbudget = 0 Then bogbudget = 270 * 2 'default to 2 deployments
With RC14ReMobTemplate
    .MaxMOB = 95
    .overlap = overlap
    '.AlterPositions ("RC")
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", _
                 deployed, "Bogging", Overlapping, "Overlapping", demobilization, "DeMobilizing", _
                    recovery, Recovering, Recovered, Recovered
    .AddRoute reset, train, 365 + getdelta(reset, deltas)
    .AddRoute train, ready, 365 + getdelta(train, deltas)
    .AddRoute ready, available, 730 + getdelta(ready, deltas)
    .AddRoute available, reset, 365 + getdelta(available, deltas)
    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
    
    'TOM Change 13 July 2011
    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
    '.AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
'    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
    .AddRoute Overlapping, recovery, overlap + getdelta(Overlapping, deltas)
    .AddRoute recovery, Recovered, CSng(recoverytime)
    .AddRoute Recovered, demobilization, 95 + getdelta(ready, deltas)
    .AddRoute demobilization, reset, 0
    .bogbudget = bogbudget
End With
'policyname, from, to, time, state
End Function
Public Function RC14ReMobDefaults(overlap As Long) As Dictionary
Set RC14ReMobDefaults = _
    newdict(reset, 365, train, 365, ready, 730, available, 365, _
                deployed, 270 - overlap, Overlapping, overlap, demobilization, 95)
End Function


'TOM Hack 24 July 2012
Public Function maxUtilizationTemplate(name As String, bog As Single, overlap As Single, mindwell As Single) As TimeStep_Policy
Set maxUtilizationTemplate = New TimeStep_Policy
With maxUtilizationTemplate
    .name = name
    .overlap = overlap
    .name = name
    .AddPosition reset, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
        "Deployable", "Deployable", "NotDeployable", "NotDeployable"
    .AddRoute reset, "Deployable", mindwell
    .AddRoute "Deployable", available, 0
    .AddRoute available, "NotDeployable", inf
    .AddRoute "NotDeployable", reset, 0
    .AddRoute deployed, Overlapping, bog - overlap
    .AddRoute Overlapping, reset, overlap
    .cyclelength = inf
    .maxbog = bog
    .mindwell = mindwell
    .maxdwell = inf
    .startdeployable = mindwell
    .stopdeployable = inf
    .startstate = reset
    .endstate = available
End With

End Function
'MaxUtilization
'ACFFG
'RCOpSus
'RCFFG

'TOM Hack 24 july 2012
'Mission Pool template
Public Function FFGMissionTemplate(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set FFGMissionTemplate = New TimeStep_Policy
With FFGMissionTemplate
    .overlap = overlap
    .name = name
    .AddPosition reset, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
                    "Deployable", "Deployable", "NotDeployable", "NotDeployable"
    .AddRoute reset, "Deployable", 0 + getdelta("Deployable", deltas)
    .AddRoute "Deployable", available, 0
    .AddRoute available, "NotDeployable", inf
    .AddRoute "NotDeployable", reset, inf + getdelta(available, deltas)
    .AddRoute deployed, Overlapping, 365 - overlap + getdelta(deployed, deltas)
    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
    'TOM Change 27 Sep 2012
    .startdeployable = inf
End With
'policyname, from, to, time, state
End Function

'TOM Hack 24 July 2012
'template for AC Future Force Generation policies, we use the parameters to grow and shrink pools
Public Function ACFFGTemplate(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set ACFFGTemplate = New TimeStep_Policy
With ACFFGTemplate
    .overlap = overlap
    .name = name
    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"

    .AddRoute reset, train, 91 + getdelta(available, deltas)
    .AddRoute train, ready, 91 + getdelta(train, deltas)
    .AddRoute ready, available, 183 + getdelta(ready, deltas)
    .AddRoute available, reset, 365 + getdelta(available, deltas)
    .AddRoute deployed, Overlapping, 365 - overlap + getdelta(deployed, deltas)
    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
End With
'policyname, from, to, time, state
End Function

'TOM Hack 24 July 2012
'template for RC Future Force Generation policies, we use the parameters to grow and shrink pools
Public Function RCFFGTemplate(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set RCFFGTemplate = RC14Template(name, overlap, deltas)
End Function

'TOM Hack 24 July 2012 -> Operational and Sustainment template.
'The difference with the O&S policy, is that upon returning from deployment, they should not go back
'to O&S, they should remain in roto status.  Another difference is that they're deployable window is
'shorter than an equivalent RC....and it takes longer for any of them to deploy.
Public Function RCOpSusTemplate(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
Set RCOpSusTemplate = New TimeStep_Policy
Dim os As String
os = "OS_"
With RCOpSusTemplate
    .MaxMOB = 95
    .overlap = overlap
    .name = name
    .AddPosition os & reset, "Dwelling", os & train, "Dwelling", os & ready, "Dwelling", os & available, "Dwelling", _
                 deployed, "Bogging", Overlapping, "Overlapping", demobilization, "DeMobilizing", _
                    "Promoting", "CheckPromotion"
    .AddRoute os & reset, os & train, 365 + getdelta(reset, deltas)
    .AddRoute os & train, os & ready, 365 + getdelta(train, deltas)
    .AddRoute os & ready, os & available, 730 + getdelta(ready, deltas)
    .AddRoute os & available, os & reset, 365 + getdelta(available, deltas)
    .AddRoute deployed, "Promoting", 0
    .AddRoute "Promoting", Overlapping, 270 - overlap + getdelta(deployed, deltas)
    'TOM Change 13 July 2011
    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
'    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
    'This is different than the RC14 policy.  After deploying, units do not go back to OS_Reset....
    'Instead, units go to a promotion state, where they get change policies to the prevailing default RC policy.
    .AddRoute demobilization, os & reset, 95 + getdelta(ready, deltas)
    .startstate = os & reset
    .endstate = os & available
End With
End Function

'TOM TODO ->
'Need a more declarative way to do this, the numerical values hide what's going on in the function
'TOM NOTE 21 MAr 2011 -> Need to parametrically vary these.  I was screwing up the DeployableStart
'and DeployableStart by winging it.  Basically a data error.
Public Function DefaultArforgenPolicies() As Dictionary

Dim pol
Dim policy As TimeStep_Policy
Dim policies As Dictionary

Set policies = New Dictionary

Set policy = RegisterTemplate(AC12, 365 * 3, 365 * 2, 365, 365 * 2, 365 * 2 + 1, 45)
'RegisterPolicyLocations policy
policy.name = "AC12Strict"
policies.add policy.name, policy

'Changed from +- 90, to +- 180
Set policy = RegisterTemplate(AC12, 365 * 3, 365 * 2, 365, 365 * 2 - 180, 365 * 2 + 180, 45)
'RegisterPolicyLocations policy
policy.name = "AC12"
policies.add policy.name, policy

'units can go until end of available
Set policy = RegisterTemplate(AC12, 365 * 3, 365, 365, 365 * 1, 1095, 45)
policy.name = "AC12Loose"
policies.add policy.name, policy


Set policy = RegisterTemplate(AC13, 365 * 3, 825, 270, 825, 825 + 1, 45)
'RegisterPolicyLocations policy
policy.name = "AC13Strict"
policies.add policy.name, policy

'change +-180
Set policy = RegisterTemplate(AC13, 365 * 3, 825, 270, 825 - 180, 825 + 180, 45)
'RegisterPolicyLocations policy
policy.name = "AC13"
policies.add policy.name, policy

'made this actually loose.
Set policy = RegisterTemplate(AC13, 365 * 3, 365, 270, 365, 365 * 3, 45)
policy.name = "AC13Loose"
policies.add policy.name, policy


Set policy = RegisterTemplate(AC11, 365 * 2, 365, 365, 365, 365 * 2, 0) '0 overlap
'RegisterPolicyLocations policy
policies.add policy.name, policy

Set policy = RegisterTemplate(RC14, 365 * 5, 365 * 2, 270, 365 * 4, 365 * 4 + 1, 45)
'RegisterPolicyLocations policy
policy.name = "RC14Strict"
policies.add policy.name, policy

'+- 180
Set policy = RegisterTemplate(RC14, 365 * 5, 365 * 2, 270, 365 * 4 - 180, 365 * 4 + 180, 45)
'RegisterPolicyLocations policy
policy.name = "RC14"
policies.add policy.name, policy

Set policy = RegisterTemplate(RC14, 365 * 5, 365 * 2, 270, 365 * 2, 365 * 5 - 90, 45)
policy.name = "RC14Loose"
policies.add policy.name, policy


Set policy = RegisterTemplate(RC15, 365 * 6, 365 * 3, 270, 365 * 5, 365 * 5 + 1, 45)
'RegisterPolicyLocations policy
policy.name = "RC15Strict"
policies.add policy.name, policy

'+- 180
Set policy = RegisterTemplate(RC15, 365 * 6, 365 * 3, 270, 365 * 5 - 180, 365 * 5 + 180, 45)
'RegisterPolicyLocations policy
policy.name = "RC15"
policies.add policy.name, policy

Set policy = RegisterTemplate(RC15, 365 * 6, 365 * 3, 270, 365 * 5 - 90, 365 * 5 + 90, 45)
policy.name = "RC15Loose"
policies.add policy.name, policy

'This is the RC surge policy...
'Note -> changed to 0 overlap for surge.
'TOM Change 13 July 2011
Set policy = RegisterTemplate(RC12, 365 * 3, 365, 270, 365 * 2, (365 * 3) - 90, 0)
policy.name = "RC12"
policies.add policy.name, policy

Set policy = policies(AC12).clone
policy.name = GhostPermanent12
policies.add policy.name, policy

Set policy = policies(AC13).clone
policy.name = GhostPermanent13
policies.add policy.name, policy

Set policy = RegisterGhostTemplate("Ghost365_45", 365, 45)
policies.add policy.name, policy

Set policy = RegisterGhostTemplate("Ghost270_45", 270, 45)
policies.add policy.name, policy

Set policy = RegisterGhostTemplate("BOGForever", inf, 0)
policies.add policy.name, policy


''Enabler policies....i.e. 30 day overlap

Set policy = RegisterTemplate(AC13, 365 * 3, 825, 270, 825 - 180, 825 + 180, 30)
policy.name = "AC13_Enabler"
policies.add policy.name, policy

'units can go until end of available
Set policy = RegisterTemplate(AC12, 365 * 3, 365, 365, 365 * 1, 1095, 30)
policy.name = "AC12Loose_Enabler"
policies.add policy.name, policy

Set policy = RegisterTemplate(RC15, 365 * 6, 365 * 3, 270, 365 * 5 - 180, 365 * 5 + 180, 30)
'RegisterPolicyLocations policy
policy.name = "RC15_Enabler"
policies.add policy.name, policy

Set policy = RegisterTemplate(RC14, 365 * 5, 365 * 2, 270, 365 * 2, 365 * 5 - 90, 30)
policy.name = "RC14Loose_Enabler"
policies.add policy.name, policy

Set policy = RegisterGhostTemplate("Ghost365_30", 365, 30)
policy.name = "Ghost365_30"
policies.add policy.name, policy

Set policy = Nothing

'Set DefaultArforgenPolicies = listVals(policies)
Set DefaultArforgenPolicies = policies
Set policies = Nothing

End Function
'
Public Function TFPolicies() As Dictionary
Dim policy As TimeStep_Policy
Set TFPolicies = New Dictionary

'This is a special policy adapted for T.F's study.
'RC has an extra year of availability to deploy.  I don't think it will matter.
Set policy = RegisterTemplate(RC14, 365 * 6, 365 * 2, 270, 365 * 2, 365 * 6 - 90, 45, _
                                newdict(available, 365))
policy.name = "RC14Loose_3Year"
TFPolicies.add policy.name, policy

'This is a special policy adapted for T.F.'s study.
Set policy = RegisterTemplate(RC14ReMob, 365 * 7, 365 * 2, 270, 365 * 2, 365 * 7 - 90, 45, _
                                newdict(available, 730))
policy.name = RC14ReMob
TFPolicies.add policy.name, policy

'This is a special policy adapted for T.F's study.
'RC has an extra year of availability to deploy.  I don't think it will matter.
Set policy = RegisterTemplate(RC14, 365 * 6, 365 * 2, 270, 365 * 2, 365 * 6 - 90, 30, _
                                newdict(available, 365))
policy.name = "RC14Loose_3Year_Enabler"
TFPolicies.add policy.name, policy

'This is a special policy adapted for T.F.'s study.
    Set policy = RegisterTemplate(RC14ReMob, 365 * 7, 365 * 2, 270, 365 * 2, 365 * 7 - 90, 30, _
                                newdict(available, 730))
policy.name = RC14ReMob & "_Enabler"
TFPolicies.add policy.name, policy

End Function

'Integrated 10 Sep 2012
'TOM Hack! 24 July 2012 -> this is a temporary patch.  There's no reason this shouldn't be data driven...blah
Public Function FFGPolicies() As Dictionary
Set FFGPolicies = New Dictionary

Dim policy As TimeStep_Policy

Set policy = ACFFGTemplate("FFGACRoto", 45)
FFGPolicies.add policy.name, policy

Set policy = ACFFGTemplate("FFGACRoto_Enabler", 30)
FFGPolicies.add policy.name, policy


Set policy = RCFFGTemplate("FFGRCRoto", 45)
FFGPolicies.add policy.name, policy


Set policy = RCFFGTemplate("FFGRCRoto_Enabler", 45)
FFGPolicies.add policy.name, policy


Set policy = FFGMissionTemplate("FFGMission", 45)
FFGPolicies.add policy.name, policy


Set policy = FFGMissionTemplate("FFGMission_Enabler", 30)
FFGPolicies.add policy.name, policy


Set policy = MarathonPolicy.RCOpSusTemplate("RCOpSus", 45)
FFGPolicies.add policy.name, policy


Set policy = MarathonPolicy.RCOpSusTemplate("RCOpSus_Enabler", 30)
FFGPolicies.add policy.name, policy


'9999999 730 9999999 730 9999999 30  0   Auto    {}

End Function


Public Function MaxUtilizationPolicies() As Dictionary
Set MaxUtilizationPolicies = New Dictionary
Dim policy As TimeStep_Policy

Set policy = MarathonPolicy.maxUtilizationTemplate("MaxUtilization", 365, 45, 0)
MaxUtilizationPolicies.add policy.name, policy

Set policy = MarathonPolicy.maxUtilizationTemplate("MaxUtilization_Enabler", 365, 30, 0)
MaxUtilizationPolicies.add policy.name, policy

Set policy = MarathonPolicy.maxUtilizationTemplate("NearMaxUtilization", 270, 45, 730)
MaxUtilizationPolicies.add policy.name, policy

Set policy = MarathonPolicy.maxUtilizationTemplate("NearMaxUtilization_Enabler", 270, 30, 730)
MaxUtilizationPolicies.add policy.name, policy

End Function

'
'
''create some new policies. Let's see if we can interactively build this badboy
'Public Sub tst()
'Dim p As TimeStep_Policy
'Set p = New TimeStep_Policy
'
'End Sub


''This is part of a reorganization of a lot of the embedded functionality in the early object oriented
''implementation for marathon.  All functions in this module either consume no arguments, to produce
''policies, or modify existing policies in some way.  Most are a port from TimeStep_Policy
'Private Type routerec
'  source As String
'  dest As String
'  distance As Single
'End Type
'Dim Key
'Option Explicit
'Public Function create(positions As Dictionary, routes As Dictionary) As TimeStep_Policy
'Set create = New TimeStep_Policy
'End Function
'Private Function addPositions(positions As Dictionary, targetpolicy As TimeStep_Policy) As TimeStep_Policy
'Set addPositions = targetpolicy
'
'With addPositions
'    For Each Key In positions.keys
'        .AddPosition CStr(Key), CStr(positions(Key))
'    Next Key
'End With
'End Function
''expects a dictionary with triples as keys
'Private Function getRoute(routes As Dictionary, Key As String) As routerec
''Dim rt As Collection
''With getRoute
'
'End Function
'Private Function addRoutes(routes As Dictionary, targetpolicy As TimeStep_Policy) As TimeStep_Policy
''Set addRoutes = targetpolicy
''With addRoutes
''    For Each key In routes.keys
'End Function
'
'
''Policy  Type    Schedule    Path    ExpectedBOG ExpectedDwell   Overlap ExpectedCycleLength TimeInterval
'Public Function fromRecord(inrec As GenericRecord) As TimeStep_Policy
'
'End Function
'Public Function fromPolicy(inpolicy As TimeStep_Policy) As TimeStep_Policy
'Set fromPolicy = inpolicy.clone
''make changes
'End Function
'Private Function getdelta(position As String, deltas As Dictionary) As Long
'getdelta = 0
'If Not (deltas Is Nothing) Then
'    If deltas.exists(position) Then
'        getdelta = deltas(position)
'    End If
'End If
'
'End Function
''TOM Hack 24 July 2012-> This is a good idea.
''Describe a base policy with recovery built in.
'Public Function recoverableTemplate(Optional recoverytime As Single) As TimeStep_Policy
'Set recoverableTemplate = New TimeStep_Policy
'
'If recoverytime = 0 Then recoverytime = 90
'
'With recoverableTemplate
'    .name = "Recoverable"
'    .AddPosition recovery, Recovering, Recovered, Recovered
'    .AddRoute recovery, Recovered, recoverytime
'End With
'
'End Function
''template for AC policies, we use the parameters to grow and shrink pools
'Public Function AC12Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set AC12Template = New TimeStep_Policy
'With AC12Template
'    .overlap = overlap
'    '.AlterPositions ("AC")
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"
'    .AddRoute reset, train, 182 + getdelta(available, deltas)
'    .AddRoute train, ready, 183 + getdelta(train, deltas)
'    .AddRoute ready, available, 365 + getdelta(ready, deltas)
'    .AddRoute available, reset, 365 + getdelta(available, deltas)
'    .AddRoute deployed, Overlapping, 365 - overlap + getdelta(deployed, deltas)
'    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
'End With
''policyname, from, to, time, state
'End Function
'Public Function AC13Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set AC13Template = New TimeStep_Policy
'With AC13Template
'    .overlap = overlap
'    '.AlterPositions ("AC")
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"
'    .AddRoute reset, train, 182 + getdelta(reset, deltas)
'    .AddRoute train, ready, 183 + getdelta(train, deltas)
'    .AddRoute ready, available, 460 + getdelta(ready, deltas)
'    .AddRoute available, reset, 270 + getdelta(available, deltas)
'    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
'    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
'End With
'End Function
'Public Function AC11Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set AC11Template = New TimeStep_Policy
'With AC11Template
'    .overlap = overlap
'    '.AlterPositions ("AC")
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"
'    .AddRoute reset, train, 182 + getdelta(reset, deltas)
'    .AddRoute train, available, 183 + getdelta(train, deltas)
'    .AddRoute available, reset, 365 + getdelta(available, deltas)
'    .AddRoute deployed, Overlapping, 365 - overlap + getdelta(deployed, deltas)
'    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
'End With
'End Function
'Public Function RC14Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set RC14Template = New TimeStep_Policy
'With RC14Template
'    .MaxMOB = 95
'    .overlap = overlap
'    '.AlterPositions ("RC")
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", _
'                 deployed, "Bogging", Overlapping, "Overlapping", demobilization, "DeMobilizing"
'    .AddRoute reset, train, 365 + getdelta(reset, deltas)
'    .AddRoute train, ready, 365 + getdelta(train, deltas)
'    .AddRoute ready, available, 730 + getdelta(ready, deltas)
'    .AddRoute available, reset, 365 + getdelta(available, deltas)
'    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
'
'    'TOM Change 13 July 2011
'    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
'    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
'    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
'End With
'End Function
''TOM Note 21 Mar 2011 -> Double check the lengths on these policies...
'Public Function RC15Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set RC15Template = New TimeStep_Policy
'With RC15Template
'    .MaxMOB = 95
'    .overlap = overlap
'    '.AlterPositions ("RC")
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
'        demobilization, "DeMobilizing"
'    .AddRoute reset, train, 730 + getdelta(reset, deltas)
'    .AddRoute train, ready, 365 + getdelta(train, deltas)
'    .AddRoute ready, available, 730 + getdelta(ready, deltas)
'    .AddRoute available, reset, 365 + getdelta(ready, deltas)
'    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
'        'TOM Change 13 July 2011
'    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
'    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
'    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
'End With
'End Function
'Public Function RC11Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set RC11Template = New TimeStep_Policy
'With RC11Template
'    .MaxMOB = 95
'    .overlap = overlap
'    '.AlterPositions ("RC")
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
'        demobilization, "DeMobilizing"
'    .AddRoute reset, train, 182 + getdelta(reset, deltas)
'    .AddRoute train, available, 183 + getdelta(train, deltas)
'    .AddRoute available, reset, 365 + getdelta(ready, deltas)
'    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
'        'TOM Change 13 July 2011
'    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
'    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
'    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
'End With
'End Function
'Public Function RC12Template(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set RC12Template = New TimeStep_Policy
'
'With RC12Template
'    .MaxMOB = 95
'    .overlap = overlap
'    '.AlterPositions ("RC")
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
'        demobilization, "DeMobilizing"
'    .AddRoute reset, train, 365 + getdelta(reset, deltas)
'    .AddRoute train, available, 365 + getdelta(train, deltas)
'    .AddRoute available, reset, 365 + getdelta(available, deltas)
'
'    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
'
'    'TOM Change 13 July 2011
'    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
'    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
'    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
'End With
'
'End Function
'Public Function GhostTemplate(name As String, bog As Single, overlap As Single) As TimeStep_Policy
'Set GhostTemplate = New TimeStep_Policy
'With GhostTemplate
'    .overlap = overlap
'    '.AlterPositions ("Ghost")
'    .name = name
'    .AddPosition "Spawning", "Spawning", "Deployable", "Deployable", "Waiting", "Nothing", _
'                "Deploying", "Deploying", "NotDeployable", "NotDeployable", _
'                    deployed, "Bogging", Overlapping, "Overlapping", _
'                            "BehaviorChange", "BehaviorChange", "ReturnToDeployable", "Nothing"
'    .AddRoute "Spawning", "Deployable", 0
'    .AddRoute "Deployable", "Waiting", 0
'    .AddRoute "Waiting", "Deploying", 9999999
'    .AddRoute "Deploying", "NotDeployable", 0
'    .AddRoute "NotDeployable", deployed, 0
'    .AddRoute deployed, Overlapping, bog - overlap
'    .AddRoute Overlapping, "ReturnToDeployable", overlap
'    .AddRoute "ReturnToDeployable", "Deployable", 0
'    .startstate = "Deployable"
'    .endstate = "ReturnToDeployable"
'    .cyclelength = 9999999
'End With
'
'End Function
''template for RC policies with a remob time, we use the parameters to grow and shrink pools
''Allows 2 deployments.  Recovery time dictates the amount of time spent in between bogs.
'Public Function RC14ReMobTemplate(name As String, overlap As Long, Optional deltas As Dictionary, _
'                                    Optional recoverytime As Long, Optional bogbudget As Long) As TimeStep_Policy
'Set RC14ReMobTemplate = New TimeStep_Policy
'If recoverytime = 0 Then recoverytime = 365
'If bogbudget = 0 Then bogbudget = 270 * 2 'default to 2 deployments
'With RC14ReMobTemplate
'    .MaxMOB = 95
'    .overlap = overlap
'    '.AlterPositions ("RC")
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", _
'                 deployed, "Bogging", Overlapping, "Overlapping", demobilization, "DeMobilizing", _
'                    recovery, Recovering, Recovered, Recovered
'    .AddRoute reset, train, 365 + getdelta(reset, deltas)
'    .AddRoute train, ready, 365 + getdelta(train, deltas)
'    .AddRoute ready, available, 730 + getdelta(ready, deltas)
'    .AddRoute available, reset, 365 + getdelta(available, deltas)
'    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
'
'    'TOM Change 13 July 2011
'    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
'    '.AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
''    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
'    .AddRoute Overlapping, recovery, overlap + getdelta(Overlapping, deltas)
'    .AddRoute recovery, Recovered, CSng(recoverytime)
'    .AddRoute Recovered, demobilization, 95 + getdelta(ready, deltas)
'    .AddRoute demobilization, reset, 0
'    .bogbudget = bogbudget
'End With
''policyname, from, to, time, state
'End Function
'
''TOM Hack 24 July 2012
'Public Function maxUtilizationTemplate(name As String, bog As Single, overlap As Single, mindwell As Single) As TimeStep_Policy
'Set maxUtilizationTemplate = New TimeStep_Policy
'With maxUtilizationTemplate
'    .name = name
'    .overlap = overlap
'    .name = name
'    .AddPosition reset, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping", _
'        "Deployable", "Deployable", "NotDeployable", "NotDeployable"
'    .AddRoute reset, "Deployable", mindwell
'    .AddRoute "Deployable", available, 0
'    .AddRoute available, "NotDeployable", 9999999
'    .AddRoute "NotDeployable", reset, 0
'    .AddRoute deployed, Overlapping, bog - overlap
'    .AddRoute Overlapping, reset, overlap
'    .cyclelength = 9999999
'    .MaxBOG = bog
'    .mindwell = mindwell
'    .MaxDwell = 9999999
'    .startdeployable = mindwell
'    .stopdeployable = 9999999
'    .startstate = reset
'    .endstate = available
'End With
'
'End Function
''MaxUtilization
''ACFFG
''RCOpSus
''RCFFG
'
''TOM Hack 24 july 2012
''Mission Pool template
'Public Function FFGMissionTemplate(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set FFGMissionTemplate = New TimeStep_Policy
'With FFGMissionTemplate
'    .overlap = overlap
'    .name = name
'    .AddPosition reset, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"
'    .AddRoute reset, available, 0 + getdelta(reset, deltas)
'    .AddRoute available, reset, 9999999 + getdelta(available, deltas)
'    .AddRoute deployed, Overlapping, 365 - overlap + getdelta(deployed, deltas)
'    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
'    .startdeployable = 9999999
'End With
''policyname, from, to, time, state
'End Function
'
''TOM Hack 24 July 2012
''template for AC Future Force Generation policies, we use the parameters to grow and shrink pools
'Public Function ACFFGTemplate(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set ACFFGTemplate = New TimeStep_Policy
'With ACFFGTemplate
'    .overlap = overlap
'    .name = name
'    .AddPosition reset, "Dwelling", train, "Dwelling", ready, "Dwelling", available, "Dwelling", deployed, "Bogging", Overlapping, "Overlapping"
'    .AddRoute reset, train, 91 + getdelta(available, deltas)
'    .AddRoute train, ready, 91 + getdelta(train, deltas)
'    .AddRoute ready, available, 183 + getdelta(ready, deltas)
'    .AddRoute available, reset, 365 + getdelta(available, deltas)
'    .AddRoute deployed, Overlapping, 365 - overlap + getdelta(deployed, deltas)
'    .AddRoute Overlapping, reset, overlap + getdelta(Overlapping, deltas)
'End With
''policyname, from, to, time, state
'End Function
''TOM Hack 24 July 2012
''template for RC Future Force Generation policies, we use the parameters to grow and shrink pools
'Public Function RCFFGTemplate(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set RCFFGTemplate = RC14Template(name, overlap, deltas)
'End Function
'
''TOM Hack 24 July 2012 -> Operational and Sustainment template.
''The difference with the O&S policy, is that upon returning from deployment, they should not go back
''to O&S, they should remain in roto status.  Another difference is that they're deployable window is
''shorter than an equivalent RC....and it takes longer for any of them to deploy.
'Public Function RCOpSusTemplate(name As String, overlap As Long, Optional deltas As Dictionary) As TimeStep_Policy
'Set RCOpSusTemplate = New TimeStep_Policy
'Dim os As String
'os = "OS_"
'With RCOpSusTemplate
'    .MaxMOB = 95
'    .overlap = overlap
'    .name = name
'    .AddPosition os & reset, "Dwelling", os & train, "Dwelling", os & ready, "Dwelling", os & available, "Dwelling", _
'                 deployed, "Bogging", Overlapping, "Overlapping", demobilization, "DeMobilizing", _
'                    "Promotion", "PolicyChange"
'    .AddRoute os & reset, os & train, 365 + getdelta(reset, deltas)
'    .AddRoute os & train, os & ready, 365 + getdelta(train, deltas)
'    .AddRoute os & ready, os & available, 730 + getdelta(ready, deltas)
'    .AddRoute os & available, os & reset, 365 + getdelta(available, deltas)
'    .AddRoute deployed, Overlapping, 270 - overlap + getdelta(deployed, deltas)
'    'TOM Change 13 July 2011
'    '.AddRoute Overlapping, Reset, overlap + getdelta(Overlapping, deltas)
'    .AddRoute Overlapping, demobilization, overlap + getdelta(ready, deltas)
''    .AddRoute demobilization, reset, 95 + getdelta(ready, deltas)
'    'This is different than the RC14 policy.  After deploying, units do not go back to OS_Reset....
'    'Instead, units go to a promotion state, where they get change policies to the prevailing default RC policy.
'    .AddRoute demobilization, "Promotion", 95 + getdelta(ready, deltas)
'    .startstate = os & reset
'    .endstate = os & available
'End With
'End Function
'
'
''create some new policies. Let's see if we can interactively build this badboy
'Public Sub tst()
'Dim p As TimeStep_Policy
'Set p = New TimeStep_Policy
'
'End Sub
