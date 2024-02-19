package org.processmining.csmminer.visualisation;

import info.clearthought.layout.TableLayout;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.csmminer.CSMMiner;
import org.processmining.csmminer.CSMMinerResults;
import org.processmining.framework.connections.Connection;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.connections.ConnectionID;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.util.Pair;
import org.processmining.framework.util.ui.scalableview.ScalableComponent;
import org.processmining.framework.util.ui.scalableview.ScalableViewPanel;
import org.processmining.framework.util.ui.scalableview.interaction.ViewInteractionPanel;
import org.processmining.models.connections.GraphLayoutConnection;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.DirectedGraph;
import org.processmining.models.graphbased.directed.DirectedGraphElementWeights;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.jgraph.ProMJGraphVisualizer;
import org.processmining.models.jgraph.visualization.ProMJGraphPanel;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyTransitionAnnotation;

import com.fluxicon.slickerbox.components.NiceIntegerSlider;
import com.fluxicon.slickerbox.components.SlickerButton;

public class StateModelTransitionFilterComponent extends JPanel implements ActionListener, ViewInteractionPanel {

	private static final long serialVersionUID = -3710929196431016518L;
	
	private UIPluginContext context;
	private CSMMinerResults results;
	private String processName;
	private ProMJGraphPanel parentPanel;
	private TSMinerTransitionSystem transitionSystem;
	private TSMinerTransitionSystem synchronousProductTS;
	private DirectedGraphElementWeights weights;
	
	private JPanel componentPanel;
	private NiceIntegerSlider slider;
	private JLabel label;
	private SlickerButton filterButton;
	
	private Set<Transition> removedTransitions;
	private Set<Transition> removedSynchronousTransitions;

	public StateModelTransitionFilterComponent(UIPluginContext context, CSMMinerResults results, String processName,
			ProMJGraphPanel parentPanel, int sliderStart, Set<Transition> removedTransitions,
			Set<Transition> removedSynchronousTransitions) {
		
		this.context = context;
		this.results = results;
		this.processName = processName;
		this.parentPanel = parentPanel;
		transitionSystem = results.tsMinerOutputs.get(processName).getTransitionSystem();
		synchronousProductTS = results.tsMinerOutputs.get(CSMMiner.CSMLabel).getTransitionSystem();
		weights = results.tsMinerOutputs.get(processName).getWeights();
		
		this.removedTransitions = removedTransitions;
		this.removedSynchronousTransitions = removedSynchronousTransitions;
		
		double size[][] = { { 175, 75 }, { 25, 25, 25 } };
		componentPanel = new JPanel();
		componentPanel.setLayout(new TableLayout(size));
		componentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		componentPanel.setOpaque(true);
		
		int maxTransitionCount = 1;
		for (Transition transition : transitionSystem.getEdges()) {
			int weight = weights.get(transition.getSource().getIdentifier(), transition.getTarget().getIdentifier(),
					transition.getIdentifier(), 1);
			maxTransitionCount = Math.max(maxTransitionCount, weight);
		}
		
		slider = new NiceIntegerSlider("Arc count", 0, Math.max(maxTransitionCount, 1), sliderStart);
		componentPanel.add(slider, "0, 0, 1, 0");
		
		slider.addChangeListener(new ChangeListener() {

			public void stateChanged(ChangeEvent e) {
				sliderChanged(e);
			}
			
		});

		label = new JLabel("<html>Filter out state transitions from the model <br/> that occur less than the count.</html>");
		componentPanel.add(label, "0, 1, 1, 2");
		
		filterButton = new SlickerButton("Filter arcs");
		filterButton.setFont(filterButton.getFont().deriveFont(11f));
		filterButton.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 12));
		filterButton.setOpaque(false);
		filterButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		filterButton.setHorizontalAlignment(SwingConstants.LEFT);
		filterButton.addActionListener(this);
		componentPanel.add(filterButton, "1, 2");
	}
	
	public void sliderChanged(ChangeEvent e) {
		for (Transition transition : transitionSystem.getEdges()) {
			int weight = weights.get(transition.getSource().getIdentifier(), transition.getTarget().getIdentifier(),
					transition.getIdentifier(), 1);
			if (weight < slider.getValue()) {
				Set<Pair<String, Transition>> mappedTransitions = results.transitionMap.getMappedTransitions(transition);
				if (mappedTransitions == null) continue;
				
				for (Pair<String, Transition> processTransition : mappedTransitions) {
					removedSynchronousTransitions.add(processTransition.getSecond());
					synchronousProductTS.removeEdge(processTransition.getSecond());
				}
				
				removedTransitions.add(transition);
			}
		}
		
		Set<Transition> addedTransitions = new HashSet<>();
		for (Transition transition : removedTransitions) {
			transitionSystem.removeEdge(transition);
			int weight = weights.get(transition.getSource().getIdentifier(), transition.getTarget().getIdentifier(),
					transition.getIdentifier(), 1);
			
			if (weight >= slider.getValue()) {
				Set<Pair<String, Transition>> mappedTransitions = results.transitionMap.getMappedTransitions(transition);
				
				for (Pair<String, Transition> processTransition : mappedTransitions) {
					synchronousProductTS.addTransition(processTransition.getSecond().getSource().getIdentifier(),
							processTransition.getSecond().getTarget().getIdentifier(), processTransition.getSecond().getIdentifier());
					removedSynchronousTransitions.remove(processTransition.getSecond());
				}
				
				transitionSystem.addTransition(transition.getSource().getIdentifier(), transition.getTarget().getIdentifier(),
						transition.getIdentifier());
				addedTransitions.add(transition);
			}
		}
		
		removedTransitions.removeAll(addedTransitions);
	}
	
	@Override
	public JComponent getComponent() {
		return componentPanel;
	}

	@Override
	public double getHeightInView() {
		return 65;
	}

	@Override
	public String getPanelName() {
		return "Filter arcs";
	}

	@Override
	public double getWidthInView() {
		return 250;
	}

	@Override
	public void setParent(ScalableViewPanel viewPanel) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setScalableComponent(ScalableComponent scalable) {
		// TODO Auto-generated method stub
	}

	@Override
	public void willChangeVisibility(boolean to) {
		// TODO Auto-generated method stub
	}

	@Override
	public void updated() {
		// TODO Auto-generated method stub
	}

	public void actionPerformed(ActionEvent e) {
		findConnection(context, transitionSystem).removeListener(parentPanel.getGraph());
		findConnection(context, synchronousProductTS).removeListener(
				((ProMJGraphPanel) ((Container) parentPanel.getParent().getParent().getComponent(0)).getComponent(1)).getGraph());
		
		// Fix transition appearance
		repairTransitionAppearance(transitionSystem, processName);
		repairTransitionAppearance(synchronousProductTS, CSMMiner.CSMLabel);
		
		// Update state model
		Container wrapper = parentPanel.getParent();
		parentPanel.removeMouseListener(results.connections.modelListeners.get(processName));
		wrapper.remove(1);
		
		ProMJGraphPanel newGraph = ProMJGraphVisualizer.instance().visualizeGraph(context, transitionSystem);
		newGraph.addViewInteractionPanel(new StateModelTransitionFilterComponent(context, results, processName, newGraph,
				slider.getValue(), removedTransitions, removedSynchronousTransitions), SwingConstants.SOUTH);
		newGraph.addViewInteractionPanel(new StateModelStateTransformationComponent(context, results, processName, newGraph),
				SwingConstants.SOUTH);
		// TODO: Expert Option
//		newGraph.addViewInteractionPanel(new StateModelHighlightingComponent(results, processName), SwingConstants.SOUTH);
		repairTransitionLabels(transitionSystem, processName);
		results.connections.graphPanels.put(processName, newGraph);
		
		StateModelVisualisationListener listener = new StateModelVisualisationListener(results, processName);
		newGraph.getGraph().addMouseListener(listener);
		results.connections.modelListeners.put(processName, listener);
		wrapper.add(newGraph);
		
		// Update the synchronous product model
		Container synchronousWrapper = (Container) wrapper.getParent().getComponent(0);
		synchronousWrapper.getComponent(1).removeMouseListener(results.connections.modelListeners.get(CSMMiner.CSMLabel));
		synchronousWrapper.remove(1);
		
		newGraph = ProMJGraphVisualizer.instance().visualizeGraph(context, synchronousProductTS);
		repairTransitionLabels(synchronousProductTS, CSMMiner.CSMLabel);
		results.connections.graphPanels.put(CSMMiner.CSMLabel, newGraph);
		
		listener = new StateModelVisualisationListener(results, CSMMiner.CSMLabel);
		newGraph.getGraph().addMouseListener(listener);
		results.connections.modelListeners.put(CSMMiner.CSMLabel, listener);
		synchronousWrapper.add(newGraph);
		
		// Clear selection and highlights in all models
		results.connections.modelListeners.get(processName).resetNodeAndEdgeColours();
		results.connections.modelListeners.get(processName).refreshAllGraphs();
		
		wrapper.revalidate();
		wrapper.repaint();
		synchronousWrapper.revalidate();
		synchronousWrapper.repaint();
	}
	
	private GraphLayoutConnection findConnection(PluginContext context, DirectedGraph<?, ?> graph) {
		Collection<ConnectionID> cids = context.getConnectionManager().getConnectionIDs();
		for (ConnectionID id : cids) {
			Connection c;
			try {
				c = context.getConnectionManager().getConnection(id);
			} catch (ConnectionCannotBeObtained e) {
				continue;
			}
			if (c != null && !c.isRemoved() && c instanceof GraphLayoutConnection
					&& c.getObjectWithRole(GraphLayoutConnection.GRAPH) == graph) {
				return (GraphLayoutConnection) c;
			}
		}
		return null;
	}
	
	public void repairTransitionAppearance(TSMinerTransitionSystem transitionSystem, String processName) {
		DirectedGraphElementWeights repairWeights = results.tsMinerOutputs.get(processName).getWeights();
		
		for (Transition transition : transitionSystem.getEdges()) {
			int weight = repairWeights.get(transition.getSource().getIdentifier(), transition.getTarget()
					.getIdentifier(), transition.getIdentifier(), 1);
			transition.getAttributeMap().put(
					AttributeMap.LINEWIDTH,Math.min(4,	new Float(1	+ Math.log(Math.E) * Math.log(weight))));
			transition.getAttributeMap().put(AttributeMap.LABELCOLOR, Color.GRAY);
			transition.setLabel(String.valueOf(weight) + "/" + String.valueOf(weight) + " (100.0%)");
		}
	}
	
	public void repairTransitionLabels(TSMinerTransitionSystem transitionSystem, String processName) {
		DirectedGraphElementWeights repairWeights = results.tsMinerOutputs.get(processName).getWeights();
		DecimalFormat df = new DecimalFormat("0.#");
		Map<State,Integer> stateTransitionCounts = new HashMap<>();
		
		for (State state : transitionSystem.getNodes()) {
			int count = 0;
			
			for (Transition transition : transitionSystem.getOutEdges(state)) {
				if (!transitionSystem.getEdges(transition.getIdentifier()).contains(transition)) continue;
				FrequencyTransitionAnnotation annotation = results.annotatedTSMinerOutputs.get(processName).
						getFrequency_TransitionAnnotation(transition);
				count += annotation.getObservations().getSum();
			}
			
			stateTransitionCounts.put(state, count);
		}
		
		
		for (Transition transition : transitionSystem.getEdges()) {
			float weight = repairWeights.get(transition.getSource().getIdentifier(), transition.getTarget()
					.getIdentifier(), transition.getIdentifier(), 1);
			float confidence = weight / stateTransitionCounts.get(transition.getSource());
			transition.setLabel(String.valueOf((int) weight) + " (" + 
					(df.format(confidence * 100)) + "%)");
			transition.getAttributeMap().put(AttributeMap.EDGECOLOR, new Color(0, 0, 0, 10 + (int) (confidence * 245)));
			transition.getAttributeMap().put(StateModelVisualisation.confidenceName, confidence);
		}
	}
}
