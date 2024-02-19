package org.processmining.csmminer.plugins;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.csmminer.CSMMiner;
import org.processmining.csmminer.CSMMinerResults;
import org.processmining.csmminer.InputChecker;
import org.processmining.csmminer.LogProcessor;
import org.processmining.csmminer.relations.StateMap;
import org.processmining.csmminer.relations.TransitionMap;
import org.processmining.csmminer.relations.TransitionsCooccurringStates;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginCategory;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.DirectedGraphElementWeights;
import org.processmining.models.graphbased.directed.transitionsystem.AcceptStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.StartStateSet;
import org.processmining.plugins.transitionsystem.miner.TSMinerOutput;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;
import org.processmining.plugins.tsanalyzer.AnnotatedTransitionSystem;
import org.processmining.plugins.utils.ProvidedObjectHelper;

@Plugin(
		name = "Composite State Machine Miner",
		level = PluginLevel.Regular,
		categories = { PluginCategory.Discovery, PluginCategory.Enhancement },
		parameterLabels = { "State Log", "Perspective State Logs" },
		returnLabels = { "State Models & Interactions", "State Log", "Discovered State Model",
				"Transition Counts", "Initial states", "Final states" },
		returnTypes = { CSMMinerResults.class, XLog.class,	TSMinerTransitionSystem.class, DirectedGraphElementWeights.class,
				StartStateSet.class, AcceptStateSet.class },
		userAccessible = true,
		help = "The Composite State Machine Miner, as described in: "
				+ "\"Discovering and Exploring State-based Models for Multi-perspective Processes\" "
				+ "by M.L. van Eck, N. Sidorova, W.M.P. van der Aalst.<br/>"
				+ "Discovers a state machine for each perspective in the state log and a composite state machine combining all perspectives."
				+ " The interactive visualisation of the results shows behavioural relations between perspectives and various statistics."
				+ "<br/><font color=\"rgb(120,120,120)\">qq</font><br/>"
				+ "Input assumptions:<br/>"
				+ "Each event in the state log has a property \"" + CSMMiner.processNameAttributeLabel + "\" "
				+ "specifying to which perspective it belongs, otherwise it is assigned to a default perspective.<br/>"
				+ "Optionally, each trace in the state log has properties \"" + CSMMiner.processInitialStateAttributeLabel
				+ "[perspectiveName]\" for each perspective, specifying the initial state of [perspectiveName] at the moment of "
				+ "the first state change in the trace.",
		mostSignificantResult = 1)
public class CSMMinerPlugin {
	
	@UITopiaVariant(
			uiLabel = "CSM Miner",
			affiliation = "Eindhoven University of Technology",
			author = "Maikel L. van Eck",
			email = "m.l.v.eck@tue.nl",
			pack = "CSMMiner")
	@PluginVariant(variantLabel = "Mine a Composite State Machine and Perspective State Machines", requiredParameterLabels = { 0 })
	public Object[] mineCompositeStateMachine(final UIPluginContext context, CSMMinerResults results) {
		return mineCompositeStateMachine(context, results.log);
	}
	
	@UITopiaVariant(
			uiLabel = "CSM Miner",
			affiliation = "Eindhoven University of Technology",
			author = "Maikel L. van Eck",
			email = "m.l.v.eck@tue.nl",
			pack = "CSMMiner")
	@PluginVariant(variantLabel = "Mine a Composite State Machine and Perspective State Machines", requiredParameterLabels = { 0 })
	public Object[] mineCompositeStateMachine(final UIPluginContext context, XLog log) {
		context.getProgress().setMinimum(0);
		context.getProgress().setMaximum(8);
		context.getProgress().setIndeterminate(false);
		
		// Check input
		long startTime = System.currentTimeMillis();
		long computationStart = startTime;
		int input = InputChecker.checkStateLog(context, log);
		startTime = outputProgress(context, "Checking input: " + (System.currentTimeMillis() - startTime) + "ms");
		
		if (input == InputChecker.INVALID) {
			System.out.println("Invalid input");
			context.log("Invalid input");
			context.getFutureResult(0).cancel(true);
			context.getFutureResult(1).cancel(true);
			context.getFutureResult(2).cancel(true);
			context.getFutureResult(3).cancel(true);
			context.getFutureResult(4).cancel(true);
			context.getFutureResult(5).cancel(true);
			return new Object[] { null, null, null, null, null, null };
		}
		else if (input == InputChecker.NO_PROCESS_NAME) {
			context.getProgress().setIndeterminate(true);
			StateLogCreatorPlugin plugin = new StateLogCreatorPlugin();
			XLog stateLog = plugin.createStateLog(context, log);
			
			String logName = XConceptExtension.instance().extractName(log);
			String stateLogName = MessageFormat.format("{0} (State Log) @{1}", logName != null ? logName : "NULL",
					DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date()));
			context.getProvidedObjectManager().createProvidedObject(stateLogName, stateLog, XLog.class, context);
			ProvidedObjectHelper.setFavorite(context, stateLog);
			startTime = outputProgress(context, "Creating State Log: " + (System.currentTimeMillis() - startTime) + "ms");
			return new Object[] { new CSMMinerResults(stateLog), null, null, null, null, null };
		}
		
		// Compute perspective names
		List<String> perspectiveNames = LogProcessor.computePerspectiveNames(log);
		startTime = outputProgress(context, "Computing perspective names: " + (System.currentTimeMillis() - startTime) + "ms");
		
		// Create perspective sub logs
		Map<String,XLog> perspectiveSubLogs = LogProcessor.createPerspectiveSubLogs(log, perspectiveNames);
		startTime = outputProgress(context, "Creating perspective sub logs: " + (System.currentTimeMillis() - startTime) + "ms");
		
		// Create synchronous product log
		XLog compositeLog = LogProcessor.createCompositeLog(log, perspectiveNames, true);
		outputProgress(context, "Creating composite log: " + (System.currentTimeMillis() - startTime) + "ms");
		
		CSMMinerResults results = mineCSMWithoutLogProcessing(context, log, perspectiveNames, compositeLog, perspectiveSubLogs,
				computationStart);
		
		// TODO: Create proper return choice
		perspectiveSubLogs.put(CSMMiner.CSMLabel, compositeLog);
		String returnLabel = CSMMiner.CSMLabel;

		return new Object[] { results, perspectiveSubLogs.get(returnLabel), results.tsMinerOutputs.get(returnLabel).getTransitionSystem(),
				results.tsMinerOutputs.get(returnLabel).getWeights(), results.tsMinerOutputs.get(returnLabel).getStarts(),
				results.tsMinerOutputs.get(returnLabel).getAccepts() };
	}
	
	// TODO: create plugin with input a set of perspective logs but no composite log yet, assumption: traces match between perspective logs
//	@UITopiaVariant(
//			uiLabel = "CSM Miner",
//			affiliation = "Eindhoven University of Technology",
//			author = "Maikel L. van Eck",
//			email = "m.l.v.eck@tue.nl",
//			pack = "CSMMiner")
//	@PluginVariant(variantLabel = "Compute Process Interactions and Synchronous Product Process", requiredParameterLabels = { 1 })
//	public Object[] mineCompositeStateMachine(final PluginContext context, XLog... perspectiveLogs) {
//		return mineCSMWithoutLogProcessing();
//	}
	
	public static CSMMinerResults mineCSMWithoutLogProcessing(PluginContext context, XLog log, List<String> perspectiveNames,
			XLog compositeLog,	Map<String, XLog> perspectiveSubLogs, long computationStart) {
		// Run the TSMiner to discover state models
		long startTime = System.currentTimeMillis();
		Map<String,TSMinerOutput> tsMinerOutputs = CSMMiner.discoverStateModels(context, perspectiveSubLogs, compositeLog);
		startTime = outputProgress(context, "Discovering state models: " + (System.currentTimeMillis() - startTime) + "ms");

		// Create annotated state models
		HashMap<String, AnnotatedTransitionSystem> annotatedTSMinerOutputs = CSMMiner.annotateStateModels(context, perspectiveSubLogs,
				compositeLog, tsMinerOutputs);
		startTime = outputProgress(context, "Annotating state models: " + (System.currentTimeMillis() - startTime) + "ms");

		// Compute the perspective interactions
		TransitionsCooccurringStates transitionsCooccurringStates = CSMMiner.computePerspectiveInteractions(log, perspectiveNames);
		startTime = outputProgress(context, "Computing perspective interactions: " + (System.currentTimeMillis() - startTime) + "ms");

		// Create mapping between states and transitions in different models
		StateMap stateMap = CSMMiner.createStateMapping(perspectiveNames, tsMinerOutputs);
		TransitionMap transitionMap = CSMMiner.createTransitionMapping(perspectiveNames, tsMinerOutputs);
		outputProgress(context, "Creating mappings: " + (System.currentTimeMillis() - startTime) + "ms");

		CSMMinerResults results = new CSMMinerResults(tsMinerOutputs, annotatedTSMinerOutputs, transitionsCooccurringStates, stateMap,
				transitionMap, log, compositeLog, perspectiveSubLogs, System.currentTimeMillis() - computationStart, perspectiveNames);
		
		// Calculate relation statistics
		CSMMiner.calculateRelationStatistics(results);
		startTime = outputProgress(context, "Calculating relation statistics: " + (System.currentTimeMillis() - startTime) + "ms");

		return results;
	}
	
	public static long outputProgress(PluginContext context, String output) {
		System.out.println(output);
		context.log(output);
		context.getProgress().inc();
		
		return System.currentTimeMillis();
	}
}
