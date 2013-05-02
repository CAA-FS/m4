(ns marathon.sim.setup)

Option Explicit
'Automates the building of a default behavior manager, linking it to a supply store.
Public Function defaultBehaviorManager(state As TimeStep_SimState, Optional bm As TimeStep_ManagerOfBehavior) As TimeStep_ManagerOfBehavior
If bm Is Nothing Then Set bm = New TimeStep_ManagerOfBehavior
bm.initBehaviors state
Set defaultBehaviorManager = bm
End Function
'creates a default set of parameters derived from a paramters table and an SRCTag table.
Public Function defaultParameters() As TimeStep_Parameters
Set defaultParameters = tablesToParameters(getTable("Parameters"), getTable("SRCTagRecords"))
End Function
'creates a default fill store.
Public Function defaultPolicyStore(context As TimeStep_SimContext) As TimeStep_ManagerOfPolicy
Set defaultPolicyStore = MarathonPolicyIO.policyStoreFromExcel
MarathonOpPolicy.initializePolicyStore defaultPolicyStore, context
End Function
'Creates a fill store and scopes the state as a side effect.
Public Function defaultFillStore(state As TimeStep_SimState) As TimeStep_ManagerOfFill
Set defaultFillStore = MarathonOpFill.fillStoreFromTables(state, getTable("SupplyRecords"), _
                                                      getTable("DemandRecords"), _
                                                      getTable("RelationRecords"))

End Function
'Return a scoped set of supply and demand, based on the information in the fillgraph of the local
'fillstore.
Public Function defaultScopedState(state As TimeStep_SimState) As TimeStep_SimState
Set defaultScopedState = MarathonOpFill.scopeSimState(state.fillstore.fillgraph, state)
End Function
'Creates a default supply.  The default is to derive from Excel worksheets.
Public Function defaultSupply(state As TimeStep_SimState, Optional ensureghost As Boolean) As TimeStep_ManagerOfSupply
Set defaultSupply = New TimeStep_ManagerOfSupply
MarathonOpSupply.fromExcel defaultSupply, state.policystore, state.parameters, state.behaviormanager, state.context, ensureghost
End Function
'Creates a default demand.  The default is to derive from Excel worksheets.
Public Function defaultDemand(state As TimeStep_SimState) As TimeStep_ManagerOfDemand
MarathonOpDemand.fromExcel state
Set defaultDemand = state.demandstore
End Function
Public Function defaultSimState(Optional forRequirements As Boolean) As TimeStep_SimState
 Set defaultSimState = makeSimState()
    With defaultSimState
        'tom change 10 SEP, needed a place to put this, might move it.
        Set .behaviormanager = defaultBehaviorManager(.supplystore, .behaviormanager)
        'moved from class_initialize
        .supplystore.SupplyTraffic = True
        .demandstore.demandtraffic = True
        .policystore.PolicyTraffic = True
        .parameters.Workstate = Initializing
        'Tom Change
        logStatus "Entering Marathon"
        
        
        'profile "ParameterSetup"
        'TESTING.
        '.parameters.fromExcel
        Set .parameters = defaultParameters()
        'profile "ParameterSetup"
        
        'Allow requirements analysis to shorten sim time to only active demands.
        If forRequirements Then
            .truncateTime = True
        End If
    
        'initialize the policy store
        Set .policystore = defaultPolicyStore(.context)
        
        'Early scoping allows us to identify SRCs that are islands, and identify them.  Additionally,
        'if we label them as out of scope, we don't waste time loading them only to scope them out after
        'the fact.  Scoping basically sets up a filter for us.
        If .earlyscoping Then
            'Create a fillstore from supply, demand, policy, parameters - derived from state.
            Set .fillstore = defaultFillStore(defaultSimState)
            'scope the simulation state.  Mutation.
            Set defaultSimState = defaultScopedState(defaultSimState)
        End If
        
        'profile "SupplySetup"
        Set .supplystore = defaultSupply(defaultSimState)
        '.supplystore.fromExcel Worksheets("SupplyAggregate").Range("SupplyStart"), forRequirements
        'profile "SupplySetup"
        
        'profile "DemandSetup"
        Set .demandstore = defaultDemand(defaultSimState)
        '.demandstore.fromExcel Worksheets("DemandRecords").Range("DemandStart")
        'profile "DemandSetup"
        
        
        'TOM Change 26 October -> separated output initialization.  Allows more freedom in generating multiple
        'simulation states.
        
''        'profile "OutputSetup"
''        'TOM Change 7 Jun 2011 -> moved to end to ensure locations are known prior to initialization.
''        .outputstore.init ActiveWorkbook.path & "\", , .policystore.locations, forRequirements
''        'profile "OutputSetup"
        
        If forRequirements Then
            .NoSupplyWarning = True
            'SupplyManager.UnitsFromDictionary unitrecords
        End If
    End With
End Function
'Tom Change 26 Oct 2012 -> extracted from simstate creation.  This is a side-effecting function.  forRequirements should
'be pulled out...
'Given a simulation state, prepare the output store.
Public Function initializeOutput(simstate As TimeStep_SimState, Optional path As String, _
                                    Optional forRequirements As Boolean) As TimeStep_SimState
If path = vbNullString Then path = ActiveWorkbook.path & "\"
With simstate
    'profile "OutputSetup"
    'TOM Change 7 Jun 2011 -> moved to end to ensure locations are known prior to initialization.
    .outputstore.init path, , .policystore.locations, forRequirements
    'profile "OutputSetup"
End With

Set initializeOutput = simstate

End Function
Public Function defaultRequirementState() As TimeStep_SimState
Set defaultRequirementState = defaultSimState(True)
End Function

'A function that derives a simState, relying on defaults where required objects are not provided.
'We allow the custom parts to be defined using a little language.
'Basically, the idea is to allow simple scripting, which can be called from an external file, or
'passed from the repl.

'Forms in our language...

'Supply
'  InitialUnits
'Demand
'  InitialDemand
'Policy
'  InitialRelations.

Public Function stateWith(Optional baseState As TimeStep_SimState, Optional customParts As Dictionary) As TimeStep_SimState

Dim prt

If customParts Is Nothing Then
   Set stateWith = defaultSimState()
Else
    Err.Raise 101, , "Not Implemented!"
'    For Each prt In customParts
'        Select Case CStr(prt)
'            Case "Supply"
'            Case "Demand"
'            Case "Policy"
End If

End Function
    
'    'profile "DemandSetup"
'    .demandstore.fromExcel Worksheets("DemandRecords").Range("DemandStart")

'Timestep_simulation from given parameters.
'Right now, we read parameters/demand from Excel Host
Public Sub Initialize_Engine_From_Excel(Optional state As TimeStep_SimState, Optional forRequirements As Boolean)

If state Is Nothing Then Set state = makeSimState()
With state
    'tom change 10 SEP, needed a place to put this, might move it.
    Set .behaviormanager = defaultBehaviorManager(state, .behaviormanager)
    'moved from class_initialize
    .supplystore.SupplyTraffic = True
    .demandstore.demandtraffic = True
    .policystore.PolicyTraffic = True
    .parameters.Workstate = Initializing
    'Tom Change
    logStatus "Entering Marathon"
    
    
    'profile "ParameterSetup"
    'TESTING.
    '.parameters.fromExcel
    Set .parameters = defaultParameters()
    'profile "ParameterSetup"
    
    'Allow requirements analysis to shorten sim time to only active demands.
    If forRequirements Then
        .truncateTime = True
    End If

    'initialize the policy store
    Set .policystore = defaultPolicyStore(.context)
    
    'Early scoping allows us to identify SRCs that are islands, and identify them.  Additionally,
    'if we label them as out of scope, we don't waste time loading them only to scope them out after
    'the fact.  Scoping basically sets up a filter for us.
    If .earlyscoping Then
        'Create a fillstore from supply, demand, policy, parameters - derived from state.
        Set .fillstore = defaultFillStore(state)
        'scope the simulation state.  Mutation.
        Set state = defaultScopedState(state)
    End If
    
    'profile "SupplySetup"
    Set .supplystore = defaultSupply(state, forRequirements)
    '.supplystore.fromExcel Worksheets("SupplyAggregate").Range("SupplyStart"), forRequirements
    'profile "SupplySetup"
    
    'profile "DemandSetup"
    Set .demandstore = defaultDemand(state)
    '.demandstore.fromExcel Worksheets("DemandRecords").Range("DemandStart")
    'profile "DemandSetup"
    
    
    'TOM Change 26 OCT yanked, output initialization is handled separately now.
''    'profile "OutputSetup"
''    'TOM Change 7 Jun 2011 -> moved to end to ensure locations are known prior to initialization.
''    .outputstore.init ActiveWorkbook.path & "\", , .policystore.locations, forRequirements
''    'profile "OutputSetup"
    
    If forRequirements Then
        .NoSupplyWarning = True
        'SupplyManager.UnitsFromDictionary unitrecords
    End If
End With

End Sub



'For right now, we're just going to load csv's into excel tables....
'We assume that the project points us to the paths needed to get at our csvs.
'From there, we load the files into the active tables.
'Then call initialize engine from excel.
'Should be easy enough...

Public Sub Initialize_Engine_From_Project(project As TimeStep_Project, _
                                            Optional forRequirements As Boolean)

Err.Raise 101, , "Needs updating! "
''
''Project.load 'should pull in all the csv's we need.
''
''parameters.Workstate = Initializing
'''Tom Change
''logStatus "Entering Marathon"
''
''profile "ParameterSetup"
''parameters.fromExcel
''profile "ParameterSetup"
''
''profile "PolicySetup"
''policymanager.fromExcel
''profile "PolicySetup"
''
''profile "SupplySetup"
''SupplyManager.fromExcel Worksheets("SupplyAggregate").Range("SupplyStart"), forRequirements
''profile "SupplySetup"
''
''profile "DemandSetup"
''DemandManager.fromExcel Worksheets("DemandRecords").Range("DemandStart")
''profile "DemandSetup"
''
''profile "FillSetup"
''fillmanager.fromExcel
''profile "FillSetup"
''
''profile "OutputSetup"
'''TOM Change 7 Jun 2011 -> moved to end to ensure locations are known prior to initialization.
''outputmanager.init ActiveWorkbook.path & "\", , policymanager.locations
''profile "OutputSetup"
''
''If forRequirements Then
''    NoSupplyWarning = True
''    'SupplyManager.UnitsFromDictionary unitrecords
''End If

End Sub

'try to keep pre-calculated policy, parameters, etc. in the system.  we just want to flush out the
'dynamic stuff and pull in more information.
'If requirements is true, we will look for supply in a GeneratedSupply worksheet.
Public Sub Reset_Engine_FromExcel(Optional Requirements As Boolean, Optional unitrecords As Dictionary)


Err.Raise 101, , "Needs updating!"
''truncateTime = Requirements
''
''
''reset 'this will reschedule demands
''
''parameters.Workstate = Initializing
'''Tom Change
''logStatus "Resetting Marathon"
''
''If Requirements Then
''    NoSupplyWarning = True
''    If Not (unitrecords Is Nothing) Then
''        SupplyManager.UnitsFromDictionary unitrecords
''    Else
''        'Tom Change 16 April 2012
''        'SupplyManager.UnitsFromSheet "GeneratedSupply"
''        SupplyManager.UnitsFromDictionary getTable(ActiveWorkbook.path & "\GeneratedSupply.csv").toGenericRecords
''    End If
''Else
''    SupplyManager.fromExcel Worksheets("SupplyAggregate").Range("SupplyStart")
''End If
''
''fillmanager.fromExcel

End Sub
'A simple function to automate buildings a TimeStep_Parameters object from two tables.
Public Function tablesToParameters(paramstbl As GenericTable, Optional srctagtable As GenericTable) As TimeStep_Parameters
Dim tags As GenericTags
Set tablesToParameters = tableToParameters(paramstbl)

If exists(srctagtable) Then
    Set tags = tablesToParameters.srcTags
    Set tags = getSRCTags(srctagtable, tags) 'adds srctags to the parameters.
End If

End Function

'Function to read in data from the existing table.
'Basically, we incorporate all the legacy stuff here, using legacy
'named ranges. Notice, I have encapsulated functionality specific to the parameters here.
'Supply and demand data ARE NO LONGER in this routine, they are not parameters.
Public Function tableToParameters(Optional tbl As GenericTable, _
                                    Optional params As TimeStep_Parameters) As TimeStep_Parameters
Dim i As Long
Dim options As Range
Dim rules As Range
Dim newperiod As GenericPeriod
Dim startRowCell As Long, startColCell As Long
Dim numOfPeriods As Long
Dim n As Long
Dim priorityColumns As Long

If tbl Is Nothing Then Set tbl = getTable("Parameters")

If params Is Nothing Then
    Set tableToParameters = New TimeStep_Parameters
Else
    Set tableToParameters = params
End If

While Not tbl.EOF
    tableToParameters.SetKey tbl.getField("ParameterName"), tbl.getField("Value")
    tbl.moveNext
Wend

End Function
'Reads SRC tags from a table, either returning a new set of tags, or adding them to existing tags.
'TOM Change 16 Aug 2011
Public Function getSRCTags(Optional tbl As GenericTable, Optional tags As GenericTags) As GenericTags
Dim src As String
If tbl Is Nothing Then Set tbl = getTable("SRCTagRecords")

If tags Is Nothing Then
    Set getSRCTags = New GenericTags
Else
    Set getSRCTags = tags
End If

While Not tbl.EOF
    src = Trim(tbl.getField("SRC"))
    getSRCTags.addTag tbl.getField("Tag"), src
    tbl.moveNext
Wend

End Function

'To facilitate independent runs, we need a function that can take several source tables, determine
'dependencies between them, and split the tables into N groups of subtables...

'Note -> the fill manager already does most of this, using tables...
'If we use the fill manager's initialization logic, we bake supply, demand, policy, to get a set of
'dependent data.
'Once the dependencies are known, we separate the data by equivalence classes.

'computeDependencies does a quick scan of supply, demand, and relations (substitutions) to determine
'which SRCs must be run together.
Public Function computeDependencies(supplytbl As GenericTable, _
                                    demandtbl As GenericTable, _
                                    relationtbl As GenericTable) As Dictionary
Dim state As TimeStep_SimState
Dim fgraph As TimeStep_FillGraph
Dim res As Dictionary

Set fgraph = MarathonOpFill.FillGraphFromTables(New TimeStep_FillGraph, supplytbl, _
                                                   demandtbl, _
                                                   relationtbl)
                                                   
Set computeDependencies = MarathonOpFill.findIslands(fgraph.reducedgraph)
End Function

'If we have a function, f, we can take an initial set of data for supply, demand, and relations,
'and split one humongous run into N smaller runs.  Note, this is pre-cooking the input data,
'not compiling and allocating everything.
Public Function splitRunData(Optional supplytbl As GenericTable, _
                             Optional demandtbl As GenericTable, _
                             Optional relationtbl As GenericTable) As Dictionary
                             
Dim deps As Dictionary
Dim srcs As Dictionary
Dim group
If nil(supplytbl) Then Set supplytbl = getTable("SupplyRecords")
If nil(demandtbl) Then Set demandtbl = getTable("DemandRecords")
If nil(relationtbl) Then Set relationtbl = getTable("RelationRecords")
Dim datasets As Dictionary
Dim filt As RecordFilter
Dim dataset As Dictionary
Dim tbls As Dictionary
Set tbls = newdict("supply", supplytbl, "demand", demandtbl, "relations", relationtbl)

Set deps = computeDependencies(supplytbl, demandtbl, relationtbl)
'For each equivalence class, split the data.
Set datasets = New Dictionary
For Each group In deps("Dependencies")
    'Should be class_1, clas_2, etc.
    Set srcs = deps("Dependencies").item(group)
    Set filt = dependencyFilter(srcs)
    datasets.add group, getDataSet(tbls, filt)
Next group

Set splitRunData = datasets
Set datasets = Nothing
    
End Function
'TOM added 5 Nov 2012 -> Returns a filter that will return true for any SRCs in the set of
'dependent SRCs.  Applied to the "SRC" field of a record.  Used to extract subsets of
'dependent data.
Public Function dependencyFilter(dependentSRCs As Dictionary) As RecordFilter
Dim src
Dim vals As Collection
Set vals = New Collection
For Each src In dependentSRCs
    vals.add CStr(src)
Next src

Set dependencyFilter = makeRecordFilter(newdict("SRC", vals), orfilter)
End Function
'Extract subtables from each tbl where the SRC field is a member of dependencies.
'Results in a map of tables that are subsets of the originals
Public Function getDataSet(tables As Dictionary, filt As RecordFilter) As Dictionary
Dim nm
Dim tbl As GenericTable

Set getDataSet = New Dictionary
For Each nm In tables
    Set tbl = tables(nm)
'    getDataSet.add nm, TableLib.filterRecords(tbl, filt)
    getDataSet.add nm, TableLib.selectWhere(tbl, filt)
Next nm

End Function
'Tom added 6 Nov 2012
'Serializes the tables in a data set into a directory structure.
'Specifically, splits the supply,demand,relation bits into separate folders...
Public Sub spitDataSet(dataset As Dictionary, Optional path As String)
If path = vbNullString Then path = ActiveWorkbook.path & "\dataset\"
mapToFolders path, dataset, True
End Sub

'Tom added 7 Nov 2012
'Given a dataset of one or more runs.....
'Slurps the dataset into a dictionary, then processes the dictionary from there...
Public Function slurpDataSet(Optional path As String) As Dictionary
If path = vbNullString Then path = ActiveWorkbook.path & "\dataset\"
Set slurpDataSet = folderToMap(path, True)
End Function

'Public Function getRecords(tbl As GenericTable, src As String, Optional fields As Collection) As Collection
'Dim itm
'Dim rec As GenericRecord
'Set getSRCRecords = New Collection
'If fields Is Nothing Then Set fields = list(src)
'
'tbl.moveFirst
'While Not tbl.EOF
'    Set rec = tbl.getRecord
'    For Each itm In fields
'        If rec.fields(CStr(itm)) = src Then
'
'End Function
