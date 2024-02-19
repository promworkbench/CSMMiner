package org.processmining.csmminer.relations;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;

import com.google.common.collect.Table;

public class TransitionsCooccurringStates {
	private Map<PerspectiveTransition,StateCounts> transitionsCooccurringStates;
	
	public TransitionsCooccurringStates() {
		transitionsCooccurringStates = new HashMap<>();
	}

	public StateCounts get(String perspectiveName, String fromState, String toState) {
		return transitionsCooccurringStates.get(new PerspectiveTransition(perspectiveName, fromState, toState));
	}
	
	public StateCounts get(String perspectiveName, Transition transition) {
		String source = transition.getSource().getIdentifier().toString();
		String target = transition.getTarget().getIdentifier().toString();
		PerspectiveTransition perspectiveTransition = new PerspectiveTransition(perspectiveName,
				source.substring(1, source.length()-1),	target.substring(1, target.length()-1));
		
		return transitionsCooccurringStates.get(perspectiveTransition);
	}
	
	public Integer getCount(String transitionPerspective, Transition transition, String statePerspective, State state) {
		StateCounts stateCounts = get(transitionPerspective, transition);
		if (stateCounts == null) return null;
		
		return stateCounts.getCount(statePerspective, state);
	}
	
	public StateCounts put(String perspectiveName, String fromState, String toState, StateCounts stateCounts) {
		return transitionsCooccurringStates.put(new PerspectiveTransition(perspectiveName, fromState, toState), stateCounts);
	}

	public boolean containsCount(String transitionPerspective, Transition transition, String statePerspective, State state) {
		StateCounts stateCounts = get(transitionPerspective, transition);
		if (stateCounts == null) return false;
		
		return stateCounts.containsCount(statePerspective, state);
	}
	
	
	// FIXME: dirty hacks below
	public Map<String, DescriptiveStatistics> timePerTraceFast;
	public Map<String, DescriptiveStatistics> timePerTraceSlow;
	public Map<String, DescriptiveStatistics> timePerEntryFast;
	public Map<String, DescriptiveStatistics> timePerEntrySlow;
	public Table<String, String, DescriptiveStatistics> cooccurringStatesTimePerTraceFast;
	public Table<String, String, DescriptiveStatistics> cooccurringStatesTimePerTraceSlow;
	public Table<String, String, DescriptiveStatistics> cooccurringStatesTimePerEntryFast;
	public Table<String, String, DescriptiveStatistics> cooccurringStatesTimePerEntrySlow;
	public Table<String, Pair<String, String>, Integer> cooccurringTransitionCountPerTraceFast;
	public Table<String, Pair<String, String>, Integer> cooccurringTransitionCountPerTraceSlow;
	public Table<String, Pair<String, String>, Integer> cooccurringTransitionCountPerEntryFast;
	public Table<String, Pair<String, String>, Integer> cooccurringTransitionCountPerEntrySlow;
}
