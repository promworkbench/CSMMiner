package org.processmining.csmminer.visualisation;

import info.clearthought.layout.TableLayout;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.csmminer.CSMMinerResults;
import org.processmining.csmminer.LogProcessor;
import org.processmining.csmminer.plugins.CSMMinerPlugin;
import org.processmining.csmminer.plugins.CSMMinerVisualisationPlugin;
import org.processmining.framework.util.ui.scalableview.ScalableComponent;
import org.processmining.framework.util.ui.scalableview.ScalableViewPanel;
import org.processmining.framework.util.ui.scalableview.interaction.ViewInteractionPanel;
import org.processmining.framework.util.ui.widgets.ProMSplitPane;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.DirectedGraphNode;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.jgraph.visualization.ProMJGraphPanel;

import com.fluxicon.slickerbox.components.SlickerButton;

public class StateModelStateTransformationComponent extends JPanel implements ViewInteractionPanel {

	private static final long serialVersionUID = -7905381673826412163L;
	
	XFactory factory = XFactoryRegistry.instance().currentDefault();
	
	private UIPluginContext context;
	private CSMMinerResults results;
	private String processName;
	private ProMJGraphPanel parentPanel;
	
	// TODO: add text entry to name groups, store name in attributemap and also access other transformation components to update log
	private JPanel componentPanel;
	private SlickerButton groupButton;
	private SlickerButton unGroupButton;
	private SlickerButton removeButton;
	private SlickerButton unRemoveButton;
	private SlickerButton transformButton;
	
	// TODO: remove dependency on mouselistener
	public StateModelVisualisationListener modelListener;
	private List<Set<State>> groupedStates = new ArrayList<>();
	private Set<State> removedStates = new HashSet<>();
	
	public StateModelStateTransformationComponent(UIPluginContext context, CSMMinerResults results, String processName,
			ProMJGraphPanel parentPanel) {
		
		this.context = context;
		this.results = results;
		this.processName = processName;
		this.parentPanel = parentPanel;
		
		modelListener = results.connections.modelListeners.get(processName);
		
		groupedStates.add(new HashSet<State>());
		
		double size[][] = { { 125, 125 }, { 25, 25, 15, 25 } };
		componentPanel = new JPanel();
		componentPanel.setLayout(new TableLayout(size));
		componentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		componentPanel.setOpaque(true);
		
		groupButton = new SlickerButton("Group States");
		groupButton.setFont(groupButton.getFont().deriveFont(11f));
		groupButton.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 12));
		groupButton.setOpaque(false);
		groupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		groupButton.setHorizontalAlignment(SwingConstants.LEFT);
		groupButton.addActionListener(new GroupStatesActionListener());
		componentPanel.add(groupButton, "0, 0");
		
		unGroupButton = new SlickerButton("Ungroup States");
		unGroupButton.setFont(unGroupButton.getFont().deriveFont(11f));
		unGroupButton.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 12));
		unGroupButton.setOpaque(false);
		unGroupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		unGroupButton.setHorizontalAlignment(SwingConstants.LEFT);
		unGroupButton.addActionListener(new UnGroupStatesActionListener());
		componentPanel.add(unGroupButton, "1, 0");
		
		removeButton = new SlickerButton("Remove States");
		removeButton.setFont(removeButton.getFont().deriveFont(11f));
		removeButton.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 12));
		removeButton.setOpaque(false);
		removeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		removeButton.setHorizontalAlignment(SwingConstants.LEFT);
		removeButton.addActionListener(new RemoveStatesActionListener());
		componentPanel.add(removeButton, "0, 1");
		
		unRemoveButton = new SlickerButton("Unremove States");
		unRemoveButton.setFont(unRemoveButton.getFont().deriveFont(11f));
		unRemoveButton.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 12));
		unRemoveButton.setOpaque(false);
		unRemoveButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		unRemoveButton.setHorizontalAlignment(SwingConstants.LEFT);
		unRemoveButton.addActionListener(new UnRemoveStatesActionListener());
		componentPanel.add(unRemoveButton, "1, 1");
		
		transformButton = new SlickerButton("Transform the Model");
		transformButton.setFont(transformButton.getFont().deriveFont(11f));
		transformButton.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 12));
		transformButton.setOpaque(false);
		transformButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		transformButton.setHorizontalAlignment(SwingConstants.LEFT);
		transformButton.addActionListener(new TransformModelActionListener());
		componentPanel.add(transformButton, "0, 3, 1, 3");
	}

	public JComponent getComponent() {
		return componentPanel;
	}

	public double getHeightInView() {
		return 80;
	}

	public String getPanelName() {
		return "Transform";
	}

	public double getWidthInView() {
		return 250;
	}

	public void setParent(ScalableViewPanel viewPanel) {
		// TODO Auto-generated method stub
		
	}

	public void setScalableComponent(ScalableComponent scalable) {
		// TODO Auto-generated method stub
		
	}

	public void updated() {
		// TODO Auto-generated method stub
		
	}

	public void willChangeVisibility(boolean to) {
		// TODO Auto-generated method stub
		
	}

	private class GroupStatesActionListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			Collection<DirectedGraphNode> selectedNodes = parentPanel.getSelectedNodes();
			Set<State> selected = new HashSet<>();
			removedStates.removeAll(selectedNodes);
			
			for (DirectedGraphNode state : selectedNodes) {
				if (!state.getLabel().contains(StateModelVisualisation.initialStateLabel)) selected.add((State) state);
			}
			
			// Clear old groupings
			for (Set<State> states : groupedStates) {
				states.removeAll(selected);
			}
			
			if (selected.size() == 1) {
				groupedStates.get(groupedStates.size()-1).addAll(selected);
			}
			else if (selected.size() > 1) {
				groupedStates.add(new HashSet<State>());
				groupedStates.get(groupedStates.size()-1).addAll(selected);
			}
			
			// Remove empty groupings
			List<Set<State>> toRemove = new ArrayList<>();
			for (Set<State> states : groupedStates) {
				if (states.size() < 2 && !states.equals(groupedStates.get(groupedStates.size()-1))) toRemove.add(states);
			}
			groupedStates.removeAll(toRemove);
			
			updateColoring(toRemove);
//			System.out.println(groupedStates);
		}
		
	}
	
	private class UnGroupStatesActionListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			List<Set<State>> toRemove = new ArrayList<>();
			Collection<DirectedGraphNode> selectedNodes = parentPanel.getSelectedNodes();
			Set<State> selected = new HashSet<>();
			
			for (DirectedGraphNode state : selectedNodes) {
				selected.add((State) state);
			}
			
			toRemove.add(new HashSet<State>());
			toRemove.get(0).addAll(selected);
			
			for (Set<State> states : groupedStates) {
				states.removeAll(selected);
			}
			
			// Remove empty groupings
			for (Set<State> states : groupedStates) {
				if (states.size() < 2 && !states.equals(groupedStates.get(groupedStates.size()-1))) toRemove.add(states);
			}
			groupedStates.removeAll(toRemove);
			
			updateColoring(toRemove);
//			System.out.println(groupedStates);
		}
		
	}
	
	public void updateColoring(List<Set<State>> toRemove) {
		for (int i = 0; i < groupedStates.size(); i++) {
			Set<State> states = groupedStates.get(i);
			int graphSize = Math.max(1, results.tsMinerOutputs.get(processName).getTransitionSystem().getNodes().size() / 2 - 1);
			Color color = new Color(0, 255 - 128/graphSize*i, 0);

			for (State state : states) {
				state.getAttributeMap().put(AttributeMap.FILLCOLOR, color);
				modelListener.oldStateColor.put(state, color);
			}

			for (Set<State> set : toRemove) {
				for (State state : set) {
					if (!removedStates.contains(state)) {
						state.getAttributeMap().put(AttributeMap.FILLCOLOR, Color.LIGHT_GRAY);
						modelListener.oldStateColor.put(state, Color.LIGHT_GRAY);
					}
				}
			}
		}

		parentPanel.getGraph().refresh();
	}
	
	private class RemoveStatesActionListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			List<Set<State>> toRemove = new ArrayList<>();
			Collection<DirectedGraphNode> selectedNodes = parentPanel.getSelectedNodes();
			Set<State> selected = new HashSet<>();
			
			for (DirectedGraphNode state : selectedNodes) {
				if (!state.getLabel().contains(StateModelVisualisation.initialStateLabel)) selected.add((State) state);
			}
			
			// Clear groupedStates first
			toRemove.add(new HashSet<State>());
			toRemove.get(0).addAll(selected);
			
			for (Set<State> states : groupedStates) {
				states.removeAll(selected);
			}
			
			// Remove empty groupings
			for (Set<State> states : groupedStates) {
				if (states.size() < 2 && !states.equals(groupedStates.get(groupedStates.size()-1))) toRemove.add(states);
			}
			groupedStates.removeAll(toRemove);
			
			if (groupedStates.isEmpty()) {
				groupedStates.add(new HashSet<State>());
			}
			
			updateColoring(toRemove);
			
			for (DirectedGraphNode state : parentPanel.getSelectedNodes()) {
				if (state.getLabel().contains(StateModelVisualisation.initialStateLabel)) continue;
				removedStates.add((State) state);
				state.getAttributeMap().put(AttributeMap.FILLCOLOR, Color.RED);
				modelListener.oldStateColor.put((State) state, Color.RED);
			}
			
			parentPanel.getGraph().refresh();
//			System.out.println(removedStates);
		}
		
	}
	
	private class UnRemoveStatesActionListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			for (DirectedGraphNode state : parentPanel.getSelectedNodes()) {
				boolean grouped = false;
				for (Set<State> states : groupedStates) {
					if (states.contains(state)) {
						grouped = true;
						break;
					}
				}
				
				if (!grouped) {
					removedStates.remove(state);
					state.getAttributeMap().put(AttributeMap.FILLCOLOR, Color.LIGHT_GRAY);
					modelListener.oldStateColor.put((State) state, Color.LIGHT_GRAY);
				}
			}
			
			parentPanel.getGraph().refresh();
//			System.out.println(removedStates);
		}
		
	}
	
	private class TransformModelActionListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			results.connections.progressUI.showProgress();
			results.connections.modelListeners.get(processName).resetNodeAndEdgeColours();
			results.connections.modelListeners.get(processName).refreshAllGraphs();
			parentPanel.getGraph().refresh();
			
			new LogTransformer().execute();
			
			System.out.println("Previous computation: " + results.computationTime + "ms");
		}
		
		class LogTransformer extends SwingWorker<XLog, Object> {

			protected XLog doInBackground() throws Exception {
				// TODO: do it directly on the existing logs
				XLog newLog = LogProcessor.transformLog(results.log, processName, removedStates, groupedStates);
				CSMMinerResults newResults = (CSMMinerResults) new CSMMinerPlugin().mineCompositeStateMachine(context, newLog)[0];
				new CSMMinerVisualisationPlugin().visualize(context, newResults);
				
				ProMSplitPane splitPane = results.connections.topComponent;
				splitPane.setTopComponent(newResults.connections.graphStatisticsWrapper);
				splitPane.setBottomComponent(newResults.connections.metricsPane);
				newResults.connections.topComponent = splitPane;
				
				for (String processname : results.connections.graphPanels.keySet()) {
					results.connections.graphPanels.get(processname).removeMouseListener(
							results.connections.modelListeners.get(processname));
				}
				
				return newLog;
			}
			
		}
		
	}
}
