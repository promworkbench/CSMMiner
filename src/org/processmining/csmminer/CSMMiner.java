package org.processmining.csmminer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.csmminer.relations.StateCounts;
import org.processmining.csmminer.relations.StateEntry;
import org.processmining.csmminer.relations.StateMap;
import org.processmining.csmminer.relations.TransitionMap;
import org.processmining.csmminer.relations.TransitionsCooccurringStates;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.plugins.transitionsystem.miner.TSMinerInput;
import org.processmining.plugins.transitionsystem.miner.TSMinerOutput;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;
import org.processmining.plugins.tsanalyzer.AnnotatedTransitionSystem;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyStateAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyTransitionAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.time.TimeStateAnnotation;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;

public class CSMMiner {
	
	public static final String processNameAttributeLabel = "process:name";
	public static final String processInitialStateAttributeLabel = "process:initialstate:";
	public static final String CSMLabel = "Composite State Machine";
	public static final String unknownPerspectiveLabel = "unknownPerspective";
	public static final String hiddenPerspectiveLabel = "hiddenPerspective";
	
	public static final int maxStates = 10000;
	
	public static Map<String, TSMinerOutput> discoverStateModels(PluginContext context, Map<String, XLog> perspectiveSubLogs,
			XLog compositeLog) {
		Map<String,TSMinerOutput> tsMinerOutputs = new HashMap<>();
		
		List<XEventClassifier> classifiers = Arrays.asList(new XEventClassifier[] { new XEventNameClassifier() });
		SMMiner miner = new SMMiner(context, false);
		TSMinerInput input = new TSMinerInput(context, compositeLog, classifiers, new XEventNameClassifier());
		input.setMaxStates(maxStates);
		tsMinerOutputs.put(CSMLabel, miner.mine(input));
		
		for (String perspectiveName : perspectiveSubLogs.keySet()) {
			input = new TSMinerInput(context, perspectiveSubLogs.get(perspectiveName), classifiers, new XEventNameClassifier());
			tsMinerOutputs.put(perspectiveName, miner.mine(input));
		}
		
		return tsMinerOutputs;
	}
	
	public static HashMap<String, AnnotatedTransitionSystem> annotateStateModels(PluginContext context, Map<String, XLog> perspectiveSubLogs,
			XLog compositeLog, Map<String, TSMinerOutput> tsMinerOutputs) {
		HashMap<String, AnnotatedTransitionSystem> annotatedTSMinerOutputs = new HashMap<>();
		
		for (String modelName : tsMinerOutputs.keySet()) {
			XLog log;
			if (modelName.equals(CSMLabel)) {
				log = compositeLog;
			}
			else {
				log = perspectiveSubLogs.get(modelName);
			}
			SMAnnotator annotator = new SMAnnotator(context, tsMinerOutputs.get(modelName).getTransitionSystem(), log, false);
			annotatedTSMinerOutputs.put(modelName, annotator.annotate());
		}
		
		return annotatedTSMinerOutputs;
	}
	
	// FIXME: why not use the composite log for this? Then skip j = 0 because this is not a transition but the initial state
	public static TransitionsCooccurringStates computePerspectiveInteractions(XLog log, List<String> perspectiveNames) {
		TransitionsCooccurringStates transitionsCooccurringStates = new TransitionsCooccurringStates();
		Map<String,String> perspectiveStates = new HashMap<>();
		// FIXME: what about transitionsCooccurringTransitions?
		
		// FIXME: dirty hacks below
		int traceCounter = 0;
		int entryCounter = 0;
		ListMultimap<String,StateEntry> perspectiveStateTimePerEntry = ArrayListMultimap.create();
		ListMultimap<String,StateEntry> perspectiveStateTimePerTrace = ArrayListMultimap.create();
		Map<String,StateEntry> perspectiveCurrentEntries = new HashMap<>();
		Table<String,String,StateEntry> perspectiveStateTraceEntries = HashBasedTable.create();
		Table<String,String,HashMap<StateEntry,Long>> cooccurringStatesTimePerEntry = HashBasedTable.create();
		Table<String,String,HashMap<StateEntry,Long>> cooccurringStatesTimePerTrace = HashBasedTable.create();
		ListMultimap<StateEntry,Pair<String,String>> cooccurringTransitionsPerEntry = ArrayListMultimap.create();
		ListMultimap<StateEntry,Pair<String,String>> cooccurringTransitionsPerTrace = ArrayListMultimap.create();
		// FIXME: end dirty hacks
		for (XTrace trace : log) {
			
			for (String perspectiveName : perspectiveNames) {
				String initialState = LogProcessor.getPerspectiveInitialState(trace, perspectiveName);
				perspectiveStates.put(perspectiveName, initialState);
			}
			
			for (int j = 0; j < trace.size(); j++) {
				XEvent event = trace.get(j);
				String eventPerspective = LogProcessor.getEventPerspective(event);
				String eventName = XConceptExtension.instance().extractName(event);
				String oldPerspectiveState = perspectiveStates.get(eventPerspective);
				
				StateCounts stateCounts = transitionsCooccurringStates.get(eventPerspective, oldPerspectiveState, eventName);
				if (stateCounts == null) {
					stateCounts = new StateCounts();
					transitionsCooccurringStates.put(eventPerspective, oldPerspectiveState, eventName, stateCounts);
				}

				String compositeFromStateName = "|";
				String compositeToStateName = "|";
				List<String> compositeCooccurringPerspectives = new ArrayList<>();

				for (String perspectiveName : perspectiveNames) {
					// FIXME: skip if perspectiveName in set of eventPerspectives? what if perspectiveName == current eventPerspective?
					// current eventPerspective count is used for total transition count
					if (perspectiveName.equals(eventPerspective)) {
						compositeToStateName += eventName + "|";
					}
					else {
						compositeToStateName += perspectiveStates.get(perspectiveName) + "|";
						compositeCooccurringPerspectives.add(perspectiveName);
					}
					
					compositeFromStateName += perspectiveStates.get(perspectiveName) + "|";
					String perspectiveState = perspectiveStates.get(perspectiveName);
					stateCounts.increment(perspectiveName, perspectiveState);
				}

				stateCounts.increment(CSMLabel, compositeFromStateName);
				
				StateCounts compositeStateCounts = transitionsCooccurringStates.get(CSMLabel, compositeFromStateName, compositeToStateName);
				if (compositeStateCounts == null) {
					compositeStateCounts = new StateCounts();
					transitionsCooccurringStates.put(CSMLabel, compositeFromStateName, compositeToStateName, compositeStateCounts);
				}
				
				for (String perspectiveName : compositeCooccurringPerspectives) {
					compositeStateCounts.increment(perspectiveName, perspectiveStates.get(perspectiveName));
				}
				
				perspectiveStates.put(eventPerspective, eventName);
				
				
				// FIXME: dirty hacks below
				if (j < trace.size() -1) {
					long eventStartTime = XTimeExtension.instance().extractTimestamp(event).getTime();
					long eventEndTime = XTimeExtension.instance().extractTimestamp(trace.get(j+1)).getTime();
					long eventDuration = eventEndTime - eventStartTime;
					
					for (String perspectiveName : perspectiveNames) {
						StateEntry entry = perspectiveStateTraceEntries.get(perspectiveName, perspectiveStates.get(perspectiveName));
						if (entry == null) {
							entry = new StateEntry(traceCounter, 0);
							perspectiveStateTraceEntries.put(perspectiveName, perspectiveStates.get(perspectiveName), entry);
							traceCounter++;
						}
						entry.duration += eventDuration;
						
						if (perspectiveName.equals(eventPerspective)) {
							// Put the perspectiveCurrentEntry if it exists in the perspectiveStateTimePerEntry 
							entry = perspectiveCurrentEntries.get(perspectiveName);
							if (entry != null) {
								perspectiveStateTimePerEntry.put(perspectiveName + "~" + oldPerspectiveState, entry);
							}
							
							entry = new StateEntry(entryCounter, eventDuration);
							perspectiveCurrentEntries.put(perspectiveName, entry);
							entryCounter++;
						}
						else {
							entry = perspectiveCurrentEntries.get(perspectiveName);
							if (entry == null) {
								entry = new StateEntry(entryCounter, 0);
								perspectiveCurrentEntries.put(perspectiveName, entry);
								entryCounter++;
							}
							entry.duration += eventDuration;
						}
					}
					
					// Calculate co-occurrence
					for (String perspectiveName : perspectiveNames) {
						for (String otherPerspectiveName : perspectiveNames) {
							if (perspectiveName.equals(otherPerspectiveName)) continue;
							String stateName = perspectiveStates.get(perspectiveName);
							String otherStateName = perspectiveStates.get(otherPerspectiveName);
							
							// Per trace
							StateEntry entry = perspectiveStateTraceEntries.get(perspectiveName, stateName);
							HashMap<StateEntry, Long> entries = cooccurringStatesTimePerTrace.get(perspectiveName + "~" + stateName,
									otherPerspectiveName + "~" + otherStateName);
							if (entries == null) {
								entries = new HashMap<StateEntry, Long>();
								cooccurringStatesTimePerTrace.put(perspectiveName + "~" + stateName,
										otherPerspectiveName + "~" + otherStateName, entries);
							}
							Long previousDuration = entries.get(entry);
							if (previousDuration == null) previousDuration = (long) 0;
							entries.put(entry, previousDuration + eventDuration);
							
							// Per entry
							entry = perspectiveCurrentEntries.get(perspectiveName);
							entries = cooccurringStatesTimePerEntry.get(perspectiveName + "~" + stateName,
									otherPerspectiveName + "~" + otherStateName);
							if (entries == null) {
								entries = new HashMap<StateEntry, Long>();
								cooccurringStatesTimePerEntry.put(perspectiveName + "~" + stateName,
										otherPerspectiveName + "~" + otherStateName, entries);
							}
							previousDuration = entries.get(entry);
							if (previousDuration == null) previousDuration = (long) 0;
							entries.put(entry, previousDuration + eventDuration);
						}
						
						// Calculate transition co-occurrence
						if ((eventPerspective + "~" + oldPerspectiveState).equals(eventPerspective + "~" + eventName)) continue;
						if (perspectiveName.equals(eventPerspective)) continue;
						StateEntry entry = perspectiveStateTraceEntries.get(perspectiveName, perspectiveStates.get(perspectiveName));
						cooccurringTransitionsPerTrace.put(entry, new Pair<String, String>(eventPerspective + "~" + oldPerspectiveState,
								eventPerspective + "~" + eventName));
						entry = perspectiveCurrentEntries.get(perspectiveName);
						cooccurringTransitionsPerEntry.put(entry, new Pair<String, String>(eventPerspective + "~" + oldPerspectiveState,
								eventPerspective + "~" + eventName));
					}
				}
				else {
					// Update final time spent per state entries
					for (String perspectiveName : perspectiveNames) {
						StateEntry entry = perspectiveCurrentEntries.get(perspectiveName);
						String perspectiveState = perspectiveStates.get(perspectiveName);
						if (perspectiveName.equals(eventPerspective)) perspectiveState = oldPerspectiveState;
						if (entry != null) perspectiveStateTimePerEntry.put(perspectiveName + "~" + perspectiveState, entry);
						
						// Calculate transition co-occurrence
						if (perspectiveName.equals(eventPerspective)) continue;
						entry = perspectiveStateTraceEntries.get(perspectiveName, perspectiveStates.get(perspectiveName));
						cooccurringTransitionsPerTrace.put(entry, new Pair<String, String>(eventPerspective + "~" + oldPerspectiveState,
								eventPerspective + "~" + eventName));
						entry = perspectiveCurrentEntries.get(perspectiveName);
						cooccurringTransitionsPerEntry.put(entry, new Pair<String, String>(eventPerspective + "~" + oldPerspectiveState,
								eventPerspective + "~" + eventName));
					}
				}
				// FIXME: end dirty hacks
			}
			
			// FIXME: dirty hacks below
			// Put all entries from perspectiveStateTraceEntries in perspectiveStateTimePerTrace
			// clear perspectiveStateTraceEntries
			for (String perspectiveName : perspectiveNames) {
				Map<String,StateEntry> stateTraceEntries = perspectiveStateTraceEntries.row(perspectiveName);
				for (String stateName : stateTraceEntries.keySet()) {
					perspectiveStateTimePerTrace.put(perspectiveName + "~" + stateName, stateTraceEntries.get(stateName));
				}
			}
			perspectiveCurrentEntries.clear();
			perspectiveStateTraceEntries.clear();
			// FIXME: end dirty hacks
		}
		
		// FIXME: dirty hacks below
		Map<String,DescriptiveStatistics> timePerEntryFast = new HashMap<>();
		Map<String,DescriptiveStatistics> timePerEntrySlow = new HashMap<>();
		ListMultimap<String,StateEntry> perspectiveStateTimePerEntryFast = ArrayListMultimap.create();
		ListMultimap<String,StateEntry> perspectiveStateTimePerEntrySlow = ArrayListMultimap.create();
		for (String state : perspectiveStateTimePerEntry.keySet()) {
			List<StateEntry> entries = perspectiveStateTimePerEntry.get(state);
			Collections.sort(entries, StateEntryComparator);
			
			int i = 0;
			for (StateEntry entry : entries) {
				if (i < (entries.size() / 2)) {
					DescriptiveStatistics fastStatistics = timePerEntryFast.get(state);
					if (fastStatistics == null) {
						fastStatistics = new DescriptiveStatistics();
						timePerEntryFast.put(state, fastStatistics);
					}
					fastStatistics.addValue(entry.duration);
					
					perspectiveStateTimePerEntryFast.put(state, entry);
					i++;
				}
				else {
					DescriptiveStatistics slowStatistics = timePerEntrySlow.get(state);
					if (slowStatistics == null) {
						slowStatistics = new DescriptiveStatistics();
						timePerEntrySlow.put(state, slowStatistics);
					}
					slowStatistics.addValue(entry.duration);
					
					perspectiveStateTimePerEntrySlow.put(state, entry);
				}
			}
		}
		Map<String,DescriptiveStatistics> timePerTraceFast = new HashMap<>();
		Map<String,DescriptiveStatistics> timePerTraceSlow = new HashMap<>();
		ListMultimap<String,StateEntry> perspectiveStateTimePerTraceFast = ArrayListMultimap.create();
		ListMultimap<String,StateEntry> perspectiveStateTimePerTraceSlow = ArrayListMultimap.create();
		for (String state : perspectiveStateTimePerTrace.keySet()) {
			List<StateEntry> entries = perspectiveStateTimePerTrace.get(state);
			Collections.sort(entries, StateEntryComparator);
			
			int i = 0;
			for (StateEntry entry : entries) {
				if (i < (entries.size() / 2)) {
					DescriptiveStatistics fastStatistics = timePerTraceFast.get(state);
					if (fastStatistics == null) {
						fastStatistics = new DescriptiveStatistics();
						timePerTraceFast.put(state, fastStatistics);
					}
					fastStatistics.addValue(entry.duration);
					
					perspectiveStateTimePerTraceFast.put(state, entry);
					i++;
				}
				else {
					DescriptiveStatistics slowStatistics = timePerTraceSlow.get(state);
					if (slowStatistics == null) {
						slowStatistics = new DescriptiveStatistics();
						timePerTraceSlow.put(state, slowStatistics);
					}
					slowStatistics.addValue(entry.duration);
					
					perspectiveStateTimePerTraceSlow.put(state, entry);
				}
			}
		}
		
		// Loop over co-occurring pairs, create slow and quick variants for entry and trace statistics
		Table<String,String,DescriptiveStatistics> cooccurringStatesTimePerTraceFast = HashBasedTable.create();
		Table<String,String,DescriptiveStatistics> cooccurringStatesTimePerTraceSlow = HashBasedTable.create();
		for (String state : cooccurringStatesTimePerTrace.rowKeySet()) {
			for (String otherState : cooccurringStatesTimePerTrace.row(state).keySet()) {
				DescriptiveStatistics fastStatistics = new DescriptiveStatistics();
				DescriptiveStatistics slowStatistics = new DescriptiveStatistics();
				cooccurringStatesTimePerTraceFast.put(state, otherState, fastStatistics);
				cooccurringStatesTimePerTraceSlow.put(state, otherState, slowStatistics);
				HashMap<StateEntry, Long> entries = cooccurringStatesTimePerTrace.get(state, otherState);
				for (StateEntry entry : entries.keySet()) {
					if (perspectiveStateTimePerTraceFast.get(state).contains(entry)) {
						fastStatistics.addValue(entries.get(entry));
					}
					else if (perspectiveStateTimePerTraceSlow.get(state).contains(entry)) {
						slowStatistics.addValue(entries.get(entry));
					}
					else {
						System.out.println("Erorr " + entry);
					}
				}
			}
		}
		
		Table<String,String,DescriptiveStatistics> cooccurringStatesTimePerEntryFast = HashBasedTable.create();
		Table<String,String,DescriptiveStatistics> cooccurringStatesTimePerEntrySlow = HashBasedTable.create();
		for (String state : cooccurringStatesTimePerEntry.rowKeySet()) {
			for (String otherState : cooccurringStatesTimePerEntry.row(state).keySet()) {
				DescriptiveStatistics fastStatistics = new DescriptiveStatistics();
				DescriptiveStatistics slowStatistics = new DescriptiveStatistics();
				cooccurringStatesTimePerEntryFast.put(state, otherState, fastStatistics);
				cooccurringStatesTimePerEntrySlow.put(state, otherState, slowStatistics);
				HashMap<StateEntry, Long> entries = cooccurringStatesTimePerEntry.get(state, otherState);
				for (StateEntry entry : entries.keySet()) {
					if (perspectiveStateTimePerEntryFast.get(state).contains(entry)) {
						fastStatistics.addValue(entries.get(entry));
					}
					else if (perspectiveStateTimePerEntrySlow.get(state).contains(entry)) {
						slowStatistics.addValue(entries.get(entry));
					}
					else {
						System.out.println("Erorr " + entry);
					}
				}
			}
		}
		
		Table<String,Pair<String,String>,Integer> cooccurringTransitionCountPerEntryFast = HashBasedTable.create();
		for (String state : perspectiveStateTimePerEntryFast.keySet()) {
			List<StateEntry> entries = perspectiveStateTimePerEntryFast.get(state);
			
			for (StateEntry entry : entries) {
				List<Pair<String, String>> transitions = cooccurringTransitionsPerEntry.get(entry);
				
				for (Pair<String, String> transition : transitions) {
					Integer count = cooccurringTransitionCountPerEntryFast.get(state, transition);
					if (count == null) count = 0;
					cooccurringTransitionCountPerEntryFast.put(state, transition, count+1);
				}
			}
		}
		Table<String,Pair<String,String>,Integer> cooccurringTransitionCountPerEntrySlow = HashBasedTable.create();
		for (String state : perspectiveStateTimePerEntrySlow.keySet()) {
			List<StateEntry> entries = perspectiveStateTimePerEntrySlow.get(state);
			
			for (StateEntry entry : entries) {
				List<Pair<String, String>> transitions = cooccurringTransitionsPerEntry.get(entry);
				
				for (Pair<String, String> transition : transitions) {
					Integer count = cooccurringTransitionCountPerEntrySlow.get(state, transition);
					if (count == null) count = 0;
					cooccurringTransitionCountPerEntrySlow.put(state, transition, count+1);
				}
			}
		}
		Table<String,Pair<String,String>,Integer> cooccurringTransitionCountPerTraceFast = HashBasedTable.create();
		for (String state : perspectiveStateTimePerTraceFast.keySet()) {
			List<StateEntry> entries = perspectiveStateTimePerTraceFast.get(state);
			
			for (StateEntry entry : entries) {
				List<Pair<String, String>> transitions = cooccurringTransitionsPerTrace.get(entry);
				
				for (Pair<String, String> transition : transitions) {
					Integer count = cooccurringTransitionCountPerTraceFast.get(state, transition);
					if (count == null) count = 0;
					cooccurringTransitionCountPerTraceFast.put(state, transition, count+1);
				}
			}
		}
		Table<String,Pair<String,String>,Integer> cooccurringTransitionCountPerTraceSlow = HashBasedTable.create();
		for (String state : perspectiveStateTimePerTraceSlow.keySet()) {
			List<StateEntry> entries = perspectiveStateTimePerTraceSlow.get(state);
			
			for (StateEntry entry : entries) {
				List<Pair<String, String>> transitions = cooccurringTransitionsPerTrace.get(entry);
				
				for (Pair<String, String> transition : transitions) {
					Integer count = cooccurringTransitionCountPerTraceSlow.get(state, transition);
					if (count == null) count = 0;
					cooccurringTransitionCountPerTraceSlow.put(state, transition, count+1);
				}
			}
		}
		
		transitionsCooccurringStates.timePerTraceFast = timePerTraceFast;
		transitionsCooccurringStates.timePerTraceSlow = timePerTraceSlow;
		transitionsCooccurringStates.timePerEntryFast = timePerEntryFast;
		transitionsCooccurringStates.timePerEntrySlow = timePerEntrySlow;
		transitionsCooccurringStates.cooccurringStatesTimePerTraceFast = cooccurringStatesTimePerTraceFast;
		transitionsCooccurringStates.cooccurringStatesTimePerTraceSlow = cooccurringStatesTimePerTraceSlow;
		transitionsCooccurringStates.cooccurringStatesTimePerEntryFast = cooccurringStatesTimePerEntryFast;
		transitionsCooccurringStates.cooccurringStatesTimePerEntrySlow = cooccurringStatesTimePerEntrySlow;
		transitionsCooccurringStates.cooccurringTransitionCountPerTraceFast = cooccurringTransitionCountPerTraceFast;
		transitionsCooccurringStates.cooccurringTransitionCountPerTraceSlow = cooccurringTransitionCountPerTraceSlow;
		transitionsCooccurringStates.cooccurringTransitionCountPerEntryFast = cooccurringTransitionCountPerEntryFast;
		transitionsCooccurringStates.cooccurringTransitionCountPerEntrySlow = cooccurringTransitionCountPerEntrySlow;
		System.out.println();
		// FIXME: end dirty hacks
		
		return transitionsCooccurringStates;
	}
	
	public static StateMap createStateMapping(List<String> perspectiveNames, Map<String, TSMinerOutput> tsMinerOutputs) {
		StateMap stateMap = new StateMap();
		TSMinerTransitionSystem compositeModel = tsMinerOutputs.get(CSMLabel).getTransitionSystem();
		
		for (String perspectiveName : perspectiveNames) {
			int perspectiveIndex = perspectiveNames.indexOf(perspectiveName);
			TSMinerTransitionSystem stateModel = tsMinerOutputs.get(perspectiveName).getTransitionSystem();
			
			for (State compositeState : compositeModel.getNodes()) {
				String[] compositeStateName = compositeState.getIdentifier().toString().split("\\|");
				if (compositeStateName.length < perspectiveNames.size() + 2) continue;
				
				for (State state : stateModel.getNodes()) {
					String stateIdentifier = state.getIdentifier().toString();
					String stateName = stateIdentifier.substring(1, stateIdentifier.length()-1);
					
					if (compositeStateName[perspectiveIndex + 1].equals(stateName)) {
						stateMap.add(CSMLabel, compositeState.getIdentifier(), perspectiveName, state.getIdentifier());
						stateMap.add(perspectiveName, state.getIdentifier(), CSMLabel, compositeState.getIdentifier());
					}
				}
			}
		}
		
		return stateMap;
	}
	
	public static TransitionMap createTransitionMapping(List<String> perspectiveNames, Map<String, TSMinerOutput> tsMinerOutputs) {
		TransitionMap transitionMap = new TransitionMap();
		TSMinerTransitionSystem compositeModel = tsMinerOutputs.get(CSMLabel).getTransitionSystem();
		
		for (String perspectiveName : perspectiveNames) {
			int perspectiveIndex = perspectiveNames.indexOf(perspectiveName);
			TSMinerTransitionSystem stateModel = tsMinerOutputs.get(perspectiveName).getTransitionSystem();
			
			for (Transition compositeTransition : compositeModel.getEdges()) {
				String[] compositeSourceName = compositeTransition.getSource().getIdentifier().toString().split("\\|");
				if (compositeSourceName.length < perspectiveNames.size() + 2) continue;
				String[] compositeTargetName = compositeTransition.getTarget().getIdentifier().toString().split("\\|");
				if (compositeTargetName.length < perspectiveNames.size() + 2) continue;
				
				for (Transition transition : stateModel.getEdges()) {
					String sourceIdentifier = transition.getSource().getIdentifier().toString();
					String targetIdentifier = transition.getTarget().getIdentifier().toString();
					String sourceName = sourceIdentifier.substring(1, sourceIdentifier.length()-1);
					String targetName = targetIdentifier.substring(1, targetIdentifier.length()-1);
					
					if (compositeSourceName[perspectiveIndex + 1].equals(sourceName) &&
							compositeTargetName[perspectiveIndex + 1].equals(targetName)) {
						transitionMap.add(compositeTransition, perspectiveName, transition);
						transitionMap.add(transition, CSMLabel, compositeTransition);
					}
				}
			}
		}
		
		return transitionMap;
	}

	public static void calculateRelationStatistics(CSMMinerResults results) {
		for (String modelName : results.tsMinerOutputs.keySet()) {
			calculateTotalStatistics(results, modelName);
			
			TSMinerTransitionSystem model = results.tsMinerOutputs.get(modelName).getTransitionSystem();
			for (State state : model.getNodes()) {
				if (!modelName.equals(CSMMiner.CSMLabel)) {
					calculateStateStatistics(results, modelName, state);
				}
				else if (!state.getIdentifier().toString().equals("[]") && !state.getIdentifier().toString().equals("[|]")) {
					results.stateCounts.put(state, (int) results.annotatedTSMinerOutputs.get(CSMMiner.CSMLabel).
							getFrequency_StateAnnotation(state).getObservations().getSum());
					results.stateSojourns.put(state, results.annotatedTSMinerOutputs.get(CSMMiner.CSMLabel).
							getTime_StateAnnotation(state).getSoujourn().getSum());
				}
			}
		}
		
		// FIXME: continue from here
		
		// Calculate interestingness metrics
		for (String modelName1 : results.tsMinerOutputs.keySet()) {
			if (modelName1.equals(CSMMiner.CSMLabel)) continue;
			
			for (String modelName2 : results.tsMinerOutputs.keySet()) {
				if (modelName2.equals(CSMMiner.CSMLabel) || modelName2.equals(modelName1)) continue;
				
				TSMinerTransitionSystem model1 = results.tsMinerOutputs.get(modelName1).getTransitionSystem();
				TSMinerTransitionSystem model2 = results.tsMinerOutputs.get(modelName2).getTransitionSystem();
				
				for (State state1 : model1.getNodes()) {
					for (State state2 : model2.getNodes()) {
						if (results.sharedStateSojourns.get(new Pair<>(state1, state2)) == null) continue;
						if (results.stateSojourns.get(state1) == 0 || results.stateSojourns.get(state2) == 0) continue;
						
						//FIXME: try with epsilon
						calculateStateInterestingnessMetrics(results, state1, state2);
					}
				}
				
				for (Transition transition : model1.getEdges()) {
					for (State state : model2.getNodes()) {
						if (!results.transitionsCooccurringStates.containsCount(modelName1, transition, modelName2, state)) continue;
						if (results.stateSojourns.get(state) == 0) continue;
						
						calculateTransitionInterestingnessMetrics(results, modelName1, transition, modelName2, state);
						calculateForwardInterestingnessMetrics(results, modelName1, transition, modelName2, state);
					}
				}
			}
		}
	}
	
	public static void calculateTotalStatistics(CSMMinerResults results, String modelName) {
		int totalTransitionCount = 0;
		for (FrequencyTransitionAnnotation annotation : results.annotatedTSMinerOutputs.get(modelName).getFrequencyAnnotation().
				getAllTransitionAnnotations()) {
			if (annotation.getTransition().getSource().getAttributeMap().get(AttributeMap.TOOLTIP).equals("[]")) continue;
			totalTransitionCount += annotation.getObservations().getSum();
		}
		results.totalTransitionCounts.put(modelName, totalTransitionCount);
		
		int totalStateCount = 0;
		for (FrequencyStateAnnotation annotation : results.annotatedTSMinerOutputs.get(modelName).getFrequencyAnnotation().
				getAllStateAnnotations()) {
			if (annotation.getState().getIdentifier().toString().equals("[]")) continue;
			totalStateCount += annotation.getObservations().getSum();
		}
		results.totalStateCounts.put(modelName, totalStateCount);
		
		float totalStateSojourn = 0;
		for (TimeStateAnnotation annotation : results.annotatedTSMinerOutputs.get(modelName).getTimeAnnotation().
				getAllStateAnnotations()) {
			if (annotation.getSoujourn().isEmpty()) continue;
			totalStateSojourn += annotation.getSoujourn().getSum();
		}
		results.totalStateSojourns.put(modelName, totalStateSojourn);
	}
	
	public static void calculateStateStatistics(CSMMinerResults results, String perspectiveName, State state) {
		TSMinerTransitionSystem compositeModel = results.tsMinerOutputs.get(CSMMiner.CSMLabel).getTransitionSystem();
		Set<Pair<String,Object>> compositeStates = results.stateMap.getMappedStates(perspectiveName, state.getIdentifier());
		results.stateCounts.put(state, 0);
		results.stateSojourns.put(state, 0f);

		if (compositeStates != null) {
			for (Pair<String, Object> pair : compositeStates) {
				// Calculate per composite state and totals per state
				State compositeState = compositeModel.getNode(pair.getSecond());
				FrequencyStateAnnotation frequencyAnnotation = results.annotatedTSMinerOutputs.get(CSMMiner.CSMLabel).
						getFrequency_StateAnnotation(compositeState);
				TimeStateAnnotation timeAnnotation = results.annotatedTSMinerOutputs.get(CSMMiner.CSMLabel).
						getTime_StateAnnotation(compositeState);

				results.stateCounts.put(state, results.stateCounts.get(state) + (int) frequencyAnnotation.getObservations().getSum());
				results.stateSojourns.put(state, results.stateSojourns.get(state) + timeAnnotation.getSoujourn().getSum());
				results.sharedStateCounts.put(new Pair<State, State>(state, compositeState),
						(int) frequencyAnnotation.getObservations().getSum());
				results.sharedStateSojourns.put(new Pair<State, State>(state, compositeState), timeAnnotation.getSoujourn().getSum());

				// Calculate per state in other perspectives
				for (Pair<String, Object> otherPair : results.stateMap.getMappedStates(CSMMiner.CSMLabel, pair.getSecond())) {
					State otherState = results.tsMinerOutputs.get(otherPair.getFirst()).getTransitionSystem().
							getNode(otherPair.getSecond());

					if (!otherState.equals(state)) {
						Pair<State, State> newPair = new Pair<State, State>(state, otherState);

						if (!results.sharedStateCounts.containsKey(newPair)) {
							results.sharedStateCounts.put(newPair, 0);
							results.sharedStateSojourns.put(newPair, 0f);
						}

						results.sharedStateCounts.put(newPair, results.sharedStateCounts.get(newPair) +
								(int) frequencyAnnotation.getObservations().getSum());
						results.sharedStateSojourns.put(newPair, results.sharedStateSojourns.get(newPair) +
								timeAnnotation.getSoujourn().getSum());
					}
				}
			}
		}
	}
	
	/*
	 * Lift: 0 = never seen together, 1 = independent, infinity = much more often seen together than expected, (0,infinity)
	 * Conviction: 0 = state2 always true?, 1 = independent, infinity = always true, (0,infinity)
	 * Cosine: 0 = no co-occurrence, geometric mean of lift and support, (0,1)?
	 * Jaccard: 1 = intersection equal to union = always co-occur, 0 = never co-occur, (0,1)
	 * Phi: 0 = independence, higher is stronger relation, (-1,1) approximately
	 */
	public static void calculateStateInterestingnessMetrics(CSMMinerResults results, State state1, State state2) {
		Pair<Object, Object> pair = new Pair<Object,Object>(state1, state2);
		float supportShared = results.sharedStateSojourns.get(pair) / results.totalStateSojourns.get(CSMMiner.CSMLabel);
		float supportState1 = results.stateSojourns.get(state1) / results.totalStateSojourns.get(CSMMiner.CSMLabel);
		float supportState2 = results.stateSojourns.get(state2) / results.totalStateSojourns.get(CSMMiner.CSMLabel);
		float confidence = results.sharedStateSojourns.get(pair) / results.stateSojourns.get(state1);
		
		float lift = confidence / supportState2;
		float conviction = (1 - supportState2) / (1 - confidence);
		float cosine = (float) (supportShared / (Math.sqrt(supportState1 * supportState2)));
		float jaccard = supportShared / (supportState1 + supportState2 - supportShared);
		float phi = (float) ((supportShared - supportState1 * supportState2) /
				(Math.sqrt(supportState1 * supportState2 * (1 - supportState1) * (1 - supportState2))));
		
		results.support.put(pair, supportShared);
		results.confidence.put(pair, confidence);
		results.lift.put(pair, lift);
		results.conviction.put(pair, conviction);
		results.cosine.put(pair, cosine);
		results.jaccard.put(pair, jaccard);
		results.phi.put(pair, phi);
	}
	
	public static void calculateTransitionInterestingnessMetrics(CSMMinerResults results, String transitionPerspective,
			Transition transition, String statePerspective, State state) {
		Pair<Object, Object> pair = new Pair<Object, Object>(transition, state);
		FrequencyTransitionAnnotation annotation = results.annotatedTSMinerOutputs.get(transitionPerspective).
				getFrequency_TransitionAnnotation(transition);
		
		int transitionCooccurringCount = results.transitionsCooccurringStates.getCount(
				transitionPerspective, transition, statePerspective, state);
		float confidence = transitionCooccurringCount / annotation.getObservations().getSum();
		float supportTransition = annotation.getObservations().getSum() / results.totalTransitionCounts.get(transitionPerspective);
		float supportState = results.stateSojourns.get(state) / results.totalStateSojourns.get(CSMMiner.CSMLabel);
		
		float supportShared = confidence * supportTransition;
		float lift = confidence / supportState;
		float conviction = (1 - supportState) / (1 - confidence);
		float cosine = (float) (supportShared / (Math.sqrt(supportTransition * supportState)));
		float jaccard = supportShared / (supportTransition + supportState - supportShared);
		float phi = (float) ((supportShared - supportTransition * supportState) /
				(Math.sqrt(supportTransition * supportState * (1 - supportTransition) * (1 - supportState))));
		
		results.support.put(pair, supportShared);
		results.confidence.put(pair, confidence);
		results.lift.put(pair, lift);
		results.conviction.put(pair, conviction);
		results.cosine.put(pair, cosine);
		results.jaccard.put(pair, jaccard);
		results.phi.put(pair, phi);
	}
	
	public static void calculateForwardInterestingnessMetrics(CSMMinerResults results, String transitionPerspective,
			Transition transition, String statePerspective, State state) {
		Pair<Object, Object> pair = new Pair<Object, Object>(state, transition);
		FrequencyTransitionAnnotation annotation = results.annotatedTSMinerOutputs.get(transitionPerspective).
				getFrequency_TransitionAnnotation(transition);
		TSMinerTransitionSystem model = results.tsMinerOutputs.get(transitionPerspective).getTransitionSystem();
		
		float outgoingTotalCount = 0.0f;
		float outgoingConditionalTotalCount = 0.0f;
		for (Transition otherTransition : model.getOutEdges(transition.getSource())) {
			outgoingTotalCount += results.annotatedTSMinerOutputs.get(transitionPerspective).
					getFrequency_TransitionAnnotation(otherTransition).getObservations().getSum();
			if (results.transitionsCooccurringStates.containsCount(transitionPerspective, otherTransition, statePerspective, state)) {
				outgoingConditionalTotalCount += results.transitionsCooccurringStates.getCount(transitionPerspective, otherTransition,
						statePerspective, state);
			}
		}
		
		int transitionCooccurringCount = results.transitionsCooccurringStates.getCount(
				transitionPerspective, transition, statePerspective, state);
		float confidence = transitionCooccurringCount / outgoingConditionalTotalCount;
		float supportTransition = annotation.getObservations().getSum() / outgoingTotalCount;
		float supportState = outgoingConditionalTotalCount / outgoingTotalCount;
		
		float supportShared = confidence * supportState;
		float lift = confidence / supportTransition;
		float conviction = (1 - supportTransition) / (1 - confidence);
		float cosine = (float) (supportShared / (Math.sqrt(supportTransition * supportState)));
		float jaccard = supportShared / (supportTransition + supportState - supportShared);
		float phi = (float) ((supportShared - supportTransition * supportState) /
				(Math.sqrt(supportTransition * supportState * (1 - supportTransition) * (1 - supportState))));
		
//		BinomialDistribution distribution = new BinomialDistribution(null, (int) outgoingTotalCount, supportTransition * supportState);
//		float significance = (float) distribution.cumulativeProbability(transitionCooccurringCount - 1);
		
		results.support.put(pair, supportShared);
		results.confidence.put(pair, confidence);
		results.lift.put(pair, lift);
		results.conviction.put(pair, conviction);
		results.cosine.put(pair, cosine);
		results.jaccard.put(pair, jaccard);
		results.phi.put(pair, phi);
//		results.significance.put(pair, significance);
	}
	
	private static Comparator<StateEntry> StateEntryComparator = new Comparator<StateEntry>() {

		public int compare(StateEntry o1, StateEntry o2) {
			if (o1.duration > o2.duration) {
				return 1;
			}
			else if (o1.duration < o2.duration) {
				return -1;
			}
			else { //if (o1.duration == o2.duration) {
				return 0;
			}
		}
		
	};
	
}