package org.processmining.csmminer.visualisation;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.table.DefaultTableModel;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.processmining.csmminer.CSMMiner;
import org.processmining.csmminer.CSMMinerResults;
import org.processmining.csmminer.relations.PerspectiveTransition;
import org.processmining.csmminer.relations.StateCounts;
import org.processmining.framework.util.Pair;
import org.processmining.framework.util.ui.scalableview.interaction.ViewInteractionPanel;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.DirectedGraphElement;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.jgraph.visualization.ProMJGraphPanel;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyStateAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyTransitionAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.time.TimeStateAnnotation;

import com.google.common.collect.Sets;

public class StateModelVisualisationListener extends MouseAdapter {

	public static final String cooccurrenceHighlighting = "Co-occurrence relations";
	public static final String stateEntryPerformanceHighlighting = "State Entry Sojourn Split";
	public static final String tracePerformanceHighlighting = "Trace Sojourn Split";
	
	Color DARKORANGE = new Color(255,140,0);
	
	public final ProMJGraphPanel graphPanel;
	private final CSMMinerResults results;
	private final String processName;
	
	public boolean useCountStatistics = false;
	
	// TODO: replace with additional property in State attributemap
	public Map<State,Object> oldStateColor = new HashMap<>();
	
	public StateModelVisualisationListener(CSMMinerResults results, String processName) {
		this.graphPanel = results.connections.graphPanels.get(processName);
		this.results = results;
		this.processName = processName;
		
		for (String name : results.tsMinerOutputs.keySet()) {
			TSMinerTransitionSystem model = results.tsMinerOutputs.get(name).getTransitionSystem();
			
			for (State state : model.getNodes()) {
				oldStateColor.put(state, Color.LIGHT_GRAY);
			}
		}
		
		for (ViewInteractionPanel panel : graphPanel.getViewInteractionPanels().keySet()) {
			if (panel instanceof StateModelStateTransformationComponent) {
				((StateModelStateTransformationComponent) panel).modelListener = this;
			}
			if (panel instanceof StateModelHighlightingComponent) {
				((StateModelHighlightingComponent) panel).modelListener = this;
			}
		}
		
		for (State state : results.tsMinerOutputs.get(processName).getTransitionSystem().getNodes()) {
			state.getAttributeMap().put(StateModelVisualisation.oldLabelName, state.getLabel());
		}
		
		for (Transition transition : results.tsMinerOutputs.get(processName).getTransitionSystem().getEdges()) {
			transition.getAttributeMap().put(StateModelVisualisation.oldLabelName, transition.getLabel());
		}
	}
	
	@Override
	public void mousePressed(MouseEvent arg0) {
		results.connections.metricsPane.table.clearSelection();
		results.connections.graphStatisticsWrapper.setBottomComponent(results.connections.statisticsPane);
		results.connections.statisticsPane.repaint();
		
		for (String name : results.connections.graphWrappers.keySet()) {
			results.connections.graphWrappers.get(name).setVisible(results.connections.visibleModels.get(name));

			if (results.connections.visibleModels.get(name)) {
				(new GraphRefreshHelper(results.connections.topComponent, results.connections.graphPanels.get(name).getGraph())).start();
			}
		}
		results.connections.topComponent.validate();
		
		graphPanel.requestFocusInWindow();
		graphPanel.getGraph().setHighlightColor(Color.ORANGE);
		resetNodeAndEdgeColours();
		resetStatistics();
		
		DirectedGraphElement clickedElement = graphPanel.getElementForLocation(arg0.getX(), arg0.getY());
		if (clickedElement instanceof Transition) {
			clickedElement.getAttributeMap().put(AttributeMap.EDGECOLOR, Color.RED);
			// FIXME: remove this reliance on PerspectiveTransition
			String source = ((Transition) clickedElement).getSource().getIdentifier().toString();
			String target = ((Transition) clickedElement).getTarget().getIdentifier().toString();
			
			// Only works under assumption that states are identified with their name as in the event log
			PerspectiveTransition transition = new PerspectiveTransition(processName, source.substring(1, source.length()-1),
					target.substring(1, target.length()-1));
			StateCounts stateCounts = results.transitionsCooccurringStates.get(processName, (Transition) clickedElement);
			
			if (processName.equals(CSMMiner.CSMLabel)) {
				updateColoursForSynchronousProductTransition((((Transition) clickedElement).getSource()),
						(((Transition) clickedElement).getTarget()));
			}
			else if (stateCounts != null) {
				updateNodeColoursForTransition((Transition) clickedElement, stateCounts);
				updateEdgeColoursForTransition(transition, stateCounts);
			}
			
			updateTransitionStatistics((Transition) clickedElement);
		}
		else if (clickedElement instanceof State) {
			if (results.connections.highlightSwitch.getSelectedItem() == cooccurrenceHighlighting) {
				updateNodeColoursForState((State) clickedElement);
				if (!processName.equals(CSMMiner.CSMLabel)) {
					updateEdgeColorsForState((State) clickedElement);
				}
			}
			else if (results.connections.highlightSwitch.getSelectedItem() == stateEntryPerformanceHighlighting) {
				updateNodeColoursStatePerformance((State) clickedElement);
				if (!processName.equals(CSMMiner.CSMLabel)) {
					updateEdgeColorsForStatePerformance((State) clickedElement);
				}
			}
			else if (results.connections.highlightSwitch.getSelectedItem() == tracePerformanceHighlighting) {
				updateNodeColoursStatePerformance((State) clickedElement);
				if (!processName.equals(CSMMiner.CSMLabel)) {
					updateEdgeColorsForStatePerformance((State) clickedElement);
				}
			}
			
			updateStateStatistics((State) clickedElement);
		}
		
		clearOldSelection();
		refreshAllGraphs();
	}
	
	private void updateEdgeColorsForStatePerformance(State state) {
		Map<State,Float> modifiedStates = new HashMap<>();
		DecimalFormat df = new DecimalFormat("0.#");
		
		for (String name : results.tsMinerOutputs.keySet()) {
			if (name.equals(processName)) continue;
			TSMinerTransitionSystem model = results.tsMinerOutputs.get(name).getTransitionSystem();
			
			for (Transition transition : model.getEdges()) {
				Integer count = results.transitionsCooccurringStates.getCount(name, transition, processName, state);
				if (count != null) {
					if (name.equals(CSMMiner.CSMLabel)) {
						float confidence = (float) transition.getAttributeMap().get(StateModelVisualisation.confidenceName);
						Color transitionColor = new Color(255, 140, 0, 10 + (int) (confidence * 245));
						transition.getAttributeMap().put(AttributeMap.EDGECOLOR, transitionColor);
					}
					else {
						State modifiedState = transition.getSource();
						if (!modifiedStates.containsKey(modifiedState)) modifiedStates.put(modifiedState, 0f);
						modifiedStates.put(modifiedState, modifiedStates.get(modifiedState) + count);
					}
				}
			}
			
			for (Transition transition : model.getEdges()) {
				Integer count = results.transitionsCooccurringStates.getCount(name, transition, processName, state);
				if (count != null && !name.equals(CSMMiner.CSMLabel)) {
					transition.getAttributeMap().put(StateModelVisualisation.oldLabelName, transition.getLabel());
					float confidence = count / modifiedStates.get(transition.getSource());
					transition.setLabel(count + " (" + df.format(confidence * 100) + "%)");
					Color transitionColor = new Color(255, 140, 0, 10 + (int) (confidence * 245));
					
					Integer fastCount = 0, slowCount = 0;
					if (results.connections.highlightSwitch.getSelectedItem() == stateEntryPerformanceHighlighting) {
						fastCount = results.transitionsCooccurringStates.cooccurringTransitionCountPerEntryFast.get(
								processName + "~" + state.getAttributeMap().get(AttributeMap.TOOLTIP),
								new Pair<String, String>(name + "~" + transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP),
										name + "~" + transition.getTarget().getAttributeMap().get(AttributeMap.TOOLTIP)));
						slowCount = results.transitionsCooccurringStates.cooccurringTransitionCountPerEntrySlow.get(
								processName + "~" + state.getAttributeMap().get(AttributeMap.TOOLTIP),
								new Pair<String, String>(name + "~" + transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP),
										name + "~" + transition.getTarget().getAttributeMap().get(AttributeMap.TOOLTIP)));
					}
					else if (results.connections.highlightSwitch.getSelectedItem() == tracePerformanceHighlighting) {
						fastCount = results.transitionsCooccurringStates.cooccurringTransitionCountPerTraceFast.get(
								processName + "~" + state.getAttributeMap().get(AttributeMap.TOOLTIP),
								new Pair<String, String>(name + "~" + transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP),
										name + "~" + transition.getTarget().getAttributeMap().get(AttributeMap.TOOLTIP)));
						slowCount = results.transitionsCooccurringStates.cooccurringTransitionCountPerTraceSlow.get(
								processName + "~" + state.getAttributeMap().get(AttributeMap.TOOLTIP),
								new Pair<String, String>(name + "~" + transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP),
										name + "~" + transition.getTarget().getAttributeMap().get(AttributeMap.TOOLTIP)));
					}
					
					if (fastCount == null) fastCount = 0;
					if (slowCount == null) slowCount = 0;
					
					confidence = 1;
					double difference = (fastCount - slowCount) / (double) count;
					if (Math.abs(difference) < 0.0005) {
						transitionColor = new Color(255, 255, 0, 10 + (int) (confidence * 245));
					}
					else if (fastCount > slowCount) {
						transitionColor = new Color((int) (255 - 255 * difference), 255, 0, 10 + (int) (confidence * 245));
					}
					else {
						transitionColor = new Color(255, (int) (255 + 255 * difference), 0, 10 + (int) (confidence * 245));
					}
					
					transition.setLabel(fastCount + "/" + slowCount);
					transition.getAttributeMap().put(AttributeMap.EDGECOLOR, transitionColor);
				}
				else if (modifiedStates.containsKey(transition.getSource())) {
					transition.getAttributeMap().put(StateModelVisualisation.oldLabelName, transition.getLabel());
					transition.setLabel("0/0");
					Color transitionColor = new Color(255, 255, 0, 10);
					transition.getAttributeMap().put(AttributeMap.EDGECOLOR, transitionColor);
				}
			}
		}
	}

	private void updateNodeColoursStatePerformance(State clickedState) {
		if (CSMMiner.CSMLabel.equals(processName)) {
			Set<Pair<String, Object>> mappedStates = results.stateMap.getMappedStates(CSMMiner.CSMLabel, clickedState.getIdentifier());
			
			if (mappedStates != null) {
				for (Pair<String, Object> pair : mappedStates) {
					State state = results.tsMinerOutputs.get(pair.getFirst()).getTransitionSystem().getNode(pair.getSecond());
					oldStateColor.put(state, state.getAttributeMap().get(AttributeMap.FILLCOLOR));
					state.getAttributeMap().put(AttributeMap.FILLCOLOR, DARKORANGE);
				}
			}
		}
		else {
			TSMinerTransitionSystem synchronousModel = results.tsMinerOutputs.get(CSMMiner.CSMLabel).getTransitionSystem();
			Set<Pair<String,Object>> synchronousStates = results.stateMap.getMappedStates(processName, clickedState.getIdentifier());
			Set<State> colouredStates = new HashSet<>();
			
			if (synchronousStates != null) {
				for (Pair<String, Object> pair : synchronousStates) {
					State synchronousState = synchronousModel.getNode(pair.getSecond());
					if (results.connections.highlightSwitch.getSelectedItem() == stateEntryPerformanceHighlighting) {
						updateNodeWithPerformanceStatisticsForStateEntry(clickedState, synchronousState, pair.getFirst());
					}
					else if (results.connections.highlightSwitch.getSelectedItem() == tracePerformanceHighlighting) {
						updateNodeWithPerformanceStatisticsForTrace(clickedState, synchronousState, pair.getFirst());
					}
					
					for (Pair<String, Object> otherPair : results.stateMap.getMappedStates(CSMMiner.CSMLabel, pair.getSecond())) {
						State state = results.tsMinerOutputs.get(otherPair.getFirst()).getTransitionSystem().getNode(otherPair.getSecond());
						
						if (!state.equals(clickedState) && !colouredStates.contains(state)) {
							if (results.connections.highlightSwitch.getSelectedItem() == stateEntryPerformanceHighlighting) {
								updateNodeWithPerformanceStatisticsForStateEntry(clickedState, state, otherPair.getFirst());
							}
							else if (results.connections.highlightSwitch.getSelectedItem() == tracePerformanceHighlighting) {
								updateNodeWithPerformanceStatisticsForTrace(clickedState, state, otherPair.getFirst());
							}
							colouredStates.add(state);
						}
					}
				}
			}
		}
	}
	
	private void updateNodeWithPerformanceStatisticsForStateEntry(State clickedState, State updateState, String updateStateProcessName) {
		oldStateColor.put(updateState, updateState.getAttributeMap().get(AttributeMap.FILLCOLOR));
		updateState.getAttributeMap().put(StateModelVisualisation.oldLabelName, updateState.getLabel());
		
		String[] oldLabel = updateState.getLabel().split("<br/>");
		String newLabel = "<br/>" + oldLabel[1];
		
		if (updateStateProcessName.equals(CSMMiner.CSMLabel)) {
			float support = results.sharedStateSojourns.get(new Pair<>(clickedState, updateState)) /
					results.totalStateSojourns.get(CSMMiner.CSMLabel);
			float confidence = results.sharedStateSojourns.get(new Pair<>(clickedState, updateState)) /
					results.stateSojourns.get(clickedState);
			
			DecimalFormat df = new DecimalFormat("#.###");
			newLabel = newLabel + "<br/>Sup:" + df.format(support);
			newLabel = newLabel + "<br/>Conf:" + df.format(confidence);
			
			updateState.setLabel(newLabel);
			
			if (!Float.isNaN(confidence)) {
				Color stateColor = new Color((int) (192 + 63 * confidence), (int) (192 - 52 * confidence), (int) (192 - 192 * confidence));
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
			}
			else {
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, Color.LIGHT_GRAY);
			}
			
			updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, Color.ORANGE);
		}
		else {
			DescriptiveStatistics fastStatistics = results.transitionsCooccurringStates.cooccurringStatesTimePerEntryFast.get(
					processName + "~" + clickedState.getAttributeMap().get(AttributeMap.TOOLTIP),
					updateStateProcessName + "~" + updateState.getAttributeMap().get(AttributeMap.TOOLTIP));
			DescriptiveStatistics slowStatistics = results.transitionsCooccurringStates.cooccurringStatesTimePerEntrySlow.get(
					processName + "~" + clickedState.getAttributeMap().get(AttributeMap.TOOLTIP),
					updateStateProcessName + "~" + updateState.getAttributeMap().get(AttributeMap.TOOLTIP));
			DescriptiveStatistics totalFastStatistics = results.transitionsCooccurringStates.timePerEntryFast.get(
					processName + "~" + clickedState.getAttributeMap().get(AttributeMap.TOOLTIP));
			DescriptiveStatistics totalSlowStatistics = results.transitionsCooccurringStates.timePerEntrySlow.get(
					processName + "~" + clickedState.getAttributeMap().get(AttributeMap.TOOLTIP));
			
			if (fastStatistics == null) fastStatistics = new DescriptiveStatistics();
			if (slowStatistics == null) slowStatistics = new DescriptiveStatistics();
			if (totalFastStatistics == null) totalFastStatistics = new DescriptiveStatistics();
			if (totalSlowStatistics == null) totalSlowStatistics = new DescriptiveStatistics();
			
			double fastConfidence = fastStatistics.getSum() / totalFastStatistics.getSum();
			double slowConfidence = slowStatistics.getSum() / totalSlowStatistics.getSum();
			
			DecimalFormat df = new DecimalFormat("#.###");
			newLabel = newLabel + "<br/>Fast:" + df.format(fastConfidence);
			newLabel = newLabel + "<br/>Slow:" + df.format(slowConfidence);
			
			updateState.setLabel(newLabel);
			
			if (Double.isNaN(fastConfidence)) fastConfidence = 0;
			if (Double.isNaN(slowConfidence)) slowConfidence = 0;
			
			double difference = fastConfidence - slowConfidence;
			
			if (Math.abs(difference) < 0.0005) {
				Color stateColor = new Color((int) 
						(192 + 63 * fastConfidence), (int) (192 + 63 * fastConfidence), (int) (192 - 192 * fastConfidence));
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
				updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, Color.YELLOW);
			}
			else if (fastConfidence > slowConfidence) {
				Color stateColor = new Color((int) 
						(192 - 192 * fastConfidence), (int) (192 + 63 * fastConfidence), (int) (192 - 192 * fastConfidence));
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
				Color fillColor = new Color((int) (255 - 255 * difference), 255, 0);
				updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, fillColor);
			}
			else {
				Color stateColor = new Color((int) 
						(192 + 63 * slowConfidence), (int) (192 - 192 * slowConfidence), (int) (192 - 192 * slowConfidence));
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
				Color fillColor = new Color(255, (int) (255 + 255 * difference), 0);
				updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, fillColor);
			}
		}
	}
	
	private void updateNodeWithPerformanceStatisticsForTrace(State clickedState, State updateState, String updateStateProcessName) {
		oldStateColor.put(updateState, updateState.getAttributeMap().get(AttributeMap.FILLCOLOR));
		updateState.getAttributeMap().put(StateModelVisualisation.oldLabelName, updateState.getLabel());
		
		String[] oldLabel = updateState.getLabel().split("<br/>");
		String newLabel = "<br/>" + oldLabel[1];
		
		if (updateStateProcessName.equals(CSMMiner.CSMLabel)) {
			float support = results.sharedStateSojourns.get(new Pair<>(clickedState, updateState)) /
					results.totalStateSojourns.get(CSMMiner.CSMLabel);
			float confidence = results.sharedStateSojourns.get(new Pair<>(clickedState, updateState)) /
					results.stateSojourns.get(clickedState);
			
			DecimalFormat df = new DecimalFormat("#.###");
			newLabel = newLabel + "<br/>Sup:" + df.format(support);
			newLabel = newLabel + "<br/>Conf:" + df.format(confidence);
			
			updateState.setLabel(newLabel);
			
			if (!Float.isNaN(confidence)) {
				Color stateColor = new Color((int) (192 + 63 * confidence), (int) (192 - 52 * confidence), (int) (192 - 192 * confidence));
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
			}
			else {
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, Color.LIGHT_GRAY);
			}
			
			updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, Color.ORANGE);
		}
		else {
			DescriptiveStatistics fastStatistics = results.transitionsCooccurringStates.cooccurringStatesTimePerTraceFast.get(
					processName + "~" + clickedState.getAttributeMap().get(AttributeMap.TOOLTIP),
					updateStateProcessName + "~" + updateState.getAttributeMap().get(AttributeMap.TOOLTIP));
			DescriptiveStatistics slowStatistics = results.transitionsCooccurringStates.cooccurringStatesTimePerTraceSlow.get(
					processName + "~" + clickedState.getAttributeMap().get(AttributeMap.TOOLTIP),
					updateStateProcessName + "~" + updateState.getAttributeMap().get(AttributeMap.TOOLTIP));
			DescriptiveStatistics totalFastStatistics = results.transitionsCooccurringStates.timePerTraceFast.get(
					processName + "~" + clickedState.getAttributeMap().get(AttributeMap.TOOLTIP));
			DescriptiveStatistics totalSlowStatistics = results.transitionsCooccurringStates.timePerTraceSlow.get(
					processName + "~" + clickedState.getAttributeMap().get(AttributeMap.TOOLTIP));
			
			if (fastStatistics == null) fastStatistics = new DescriptiveStatistics();
			if (slowStatistics == null) slowStatistics = new DescriptiveStatistics();
			if (totalFastStatistics == null) totalFastStatistics = new DescriptiveStatistics();
			if (totalSlowStatistics == null) totalSlowStatistics = new DescriptiveStatistics();
			
			double fastConfidence = fastStatistics.getSum() / totalFastStatistics.getSum();
			double slowConfidence = slowStatistics.getSum() / totalSlowStatistics.getSum();
			
			DecimalFormat df = new DecimalFormat("#.###");
			newLabel = newLabel + "<br/>Fast:" + df.format(fastConfidence);
			newLabel = newLabel + "<br/>Slow:" + df.format(slowConfidence);
			
			updateState.setLabel(newLabel);
			
			if (Double.isNaN(fastConfidence)) fastConfidence = 0;
			if (Double.isNaN(slowConfidence)) slowConfidence = 0;
					
			double difference = fastConfidence - slowConfidence;
			
			if (Math.abs(difference) < 0.0005) {
				Color stateColor = new Color((int) 
						(192 + 63 * fastConfidence), (int) (192 + 63 * fastConfidence), (int) (192 - 192 * fastConfidence));
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
				updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, Color.YELLOW);
			}
			else if (fastConfidence > slowConfidence) {
				Color stateColor = new Color((int) 
						(192 - 192 * fastConfidence), (int) (192 + 63 * fastConfidence), (int) (192 - 192 * fastConfidence));
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
				Color fillColor = new Color((int) (255 - 255 * difference), 255, 0);
				updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, fillColor);
			}
			else {
				Color stateColor = new Color((int) 
						(192 + 63 * slowConfidence), (int) (192 - 192 * slowConfidence), (int) (192 - 192 * slowConfidence));
				updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
				Color fillColor = new Color(255, (int) (255 + 255 * difference), 0);
				updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, fillColor);
			}
		}
	}

	public void updateNodeColoursForTransition(Transition transition, StateCounts stateCounts) {
		int totalCount = stateCounts.getCount(processName, transition.getSource());
		// FIXME: remove
//		int totalCount = 0;
//		for (Pair<String, String> processState : stateCounts.counts.keySet()) {
//			if (processState.getFirst().equals(processName)) {
//				totalCount = stateCounts.counts.get(processState);
//				break;
//			}
//		}
		
		for (Pair<String, String> processState : stateCounts.getStates()) {
			if (processState.getFirst().equals(processName) || processState.getFirst().equals(CSMMiner.CSMLabel)) continue;
			TSMinerTransitionSystem model = results.tsMinerOutputs.get(processState.getFirst()).getTransitionSystem();
			State modelState = model.getNode(results.stateMap.getIdentifier(processState.getFirst(), processState.getSecond()));
			
			updateNodeWithStatisticsForTransition(transition, stateCounts, totalCount, processState.getFirst(), modelState,
					stateCounts.getCount(processState));
		}
	}
	
	public void updateNodeWithStatisticsForTransition(Transition transition, StateCounts stateCounts, int totalCount,
			String statePerspective, State state, int count) {
		oldStateColor.put(state, state.getAttributeMap().get(AttributeMap.FILLCOLOR));
		state.getAttributeMap().put(StateModelVisualisation.oldLabelName, state.getLabel());
		
		float stateTotal = 0f;
		float transitionTotal = 0f;
		for (Transition edge : transition.getSource().getGraph().getOutEdges(transition.getSource())) {
			if (results.transitionsCooccurringStates.containsCount(processName, edge, statePerspective, state)) {
				stateTotal += results.transitionsCooccurringStates.getCount(processName, edge, statePerspective, state);
			}
			// FIXME: remove
//			stateTotal += results.transitionCounts.get(new Pair<>(state, edge)) == null ? 0 :
//				results.transitionCounts.get(new Pair<>(state, edge));
			transitionTotal += results.annotatedTSMinerOutputs.get(processName).getFrequency_TransitionAnnotation(edge).
					getObservations().getSum();
		}
		float stateProbability = stateTotal / transitionTotal;
		
		String[] oldLabel = state.getLabel().split("<br/>");
		String newLabel = "<br/>" + oldLabel[1];
		float confidence = count / (float) totalCount;
		float lift = confidence / stateProbability;
		
		DecimalFormat df = new DecimalFormat("#.###");
		newLabel = newLabel + "<br/>Conf:" + df.format(confidence);
		newLabel = newLabel + "<br/>Lift:" + df.format(lift);
		
		state.setLabel(newLabel);
		
		Color stateColor = new Color((int) (192 + 63 * confidence), (int) (192 - 52 * confidence), (int) (192 - 192 * confidence));
		state.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
		state.getAttributeMap().put(AttributeMap.STROKECOLOR, Color.ORANGE);
	}
	
	public void updateEdgeColoursForTransition(PerspectiveTransition perspectiveTransition, StateCounts stateCounts) {
		TSMinerTransitionSystem model = results.tsMinerOutputs.get(CSMMiner.CSMLabel).getTransitionSystem();
		
		for (Pair<String, String> processState : stateCounts.getStates()) {
			if (processState.getFirst().equals(CSMMiner.CSMLabel)) {
				for (Transition transition : model.getEdges()) {
					if (("[" + processState.getSecond() + "]").equals(transition.getSource().getIdentifier().toString()) &&
							transition.getTarget().getIdentifier().toString().contains("|" + perspectiveTransition.toState + "|")) {
						transition.getAttributeMap().put(AttributeMap.EDGECOLOR, DARKORANGE);
					}
				}
			}
		}
	}
	
	public void updateColoursForSynchronousProductTransition(State source, State target) {
		Set<Pair<String, Object>> sourceMappedStates = results.stateMap.getMappedStates(CSMMiner.CSMLabel, source.getIdentifier());
		Set<Pair<String, Object>> targetMappedStates = results.stateMap.getMappedStates(CSMMiner.CSMLabel, target.getIdentifier());
		
		if (sourceMappedStates != null && targetMappedStates != null) {
			Set<Pair<String, Object>> sourceDifferences = Sets.difference(sourceMappedStates, targetMappedStates);
			Set<Pair<String, Object>> targetDifferences = Sets.difference(targetMappedStates, sourceMappedStates);
			Set<Pair<String, Object>> intersection = Sets.intersection(sourceMappedStates, targetMappedStates);
			
			for (Pair<String, Object> sourceDifference : sourceDifferences) {
				for (Pair<String, Object> targetDifference : targetDifferences) {
					if (sourceDifference.getFirst().equals(targetDifference.getFirst())) {
						TSMinerTransitionSystem model = results.tsMinerOutputs.get(sourceDifference.getFirst()).getTransitionSystem();
						
						for (Transition transition : model.getEdges()) {
							if (transition.getSource().getIdentifier().equals(sourceDifference.getSecond()) &&
									transition.getTarget().getIdentifier().equals(targetDifference.getSecond())) {
								transition.getAttributeMap().put(AttributeMap.EDGECOLOR, DARKORANGE);
							}
						}
					}
				}
			}
			
			for (Pair<String, Object> processStateIdentifier : intersection) {
				TSMinerTransitionSystem model = results.tsMinerOutputs.get(processStateIdentifier.getFirst()).getTransitionSystem();
				State state = model.getNode(processStateIdentifier.getSecond());
				oldStateColor.put(state, state.getAttributeMap().get(AttributeMap.FILLCOLOR));
				state.getAttributeMap().put(AttributeMap.FILLCOLOR, DARKORANGE);
			}
		}
	}
	
	public void updateNodeColoursForState(State clickedState) {
		if (CSMMiner.CSMLabel.equals(processName)) {
			Set<Pair<String, Object>> mappedStates = results.stateMap.getMappedStates(CSMMiner.CSMLabel, clickedState.getIdentifier());
			
			if (mappedStates != null) {
				for (Pair<String, Object> pair : mappedStates) {
					State state = results.tsMinerOutputs.get(pair.getFirst()).getTransitionSystem().getNode(pair.getSecond());
					oldStateColor.put(state, state.getAttributeMap().get(AttributeMap.FILLCOLOR));
					state.getAttributeMap().put(AttributeMap.FILLCOLOR, DARKORANGE);
				}
			}
		}
		else {
			TSMinerTransitionSystem synchronousModel = results.tsMinerOutputs.get(CSMMiner.CSMLabel).getTransitionSystem();
			Set<Pair<String,Object>> synchronousStates = results.stateMap.getMappedStates(processName, clickedState.getIdentifier());
			Set<State> colouredStates = new HashSet<>();
			
			if (synchronousStates != null) {
				for (Pair<String, Object> pair : synchronousStates) {
					State synchronousState = synchronousModel.getNode(pair.getSecond());
					updateNodeWithStatisticsForState(clickedState, synchronousState, pair.getFirst());
					
					for (Pair<String, Object> otherPair : results.stateMap.getMappedStates(CSMMiner.CSMLabel, pair.getSecond())) {
						State state = results.tsMinerOutputs.get(otherPair.getFirst()).getTransitionSystem().getNode(otherPair.getSecond());
						
						if (!state.equals(clickedState) && !colouredStates.contains(state)) {
							updateNodeWithStatisticsForState(clickedState, state, otherPair.getFirst());
							colouredStates.add(state);
						}
					}
				}
			}
		}
	}
	
	public void updateNodeWithStatisticsForState(State clickedState, State updateState, String updateStateProcessName) {
		oldStateColor.put(updateState, updateState.getAttributeMap().get(AttributeMap.FILLCOLOR));
		updateState.getAttributeMap().put(StateModelVisualisation.oldLabelName, updateState.getLabel());
		
		String[] oldLabel = updateState.getLabel().split("<br/>");
		String newLabel = "<br/>" + oldLabel[1];
		float support = 0;
		float confidence = 0;
		float lift = 0;
		
		if (useCountStatistics) {
			support = results.sharedStateCounts.get(new Pair<>(clickedState, updateState)) /
					(float) results.totalStateCounts.get(CSMMiner.CSMLabel);
			confidence = results.sharedStateCounts.get(new Pair<>(clickedState, updateState)) /
					(float) results.stateCounts.get(clickedState);
			lift = confidence / (results.stateCounts.get(updateState) / (float) results.totalStateCounts.get(CSMMiner.CSMLabel));
		}
		else {
			support = results.sharedStateSojourns.get(new Pair<>(clickedState, updateState)) /
					results.totalStateSojourns.get(CSMMiner.CSMLabel);
			confidence = results.sharedStateSojourns.get(new Pair<>(clickedState, updateState)) / results.stateSojourns.get(clickedState);
			lift = confidence / (results.stateSojourns.get(updateState) / results.totalStateSojourns.get(CSMMiner.CSMLabel));
		}
		
		DecimalFormat df = new DecimalFormat("#.###");
		if (updateStateProcessName.equals(CSMMiner.CSMLabel)) newLabel = newLabel + "<br/>Sup:" + df.format(support);
		newLabel = newLabel + "<br/>Conf:" + df.format(confidence);
		if (!updateStateProcessName.equals(CSMMiner.CSMLabel)) newLabel = newLabel + "<br/>Lift:" + df.format(lift);
		
		updateState.setLabel(newLabel);
		
		if (!Float.isNaN(confidence)) {
			Color stateColor = new Color((int) (192 + 63 * confidence), (int) (192 - 52 * confidence), (int) (192 - 192 * confidence));
			updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, stateColor);
		}
		else {
			updateState.getAttributeMap().put(AttributeMap.FILLCOLOR, Color.LIGHT_GRAY);
		}
		
		updateState.getAttributeMap().put(AttributeMap.STROKECOLOR, Color.ORANGE);
	}
	
	public void updateEdgeColorsForState(State state) {
		Map<State,Float> modifiedStates = new HashMap<>();
		DecimalFormat df = new DecimalFormat("0.#");
		
		for (String name : results.tsMinerOutputs.keySet()) {
			if (name.equals(processName)) continue;
			TSMinerTransitionSystem model = results.tsMinerOutputs.get(name).getTransitionSystem();
			
			for (Transition transition : model.getEdges()) {
				Integer count = results.transitionsCooccurringStates.getCount(name, transition, processName, state);
				if (count != null) {
					if (name.equals(CSMMiner.CSMLabel)) {
						float confidence = (float) transition.getAttributeMap().get(StateModelVisualisation.confidenceName);
						Color transitionColor = new Color(255, 140, 0, 10 + (int) (confidence * 245));
						transition.getAttributeMap().put(AttributeMap.EDGECOLOR, transitionColor);
					}
					else {
						State modifiedState = transition.getSource();
						if (!modifiedStates.containsKey(modifiedState)) modifiedStates.put(modifiedState, 0f);
						modifiedStates.put(modifiedState, modifiedStates.get(modifiedState) + count);
					}
				}
			}
			
			for (Transition transition : model.getEdges()) {
				Integer count = results.transitionsCooccurringStates.getCount(name, transition, processName, state);
				if (count != null && !name.equals(CSMMiner.CSMLabel)) {
					transition.getAttributeMap().put(StateModelVisualisation.oldLabelName, transition.getLabel());
					float confidence = count / modifiedStates.get(transition.getSource());
					transition.setLabel(count + " (" + df.format(confidence * 100) + "%)");
//					Color stateColor = new Color((int) (255 * Math.pow(confidence, 1/2d)), (int) (140 * Math.pow(confidence, 1/2d)), 0);
					Color stateColor = new Color(255, 140, 0, 10 + (int) (confidence * 245));
					transition.getAttributeMap().put(AttributeMap.EDGECOLOR, stateColor);
				}
				else if (modifiedStates.containsKey(transition.getSource())) {
					transition.getAttributeMap().put(StateModelVisualisation.oldLabelName, transition.getLabel());
					transition.setLabel("0 (0%)");
					Color stateColor = new Color(255, 140, 0, 10);
					transition.getAttributeMap().put(AttributeMap.EDGECOLOR, stateColor);
				}
			}
		}
	}
	
	public void resetNodeAndEdgeColours() {
		for (String name : results.tsMinerOutputs.keySet()) {
			TSMinerTransitionSystem model = results.tsMinerOutputs.get(name).getTransitionSystem();
			
			for (State state : model.getNodes()) {
				state.getAttributeMap().put(AttributeMap.FILLCOLOR, oldStateColor.get(state));
				state.getAttributeMap().put(AttributeMap.STROKECOLOR, Color.BLACK);
				state.setLabel((String) state.getAttributeMap().get(StateModelVisualisation.oldLabelName));
			}
			
			for (Transition transition : model.getEdges()) {
				float confidence = (float) transition.getAttributeMap().get(StateModelVisualisation.confidenceName);
				transition.getAttributeMap().put(AttributeMap.EDGECOLOR, new Color(0, 0, 0, 10 + (int) (confidence * 245)));
				transition.setLabel((String) transition.getAttributeMap().get(StateModelVisualisation.oldLabelName));
			}
		}
	}
	
	public void clearOldSelection() {
		for (ProMJGraphPanel otherGraphPanel : results.connections.graphPanels.values()) {
			if (!otherGraphPanel.equals(graphPanel)) {
				otherGraphPanel.getGraph().clearSelection();
			}
		}
	}
	
	public void refreshAllGraphs() {
		for (ProMJGraphPanel graphPanel : results.connections.graphPanels.values()) {
			graphPanel.getGraph().refresh();
		}
	}
	
	public void resetStatistics() {
		DefaultTableModel model = (DefaultTableModel) results.connections.statisticsPane.getModel();
		model.removeRow(1);
		model.addRow(new Object[] {""});
	}
	
	public void updateTransitionStatistics(Transition transition) {
		DefaultTableModel model = (DefaultTableModel) results.connections.statisticsPane.getModel();
		model.removeRow(1);
		
		FrequencyTransitionAnnotation annotation = results.annotatedTSMinerOutputs.get(processName).
				getFrequency_TransitionAnnotation(transition);
		
		// FIXME: inconsistent with Metrics Panel
		String name = transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP) + ">>" + transition.getIdentifier().toString();
		String count = Integer.toString((int) annotation.getObservations().getSum());
		String traces = Integer.toString((int) annotation.getTraces().getSum());
		String average = Float.toString(annotation.getObservations().getSum() / annotation.getTraces().getSum());
		String support = Float.toString(annotation.getObservations().getSum() / results.totalTransitionCounts.get(processName));
		
		if (transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP).equals("")) support = "";
		
		model.addRow(new Object[] {name, count, traces, average, support});
	}
	
	public void updateStateStatistics(State state) {
		DefaultTableModel model = (DefaultTableModel) results.connections.statisticsPane.getModel();
		model.removeRow(1);
		
		if (state.getLabel().contains(StateModelVisualisation.initialStateLabel)) {
			model.addRow(new Object[] {""});
		}
		else {
			FrequencyStateAnnotation frequencyAnnotation = results.annotatedTSMinerOutputs.get(processName).
					getFrequency_StateAnnotation(state);
			TimeStateAnnotation timeAnnotation = results.annotatedTSMinerOutputs.get(processName).getTime_StateAnnotation(state);
			
			String name = (String) state.getAttributeMap().get(AttributeMap.TOOLTIP);
			String count = Integer.toString((int) frequencyAnnotation.getObservations().getSum());
			String traces = Integer.toString((int) frequencyAnnotation.getTraces().getSum());
			String average = Float.toString(frequencyAnnotation.getObservations().getSum() / frequencyAnnotation.getTraces().getSum());
			String supportCount = Float.toString(frequencyAnnotation.getObservations().getSum() / results.totalStateCounts.get(processName));
			
			String medianSojourn = DurationFormatUtils.formatDuration(
					(long) timeAnnotation.getSoujourn().getMedian(),"d 'days' HH:mm:ss",true);
			String totalSojourn = DurationFormatUtils.formatDuration(
					(long) (timeAnnotation.getSoujourn().getSum() / frequencyAnnotation.getTraces().getSum()),"d 'days' HH:mm:ss",true);
			String supportTime = Float.toString(timeAnnotation.getSoujourn().getSum() / results.totalStateSojourns.get(processName));
			
			model.addRow(new Object[] {name, count, traces, average, supportCount, medianSojourn, totalSojourn, supportTime});
		}
	}

}
