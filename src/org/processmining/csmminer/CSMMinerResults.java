package org.processmining.csmminer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.model.XLog;
import org.processmining.csmminer.relations.StateMap;
import org.processmining.csmminer.relations.TransitionMap;
import org.processmining.csmminer.relations.TransitionsCooccurringStates;
import org.processmining.csmminer.visualisation.VisualisationConnections;
import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.plugins.transitionsystem.miner.TSMinerOutput;
import org.processmining.plugins.tsanalyzer.AnnotatedTransitionSystem;

public class CSMMinerResults {
	public Map<String, TSMinerOutput> tsMinerOutputs;
	public Map<String, AnnotatedTransitionSystem> annotatedTSMinerOutputs;
	public TransitionsCooccurringStates transitionsCooccurringStates;
	public StateMap stateMap;
	public TransitionMap transitionMap;
	public XLog log;
	// FIXME: remove private logs?
	private XLog compositeLog;
	private Map<String, XLog> perspectiveSubLogs;
	public long computationTime;
	public List<String> perspectiveNames;
	
	public VisualisationConnections connections;
	
	public Map<String, Integer> totalTransitionCounts = new HashMap<>();
	public Map<String, Integer> totalStateCounts = new HashMap<>();
	public Map<String, Float> totalStateSojourns = new HashMap<>();
	public Map<State,Integer> stateCounts = new HashMap<>();
	public Map<State,Float> stateSojourns = new HashMap<>();
	public Map<Pair<State,State>,Integer> sharedStateCounts = new HashMap<>();
	public Map<Pair<State,State>,Float> sharedStateSojourns = new HashMap<>();
	public Map<Pair<State,State>,Integer> sharedStateFrequencies = new HashMap<>();
	
	public Map<Pair<Object,Object>,Float> support = new HashMap<>();
	public Map<Pair<Object,Object>,Float> confidence = new HashMap<>();
	public Map<Pair<Object,Object>,Float> lift = new HashMap<>();
	public Map<Pair<Object,Object>,Float> conviction = new HashMap<>();
	public Map<Pair<Object,Object>,Float> cosine = new HashMap<>();
	public Map<Pair<Object,Object>,Float> jaccard = new HashMap<>();
	public Map<Pair<Object,Object>,Float> phi = new HashMap<>();
	public Map<Pair<Object,Object>,Float> significance = new HashMap<>();

	public CSMMinerResults(Map<String, TSMinerOutput> tsMinerOutputs, HashMap<String, AnnotatedTransitionSystem> annotatedTSMinerOutputs,
			TransitionsCooccurringStates transitionsCooccurringStates, StateMap stateMap, TransitionMap transitionMap, XLog log,
			XLog compositeLog, Map<String, XLog> perspectiveSubLogs, long computationTime, List<String> perspectiveNames) {
		this.tsMinerOutputs = tsMinerOutputs;
		this.annotatedTSMinerOutputs = annotatedTSMinerOutputs;
		this.transitionsCooccurringStates = transitionsCooccurringStates;
		this.stateMap = stateMap;
		this.transitionMap = transitionMap;
		this.log = log;
		this.compositeLog = compositeLog;
		this.perspectiveSubLogs = perspectiveSubLogs;
		this.computationTime = computationTime;
		this.perspectiveNames = perspectiveNames;
	}
	
	public CSMMinerResults(XLog log) {
		this.log = log;
	}

	public void print() {
		for (String modelName : tsMinerOutputs.keySet()) {
			System.out.println(modelName + ": " + tsMinerOutputs.get(modelName));
		}
	}

}
