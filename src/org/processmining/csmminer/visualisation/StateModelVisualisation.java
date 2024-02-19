package org.processmining.csmminer.visualisation;

import java.awt.Color;
import java.awt.Dimension;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.SwingConstants;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.Visualizer;
import org.processmining.csmminer.CSMMiner;
import org.processmining.csmminer.CSMMinerResults;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.connections.ConnectionManager;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.models.connections.transitionsystem.TransitionSystemConnection;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.DirectedGraphElementWeights;
import org.processmining.models.graphbased.directed.transitionsystem.AcceptStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.StartStateSet;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.jgraph.ProMJGraphVisualizer;
import org.processmining.models.jgraph.visualization.ProMJGraphPanel;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyStateAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyTransitionAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.time.TimeStateAnnotation;

public class StateModelVisualisation {

	private static final float[] INITIAL_DASH_PATTERN = { 7f };
	public static final String initialStateLabel = "[Initial State]";
	public static final String confidenceName = "CSMMiner_confidence";
	public static final String oldLabelName = "CSMMiner_oldLabel";

	@Plugin(
			name = "@0 Show Mined State Model",
			level = PluginLevel.NightlyBuild,
			returnLabels = { "Visualization of Mined State Model" },
			returnTypes = { JComponent.class },
			parameterLabels = { "CSM Miner results", "Process name" },
			userAccessible = true)
	@Visualizer
	public JComponent visualize(UIPluginContext context, CSMMinerResults results, String processName) {
		initialiseGraphElementLabels(results, processName);
		
		/**
		 * Will hold the weights, start states, and accept states.
		 */
		TSMinerTransitionSystem stateModel = results.tsMinerOutputs.get(processName).getTransitionSystem();
		DirectedGraphElementWeights weights = new DirectedGraphElementWeights();
		StartStateSet starts = new StartStateSet();
		AcceptStateSet accepts = new AcceptStateSet();
		ProMJGraphPanel mainPanel;

		/**
		 * 1. Tries to get connected transition weights from the framework.
		 */
		ConnectionManager cm = context.getConnectionManager();
		try {
			//			System.out.println("Checking for connection");
			TransitionSystemConnection tsc = cm.getFirstConnection(TransitionSystemConnection.class, context, stateModel);
			//			System.out.println("Checked for connection: " + settings);
			if (tsc.hasWeights()) {
				weights = tsc.getObjectWithRole(TransitionSystemConnection.WEIGHTS);
			}
			starts = tsc.getObjectWithRole(TransitionSystemConnection.STARTIDS);
			accepts = tsc.getObjectWithRole(TransitionSystemConnection.ACCEPTIDS);

		} catch (ConnectionCannotBeObtained e) {
			/**
			 * No connected transition weights found, no problem.
			 */
		}

		/**
		 * 2. Based on the connected objects found: updates visualization.
		 */
		if (!weights.isEmpty()) {
			/**
			 * Set the line widths according to the weights. To avoid getting
			 * ridiculous line widths: linewidth=ln(weight).
			 */
			for (State state : stateModel.getNodes()) {
				state.getAttributeMap().put(AttributeMap.LINEWIDTH,
						new Float(1 + Math.log(Math.E) * Math.log(weights.get(state.getIdentifier(), 1))));
			}
			for (Transition transition : stateModel.getEdges()) {
				transition.getAttributeMap().put(AttributeMap.LINEWIDTH, Math.min(4,
						new Float(1 + Math.log(Math.E) * Math.log(weights.get(transition.getSource().getIdentifier(),
								transition.getTarget().getIdentifier(), transition.getIdentifier(), 1)))));
			}
		}
		if (!starts.isEmpty() || !accepts.isEmpty()) {
			for (State state : stateModel.getNodes()) {
				state.getAttributeMap().put(AttributeMap.BORDERWIDTH, 3);
				state.getAttributeMap().put(AttributeMap.SIZE, new Dimension(120,72));
				
				/**
				 * Note that, in fact, the set of start states is the the set of
				 * start state ids.
				 */
				if (starts.contains(state.getIdentifier())) {
					/**
					 * This state is a start state.
					 */
					state.getAttributeMap().put(AttributeMap.DASHPATTERN, INITIAL_DASH_PATTERN);
				}
				if (accepts.contains(state.getIdentifier())) {
					/**
					 * This state is an accept state.
					 */
					state.setAccepting(true);
				}
			}
		}

		mainPanel = ProMJGraphVisualizer.instance().visualizeGraph(context, stateModel);

		if (!CSMMiner.CSMLabel.equals(processName)) {
			mainPanel.addViewInteractionPanel(new StateModelTransitionFilterComponent(context, results, processName, mainPanel,	1,
					new HashSet<Transition>(), new HashSet<Transition>()), SwingConstants.SOUTH);
			mainPanel.addViewInteractionPanel(new StateModelStateTransformationComponent(context, results, processName, mainPanel),
					SwingConstants.SOUTH);
			// TODO: Expert Option
//			mainPanel.addViewInteractionPanel(new StateModelHighlightingComponent(results, processName), SwingConstants.SOUTH);
		}
		
		setTransitionLabels(results, processName);

		return mainPanel;
	}
	
	public void initialiseGraphElementLabels(CSMMinerResults results, String processName) {
		TSMinerTransitionSystem model = results.tsMinerOutputs.get(processName).getTransitionSystem();

		// Make sure that transition labels are big enough to display the full text
		for (Transition transition : model.getEdges()) {
			FrequencyTransitionAnnotation annotation = results.annotatedTSMinerOutputs.get(processName).
					getFrequency_TransitionAnnotation(transition);
			transition.setLabel(Integer.toString((int) annotation.getObservations().getSum()) + "/" +
					Integer.toString((int) annotation.getObservations().getSum()) + " (100.0%)");
			transition.getAttributeMap().put(AttributeMap.LABELCOLOR, Color.GRAY);
		}

		for (State state : model.getNodes()) {
			FrequencyStateAnnotation frequencyAnnotation = results.annotatedTSMinerOutputs.get(processName).
					getFrequency_StateAnnotation(state);
			TimeStateAnnotation timeAnnotation = results.annotatedTSMinerOutputs.get(processName).getTime_StateAnnotation(state);
			String oldLabel = state.getLabel();
			String tooltip = (String) state.getAttributeMap().get(AttributeMap.TOOLTIP);

			String newLabel = "";
			if (oldLabel.contains("<br/>")) {
				continue;
			}
			else if (tooltip.equals("[]")) {
				newLabel = "<br/>1:" + initialStateLabel;
			}
			else if (tooltip.length() > 14) {
				newLabel = "<br/>" + oldLabel + ":" + tooltip.substring(1, 13) + "...<br/>" +
						"Count:" + (int) frequencyAnnotation.getObservations().getSum();
			}
			else {
				newLabel = "<br/>" + oldLabel + ":" + tooltip.substring(1, tooltip.length()-1) + "<br/>" +
						"Count:" + (int) frequencyAnnotation.getObservations().getSum();
			}

			if (!tooltip.equals("[]") && !timeAnnotation.getSoujourn().isEmpty()) {
				newLabel = newLabel + "<br/>" + "Time " + DurationFormatUtils.formatDuration(
						(long) timeAnnotation.getSoujourn().getMedian(),"HH:mm:ss",true);
			}

			// FIXME: use new attribute, not TOOLTIP
			state.setLabel(newLabel);
			state.getAttributeMap().put(AttributeMap.SHOWLABEL, true);
			state.getAttributeMap().put(AttributeMap.TOOLTIP, tooltip.substring(1, tooltip.length()-1));
		}
	}
	
	public void setTransitionLabels(CSMMinerResults results, String processName) {
		DecimalFormat df = new DecimalFormat("0.#");
		
		TSMinerTransitionSystem model = results.tsMinerOutputs.get(processName).getTransitionSystem();
		Map<State,Integer> stateTransitionCounts = new HashMap<>();

		for (State state : model.getNodes()) {
			int count = 0;

			for (Transition transition : model.getOutEdges(state)) {
				FrequencyTransitionAnnotation annotation = results.annotatedTSMinerOutputs.get(processName).
						getFrequency_TransitionAnnotation(transition);
				count += annotation.getObservations().getSum();
			}

			stateTransitionCounts.put(state, count);
		}

		for (Transition transition : model.getEdges()) {
			FrequencyTransitionAnnotation annotation = results.annotatedTSMinerOutputs.get(processName).
					getFrequency_TransitionAnnotation(transition);
			float confidence = annotation.getObservations().getSum() / stateTransitionCounts.get(transition.getSource());
			transition.setLabel(Integer.toString((int) annotation.getObservations().getSum()) + " (" + 
					(df.format(confidence * 100)) + "%)");
			transition.getAttributeMap().put(AttributeMap.EDGECOLOR, new Color(0, 0, 0, 10 + (int) (confidence * 245)));
			transition.getAttributeMap().put(confidenceName, confidence);
		}
	}
	
}
