package org.processmining.csmminer;

import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.connections.transitionsystem.TransitionSystemConnection;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.DirectedGraphElementWeights;
import org.processmining.models.graphbased.directed.transitionsystem.AcceptStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.StartStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.payload.event.EventPayloadTransitionSystem;
import org.processmining.plugins.transitionsystem.converter.TSConverterInput;
import org.processmining.plugins.transitionsystem.converter.TSConverterOutput;
import org.processmining.plugins.transitionsystem.converter.util.TSConversions;
import org.processmining.plugins.transitionsystem.miner.TSMinerPayloadHandler;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;

public class SMConverter {
	
	/**
	 * The context of this converter.
	 */
	private final PluginContext context;

	/**
	 * Creates a converter, given its context.
	 * 
	 * @param context
	 *            Its context.
	 */
	public SMConverter(final PluginContext context) {
		this.context = context;
	}

	/**
	 * Converts a transition system according to the given settings.
	 * 
	 * @param settings
	 *            The given settings, which includes the transition system.
	 * @return The conversion result, which includes the converted transition
	 *         system.
	 */
	public TSConverterOutput convert(final TSConverterInput settings, boolean createConnection) {
		EventPayloadTransitionSystem ts = settings.getTransitionSystem();

		int oldSize;
		boolean done = false;

		/**
		 * Repeat converting the transition system until no conversion yields
		 * results any more.
		 */
		while (!done) {
			done = true;
			/**
			 * If applicable, remove self loops. Reduces number of edges on
			 * actual removal.
			 */
			if (settings.getUse(TSConversions.KILLSELFLOOPS)) {
				oldSize = ts.getEdges().size();
				ts = killSelfLoops(context, settings);
				if (oldSize != ts.getEdges().size()) {
					/**
					 * Some edges got removed.
					 */
					done = false;
				}
			}
			/**
			 * If applicable, improve the diamond structure. Increases number of
			 * edges on actual improvement.
			 */
			if (settings.getUse(TSConversions.EXTEND)) {
				oldSize = ts.getEdges().size();
				ts = improveDiamondStructure(context, settings);
				if (oldSize != ts.getEdges().size()) {
					/**
					 * Some edges got added.
					 */
					done = false;
				}
			}
			/**
			 * If applicable, merge states if outputs are identical. Reduces
			 * number of states on actual merge.
			 */
			if (settings.getUse(TSConversions.MERGEBYOUTPUT)) {
				oldSize = ts.getNodes().size();
				ts = merge(settings, true);
				if (oldSize != ts.getNodes().size()) {
					/**
					 * Some states got merged.
					 */
					done = false;
				}
			}
			/**
			 * If applicable, merge states if inputs are identical. Reduces
			 * number of states on actual merge.
			 */
			if (settings.getUse(TSConversions.MERGEBYINPUT)) {
				oldSize = ts.getNodes().size();
				ts = merge(settings, false);
				if (oldSize != ts.getNodes().size()) {
					/**
					 * Some states got merged.
					 */
					done = false;
				}
			}
		}

		if (createConnection) {
			context.addConnection(new TransitionSystemConnection(settings.getTransitionSystem(), settings.getWeights(),
					settings.getStarts(), settings.getAccepts()));
		}

		return new TSConverterOutput(settings);
	}

	public TSConverterOutput convert(final TSConverterInput settings) {
		return convert(settings, true);
	}

	/**
	 * Returns a copy of the given transition system that does not contain self
	 * loops.
	 * 
	 * @param ts
	 *            The given transition system.
	 * @return The resulting transition system.
	 */
	private static TSMinerTransitionSystem killSelfLoops(PluginContext context, TSConverterInput settings) {
		TSMinerTransitionSystem ts = settings.getTransitionSystem();
		TSMinerTransitionSystem newTs = new TSMinerTransitionSystem(ts.getLabel(),
				(TSMinerPayloadHandler) ts.getPayloadHanlder());
		newTs.addProxyMap(ts);
		DirectedGraphElementWeights weights = settings.getWeights();
		DirectedGraphElementWeights newWeights = new DirectedGraphElementWeights();

		/**
		 * Copy all edges together with their states.
		 */
		for (Transition transition : ts.getEdges()) {
			State fromState = transition.getSource();
			State toState = transition.getTarget();
			/**
			 * Check whether self loop.
			 */
			if (fromState != toState) {
				/**
				 * No self loop. Copy.
				 */
				if (newTs.addState(fromState.getIdentifier())) {
					newWeights.add(fromState.getIdentifier(), weights.get(fromState.getIdentifier(), 1));
					copyAttributes(newTs, ts, fromState.getIdentifier());
				}
				if (newTs.addState(toState.getIdentifier())) {
					newWeights.add(toState.getIdentifier(), weights.get(toState.getIdentifier(), 1));
					copyAttributes(newTs, ts, toState.getIdentifier());
				}
				if (newTs.addTransition(fromState.getIdentifier(), toState.getIdentifier(), transition.getIdentifier())) {
					newWeights.add(fromState.getIdentifier(), toState.getIdentifier(), transition.getIdentifier(),
							weights.get(fromState.getIdentifier(), toState.getIdentifier(), transition.getIdentifier(),
									1));
				}
			}
		}

		/**
		 * Replace the old with the new. Note that start and accept payloads are
		 * not affected by this conversion.
		 */
		settings.setTransitionSystem(newTs);
		settings.setWeights(newWeights);
		return newTs;
	}

	/**
	 * Using this method leads to problems with the annotation of non-existing self-loops.
	 */
//	private static TSMinerTransitionSystem killSelfLoopsByRemoval(PluginContext context, TSConverterInput settings) {
//		TSMinerTransitionSystem ts = settings.getTransitionSystem();
//		Set<Transition> selfLoops = new HashSet<Transition>();
//		
//		for (Transition transition : ts.getEdges()) {
//			State fromState = transition.getSource();
//			State toState = transition.getTarget();
//			if (fromState == toState) {
//				selfLoops.add(transition);
//			}
//		}
//
//		for (Transition transition : selfLoops) {
//			ts.removeTransition(transition.getSource().getIdentifier(), transition.getTarget().getIdentifier(), transition.getIdentifier());
//			settings.getWeights().remove(transition);
//		}
//		
//		return ts;
//	}

	/**
	 * Preserve some settings from the old state in the new state.
	 * 
	 * @param newTs
	 *            The new transitions system.
	 * @param oldTs
	 *            The old transition system.
	 * @param identifier
	 *            The identifier of the state in both systems.
	 */
	private static void copyAttributes(TSMinerTransitionSystem newTs, TSMinerTransitionSystem oldTs, Object identifier) {
		newTs.getNode(identifier).getAttributeMap()
				.put(AttributeMap.LABEL, oldTs.getNode(identifier).getAttributeMap().get(AttributeMap.LABEL));
		newTs.getNode(identifier).getAttributeMap()
				.put(AttributeMap.TOOLTIP, oldTs.getNode(identifier).getAttributeMap().get(AttributeMap.TOOLTIP));
	}

	/**
	 * Returns a copy of the given transition system with improved diamond
	 * structure.
	 * 
	 * @param ts
	 *            The given transition system.
	 * @return The resulting transition system.
	 */
	private static TSMinerTransitionSystem improveDiamondStructure(PluginContext context, TSConverterInput settings) {
		TSMinerTransitionSystem ts = settings.getTransitionSystem();
		TSMinerTransitionSystem newTs = new TSMinerTransitionSystem(ts.getLabel(),
				(TSMinerPayloadHandler) ts.getPayloadHanlder());
		newTs.addProxyMap(ts);
		DirectedGraphElementWeights weights = settings.getWeights();
		DirectedGraphElementWeights newWeights = new DirectedGraphElementWeights();

		/**
		 * First, copy all states and edges.
		 */
		for (Transition transition : ts.getEdges()) {
			State fromState = transition.getSource();
			State toState = transition.getTarget();
			if (newTs.addState(fromState.getIdentifier())) {
				newWeights.add(fromState.getIdentifier(), weights.get(fromState.getIdentifier(), 1));
				copyAttributes(newTs, ts, fromState.getIdentifier());
			}
			if (newTs.addState(toState.getIdentifier())) {
				newWeights.put(toState.getIdentifier(), weights.get(toState.getIdentifier(), 1));
				copyAttributes(newTs, ts, toState.getIdentifier());
			}
			if (newTs.addTransition(fromState.getIdentifier(), toState.getIdentifier(), transition.getIdentifier())) {
				newWeights.add(fromState.getIdentifier(), toState.getIdentifier(), transition.getIdentifier(),
						weights.get(fromState.getIdentifier(), toState.getIdentifier(), transition.getIdentifier(), 1));
			}
		}
		/**
		 * Second, check whether we can improve the diamond structure by adding
		 * edges.
		 */
		for (Transition northEastTransition : ts.getEdges()) {
			State northState = northEastTransition.getSource();
			State eastState = northEastTransition.getTarget();
			if (eastState == northState) {
				continue;
			}
			/**
			 * northState: state from which we can do actions A and B (A might
			 * be identical to B). eastState: state we reach after doing B in
			 * northState. westState: state we reach after doing A in
			 * northState. southState: state we reach after doing B in
			 * westState. If we cannot reach southState from eastState by doing
			 * an A, an A edge will be added from eastState to southState.
			 */
			for (Transition northWestTransition : ts.getOutEdges(northState)) {
				State westState = northWestTransition.getTarget();
				if ((westState == northState) || (westState == eastState)) {
					continue;
				}
				for (Transition southWestTransition : ts.getOutEdges(westState)) {
					if (!northEastTransition.getIdentifier().equals(southWestTransition.getIdentifier())) {
						continue;
					}
					State southState = southWestTransition.getTarget();
					if ((southState == northState) || (southState == eastState) || (southState == westState)) {
						continue;
					}
					boolean found = false;
					for (Transition southEastTransition : ts.getOutEdges(eastState)) {
						if (southEastTransition.getTarget() == southState) {
							if (southEastTransition.getIdentifier().equals(northWestTransition.getIdentifier())) {
								found = true;
							}
						}
					}
					if (!found) {
						newTs.addTransition(eastState.getIdentifier(), southState.getIdentifier(),
								northWestTransition.getIdentifier());
						newWeights.add(eastState.getIdentifier(), southState.getIdentifier(),
								northWestTransition.getIdentifier(), 1);
					}
				}
			}
		}

		/**
		 * Replace the old with the new. Note that start and accept payloads are
		 * not affected by this conversion.
		 */
		settings.setTransitionSystem(newTs);
		settings.setWeights(newWeights);
		return newTs;
	}

	/**
	 * Returns a copy of the given transition system with all states with
	 * identical outputs (inputs) merged.
	 * 
	 * @param ts
	 *            The given transition system.
	 * @param output
	 *            Whether to merge by output (true) or by input (false).
	 * @return The resulting transition system.
	 */
	private static TSMinerTransitionSystem merge(TSConverterInput settings, boolean output) {
		TSMinerTransitionSystem ts = settings.getTransitionSystem();
		TSMinerTransitionSystem newTs = new TSMinerTransitionSystem(ts.getLabel(),
				(TSMinerPayloadHandler) ts.getPayloadHanlder());
		newTs.addProxyMap(ts);
		DirectedGraphElementWeights weights = settings.getWeights();
		DirectedGraphElementWeights newWeights = new DirectedGraphElementWeights();
		StartStateSet starts = new StartStateSet();
		AcceptStateSet accepts = new AcceptStateSet();
		/**
		 * Maps every combination of outputs (represented by a string) to the
		 * identifier that will serve as a proxy for all identifier which share
		 * the same set of outgoing edges.
		 */
		TreeMap<String, Object> outputMap = new TreeMap<String, Object>();
		/**
		 * Maps every identifier onto the identifier that will serve as its
		 * proxy.
		 */
		HashMap<Object, Object> proxyMap = new HashMap<Object, Object>();
		/**
		 * Iterate over all states and identifiers, and create the output and
		 * proxy maps.
		 */

		for (State state : ts.getNodes()) {
			/**
			 * Construct the string from the outgoing edges.
			 */
			TreeSet<String> key = new TreeSet<String>();
			Collection<Transition> transitions = output ? ts.getOutEdges(state) : ts.getInEdges(state);
			for (Transition transition : transitions) {
				key.add(transition.getIdentifier().toString());
			}
			Object id = state.getIdentifier();
			Object proxyId;
			if (outputMap.containsKey(key.toString())) {
				/**
				 * Known combination of outputs. Get the proxy.
				 */
				proxyId = outputMap.get(key.toString());
				newWeights.add(proxyId, weights.get(id, 1));
			} else {
				/**
				 * New combination. Add the payload, which will act as a proxy
				 * for this output.
				 */
				newTs.addState(id);
				copyAttributes(newTs, ts, id);
				outputMap.put(key.toString(), id);
				proxyId = id;
				newWeights.add(id, weights.get(id, 1));
			}
			/**
			 * Add the proxy state as proxy for this state.
			 */
			proxyMap.put(id, proxyId);
			if (id != proxyId) {
				newTs.putProxy(id, proxyId);
			}
			/**
			 * If the identifier is a start identifier, then so should its proxy
			 * be.
			 */
			if (settings.getStarts().contains(id)) {
				starts.add(proxyId);
			}
			/**
			 * If the identifier is an accept identifier, then so should its
			 * proxy be.
			 */
			if (settings.getAccepts().contains(id)) {
				accepts.add(proxyId);
			}
		}
		/**
		 * Now, iterate over all transitions, and add a transition from the
		 * proxy of its source to the proxy of its target.
		 */
		for (Transition transition : ts.getEdges()) {
			State fromState = transition.getSource();
			State toState = transition.getTarget();
			/**
			 * Get both proxies.
			 */
			Object fromId = proxyMap.get(fromState.getIdentifier());
			Object toId = proxyMap.get(toState.getIdentifier());
			/**
			 * Add the transition.
			 */
			newTs.addTransition(fromId, toId, transition.getIdentifier());
			newWeights.add(fromId, toId, transition.getIdentifier(),
					weights.get(fromId, toId, transition.getIdentifier(), 1));
		}

		/**
		 * Replace the old with the new.
		 */
		settings.setTransitionSystem(newTs);
		settings.setWeights(newWeights);
		settings.setStarts(starts);
		settings.setAccepts(accepts);
		return newTs;
	}
}
