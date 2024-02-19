package org.processmining.csmminer.relations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.framework.util.Pair;

public class StateMap {
	public Map<Pair<String,Object>,Set<Pair<String,Object>>> map;
	public Map<Pair<String,String>,Object> stateIdentifiers;
	
	public StateMap() {
		map = new HashMap<>();
		stateIdentifiers = new HashMap<>();
	}
	
	public void add(String processName, Object stateIdentifier, String otherProcessName, Object otherStateIdentifier) {
		Pair<String,Object> processStateIdentifier = new Pair<>(processName, stateIdentifier);
		
		Set<Pair<String,Object>> set;
		if (map.containsKey(processStateIdentifier)) {
			set = map.get(processStateIdentifier);
		}
		else {
			set = new HashSet<>();
			map.put(processStateIdentifier, set);
		}
		
		set.add(new Pair<>(otherProcessName, otherStateIdentifier));
		
		// Also store the stateIdentifiers
		String stateName = stateIdentifier.toString();
		String otherStateName = otherStateIdentifier.toString();
		Pair<String,String> processStateName = new Pair<>(processName, stateName.substring(1,stateName.length()-1));
		Pair<String,String> otherProcessStateName = new Pair<>(otherProcessName, otherStateName.substring(1, otherStateName.length()-1));
		stateIdentifiers.put(processStateName, stateIdentifier);
		stateIdentifiers.put(otherProcessStateName, otherStateIdentifier);
	}
	
	public Set<Pair<String, Object>> getMappedStates(String processName, Object stateIdentifier) {
		Pair<String,Object> processStateIdentifier = new Pair<>(processName, stateIdentifier);
		
		return map.get(processStateIdentifier);
	}
	
	public Object getIdentifier(String processName, String stateName) {
		return stateIdentifiers.get(new Pair<>(processName, stateName));
	}
}
