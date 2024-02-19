package org.processmining.csmminer.relations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;

public class TransitionMap {
	public Map<Transition,Set<Pair<String,Transition>>> map;
	
	public TransitionMap() {
		map = new HashMap<>();
	}
	
	public void add(Transition transition, String processName, Transition otherTransition) {
		Set<Pair<String,Transition>> set;
		if (map.containsKey(transition)) {
			set = map.get(transition);
		}
		else {
			set = new HashSet<>();
			map.put(transition, set);
		}
		
		set.add(new Pair<>(processName, otherTransition));
	}
	
	public Set<Pair<String, Transition>> getMappedTransitions(Transition transition) {
		return map.get(transition);
	}
}
