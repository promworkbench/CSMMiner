package org.processmining.csmminer.relations;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.transitionsystem.State;

public class StateCounts {
	private Map<Pair<String,String>,Integer> counts;
	
	public StateCounts() {
		counts = new HashMap<>();
	}
	
	public void increment(String perspectiveName, String stateName) {
		Pair<String,String> perspectiveState = new Pair<>(perspectiveName, stateName);
		
		if (counts.containsKey(perspectiveState)) {
			counts.put(perspectiveState, counts.get(perspectiveState)+1);
		}
		else {
			counts.put(perspectiveState, 1);
		}
	}
	
	public Integer getCount(Pair<String, String> perspectiveState) {
		return counts.get(perspectiveState);
	}
	
	public Integer getCount(String perspectiveName, State state) {
		return getCount(perspectiveState(perspectiveName, state));
	}
	
	public boolean containsCount(String perspectiveName, State state) {
		Pair<String,String> perspectiveState = perspectiveState(perspectiveName, state);
		return counts.containsKey(perspectiveState);
	}
	
	public Set<Pair<String, String>> getStates() {
		return counts.keySet();
	}

	public String toString() {
		return "StateCounts [counts=" + counts + "]";
	}
	
	private Pair<String,String> perspectiveState(String perspectiveName, State state) {
		String stateIdentifier = state.getIdentifier().toString();
		String stateName = stateIdentifier.substring(1, stateIdentifier.length()-1);
		return new Pair<>(perspectiveName, stateName);
	}
}