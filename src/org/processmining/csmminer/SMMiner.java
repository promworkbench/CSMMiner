package org.processmining.csmminer;

import javax.swing.JOptionPane;

import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.connections.transitionsystem.TransitionSystemConnection;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.DirectedGraphElementWeights;
import org.processmining.models.graphbased.directed.transitionsystem.AcceptStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.StartStateSet;
import org.processmining.plugins.transitionsystem.converter.TSConverterInput;
import org.processmining.plugins.transitionsystem.converter.util.TSConversions;
import org.processmining.plugins.transitionsystem.miner.TSMiner;
import org.processmining.plugins.transitionsystem.miner.TSMinerInput;
import org.processmining.plugins.transitionsystem.miner.TSMinerOutput;
import org.processmining.plugins.transitionsystem.miner.TSMinerPayload;
import org.processmining.plugins.transitionsystem.miner.TSMinerPayloadHandler;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;
import org.processmining.plugins.transitionsystem.miner.util.TSMinerLog;

public class SMMiner extends TSMiner {

	private PluginContext context;
	private boolean useProgressCounter;
	
	public SMMiner(PluginContext context, boolean useProgressCounter) {
		super(context);
		this.context = context;
		this.useProgressCounter = useProgressCounter;
	}
	
	/**
	 * Mines a transition system according to the given settings.
	 * 
	 * @param settings
	 *            The given settings, which includes the log to mine.
	 * @return The mining result, which includes the mined transition system.
	 */
	@Override
	public TSMinerOutput mine(final TSMinerInput settings) {
		TSMinerPayloadHandler payloadHandler = new TSMinerPayloadHandler(settings);
		/**
		 * All results from the mining stage are stored in the input for the
		 * conversion stage.
		 */
		TSConverterInput converterSettings = settings.getConverterSettings();
		TSMinerTransitionSystem ts = new TSMinerTransitionSystem("", payloadHandler);
		converterSettings.setTransitionSystem(ts);
		DirectedGraphElementWeights weights = converterSettings.getWeights();
		StartStateSet starts = converterSettings.getStarts();
		AcceptStateSet accepts = converterSettings.getAccepts();

		int stateCtr = 0;
		int traceCounter = 0;
		int percCounter = 0;

		boolean truncated = false;
		/**
		 * Mining stage.
		 */
		XLog log = settings.getLog();
		int nofTraces = TSMinerLog.getTraces(log).size();
		
		if (useProgressCounter) {
			context.getProgress().setMinimum(0);
			/**
			 * For every trace a tick on the progress bar, and an extra tick for the
			 * modification phase.
			 */
			context.getProgress().setMaximum(nofTraces + 1);
			context.log("Constructing initial transition system");
			context.getProgress().setIndeterminate(false);
		}
		
		for (XTrace trace : TSMinerLog.getTraces(log)) {

			// Cache all events in this trace. This prevents reading the same events over and over again.
			//eventCache = new XEvent[trace.size()];
			//for (int i = 0; i < trace.size(); i++) {
			//	eventCache[i] = trace.get(i);
			//}

			for (int i = 0; i < trace.size(); i++) {

				/**
				 * An Xevent corresponds to a transition in the transition
				 * system. First, construct the payload of the state preceding
				 * the transition.
				 */
				TSMinerPayload fromPayload = (TSMinerPayload) payloadHandler.getSourceStateIdentifier(trace, i);

				/**
				 * Second, in a similar way, create the payload of the state
				 * succeeding the transition.
				 */
				TSMinerPayload toPayload = (TSMinerPayload) payloadHandler.getTargetStateIdentifier(trace, i);

				if (stateCtr > settings.getMaxStates()) {
					if (ts.getNode(fromPayload) == null) {
						if (!truncated) {
							truncated = true;
							if (context instanceof UIPluginContext) {
								JOptionPane.showMessageDialog(null,
										"This transition system contains too many states, and will be truncated.");
							}
						}
						continue;
					}
					if (ts.getNode(toPayload) == null) {
						if (!truncated) {
							truncated = true;
							JOptionPane.showMessageDialog(null,
									"This transition system contains too many states, and will be truncated.");
						}
						continue;
					}
				}

				/**
				 * Create both states with the constructed payloads.
				 */
				if (ts.addState(fromPayload)) {
					stateCtr++;
					ts.getNode(fromPayload).getAttributeMap().put(AttributeMap.LABEL, String.valueOf(stateCtr));
					ts.getNode(fromPayload).getAttributeMap().put(AttributeMap.TOOLTIP, fromPayload.toString());
				}
				weights.add(fromPayload, 1);
				if (ts.addState(toPayload)) {
					stateCtr++;
					ts.getNode(toPayload).getAttributeMap().put(AttributeMap.LABEL, String.valueOf(stateCtr));
					ts.getNode(toPayload).getAttributeMap().put(AttributeMap.TOOLTIP, toPayload.toString());
				}
				weights.add(toPayload, 1);

				/**
				 * Create the transition. Add label if not filtered out.
				 */
				XEvent event = payloadHandler.getSequenceElement(trace, i);
				Object transitionIdentifier = payloadHandler.getTransitionIdentifier(event);

				/**
				 * Note: if the transition already exists, a new one will not be
				 * added.
				 */
				ts.addTransition(fromPayload, toPayload, transitionIdentifier);
				weights.add(fromPayload, toPayload, transitionIdentifier, 1);

				/**
				 * Update start payloads and/or accept payloads if necessary.
				 */
				if (i == 0) {
					starts.add(fromPayload);
				}
				if (i == trace.size() - 1) {
					accepts.add(toPayload);
				}
			}
			
			if (useProgressCounter) {
				//context.getProgress().inc();
				traceCounter++;
				if ((100 * traceCounter / (nofTraces + 1)) > percCounter) {
					context.getProgress().setValue(traceCounter);
					percCounter++;
				}
			}
		}

		if (useProgressCounter) {
//			context.log("Weights after mining: " + converterSettings.getWeights().toString());

			context.log("Converting transition system");
		}
		
		/**
		 * Conversion stage.
		 */
		SMConverter converter = new SMConverter(context);
		TSMinerOutput output = converter.convert(converterSettings, false);
		
		if (useProgressCounter) {
			context.getProgress().setValue(nofTraces + 1); // We're done.
			
//			context.log("Weights after converting: " + output.getWeights().toString());
			context.log("Done!");
		}

		boolean useSettings = true;
		if (ts != output.getTransitionSystem()) {
			/*
			 * Reduction rules have been applied. This may affect states and/or
			 * transitions.
			 */
			if (converterSettings.getUse(TSConversions.EXTEND)) {
				/*
				 * Transitions may have been added.
				 */
				//useSettings = false;
			}
			if (converterSettings.getUse(TSConversions.MERGEBYINPUT)) {
				/*
				 * States may have been merged.
				 */
				//useSettings = false;
			}
			if (converterSettings.getUse(TSConversions.MERGEBYOUTPUT)) {
				/*
				 * States may have been merged.
				 */
				//useSettings = false;
			}
		}

		if (useSettings) {
			//			System.out.println("Creating provided object for settings");
			context.getProvidedObjectManager().createProvidedObject("TS Miner settings", settings, TSMinerInput.class,
					context);
			//			System.out.println("Created provided object for settings");
		}

		//		System.out.println("Creating connection");
		context.addConnection(new TransitionSystemConnection(output.getTransitionSystem(), output.getWeights(),
				output.getStarts(), output.getAccepts(), useSettings ? settings : null));
		//		System.out.println("Created connection");

		return output;
	}

}
