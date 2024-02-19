package org.processmining.csmminer.visualisation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowFilter.ComparisonType;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.processmining.csmminer.CSMMiner;
import org.processmining.csmminer.CSMMinerResults;
import org.processmining.csmminer.relations.PerspectiveTransition;
import org.processmining.csmminer.relations.StateCounts;
import org.processmining.framework.util.Pair;
import org.processmining.framework.util.ui.widgets.ProMTable;
import org.processmining.framework.util.ui.widgets.ProMTextField;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.DirectedGraphElement;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.jgraph.elements.ProMGraphCell;
import org.processmining.models.jgraph.elements.ProMGraphEdge;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;

import com.fluxicon.slickerbox.factory.SlickerFactory;

public class MetricsPanel extends JSplitPane {
	
	private static final long serialVersionUID = 8898962032762141468L;
	
	private CSMMinerResults results;
	private VisualisationConnections connections;
	private DefaultTableModel tableModel;
	public ProMTable table;
	private List<String> conditionNamesPerRow = new ArrayList<>();
	private List<String> consequenceNamesPerRow = new ArrayList<>();
	private List<String> processNamesPerRowCondition = new ArrayList<>();
	private List<String> processNamesPerRowConsequence = new ArrayList<>();
	private List<DirectedGraphElement> graphElementsPerRowCondition = new ArrayList<>();
	private List<DirectedGraphElement> graphElementsPerRowConsequence = new ArrayList<>();
	private List<Integer> relationTypePerRow = new ArrayList<>();
	private TableRowSorter<TableModel> sorter;
	private Map<Integer,Float> minValuesPerColumn = new HashMap<>();
	private Set<Pair<String,String>> symmetricElements = new HashSet<>();
	
	private Dimension singleSize, doubleSize, tripleSize;
	private String[] relationTypes = { "State co-occurrences", "State co-occurrences (Hide symmetric half)", "Transition co-occurrences",
			"Forward-looking co-occurrences" };
	private JComboBox switchRelationTypeBox = SlickerFactory.instance().createComboBox(relationTypes);
	private JCheckBox toggleProcessNameBox = SlickerFactory.instance().createCheckBox("Show process names", false);
	private ProMTextField nameFilter = new ProMTextField("Filter by name");
	private ProMTextField minSupport = new ProMTextField("Minimum Support");
	private ProMTextField minConfidence = new ProMTextField("Minimum Confidence");
	private ProMTextField minLift = new ProMTextField("Minimum Lift");
	private ProMTextField minConviction = new ProMTextField("Minimum Conviction");
	private ProMTextField minCosine = new ProMTextField("Minimum Cosine");
	private ProMTextField minJaccard = new ProMTextField("Minimum Jaccard");
	private ProMTextField minPhi = new ProMTextField("Minimum Phi");
//	private ProMTextField minSignificance = new ProMTextField("Minimum Significance");
	
	@SuppressWarnings("serial")
	public MetricsPanel(DefaultTableModel metricsTableModel, CSMMinerResults results, VisualisationConnections connections, int width) {
		super(JSplitPane.VERTICAL_SPLIT);
		
		this.results = results;
		this.connections = connections;
		this.tableModel = metricsTableModel;
		this.table = new ProMTable(metricsTableModel);
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		
		setBackground(Color.LIGHT_GRAY);
		setBottomComponent(table);
		setTopComponent(panel);
		setResizeWeight(0.0);
		setDividerSize(0);
		setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
		
		int columnCount = table.getColumnModel().getColumnCount();
		singleSize = new Dimension((width - 0) / (columnCount + 4),23);
		doubleSize = new Dimension((width - 40) * 2 / (columnCount + 4),23);
		tripleSize = new Dimension((width - 120) * 3 / (columnCount + 4),23);
		switchRelationTypeBox.setMinimumSize(doubleSize);
		toggleProcessNameBox.setMinimumSize(singleSize);
		nameFilter.setMinimumSize(tripleSize);
		minSupport.setMinimumSize(singleSize);
		minConfidence.setMinimumSize(singleSize);
		minLift.setMinimumSize(singleSize);
		minConviction.setMinimumSize(singleSize);
		minCosine.setMinimumSize(singleSize);
		minJaccard.setMinimumSize(singleSize);
		minPhi.setMinimumSize(singleSize);
//		minSignificance.setMinimumSize(singleSize);
		
		constraints.weightx = 2;
		constraints.gridwidth = 2;
		panel.add(switchRelationTypeBox, constraints);
		constraints.weightx = 1;
		constraints.gridwidth = 1;
		panel.add(toggleProcessNameBox, constraints);
		constraints.weightx = 3;
		constraints.gridwidth = 3;
		panel.add(nameFilter, constraints);
		constraints.weightx = 1;
		constraints.gridwidth = 1;
		panel.add(minSupport, constraints);
		panel.add(minConfidence, constraints);
		panel.add(minLift, constraints);
		panel.add(minConviction, constraints);
		panel.add(minCosine, constraints);
		panel.add(minJaccard, constraints);
		panel.add(minPhi, constraints);
//		panel.add(minSignificance, constraints);
		
		panel.setPreferredSize(new Dimension(width, 23));
		panel.setMinimumSize(new Dimension(width, 23));
		panel.setBackground(Color.LIGHT_GRAY);
		
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(new RowListener());
		table.setMinimumSize(new Dimension(200, 40));
		table.setPreferredSize(new Dimension(1000, 90));
		table.getTableHeader().setPreferredSize(new Dimension(1000,20));
		table.getTableHeader().setReorderingAllowed(false);
		
		sorter = new TableRowSorter<TableModel>(table.getTable().getModel());
		table.setRowSorter(sorter);
		
		metricsTableModel.addColumn("Condition");
		metricsTableModel.addColumn("Consequence");
		metricsTableModel.addColumn("Support");
		metricsTableModel.addColumn("Confidence");
		metricsTableModel.addColumn("Lift");
		metricsTableModel.addColumn("Conviction");
		metricsTableModel.addColumn("Cosine");
		metricsTableModel.addColumn("Jaccard");
		metricsTableModel.addColumn("Phi");
//		metricsTableModel.addColumn("Significance");
		
		Enumeration<TableColumn> columns = table.getColumnModel().getColumns();
		while (columns.hasMoreElements()) {
			final TableColumn column = columns.nextElement();
			column.setPreferredWidth(width / (columnCount + 4));
			column.setCellEditor(new DefaultCellEditor(new JTextField()) {

				protected void fireEditingStopped() {
					this.cancelCellEditing();
					super.fireEditingStopped();
				}

				protected void fireEditingCanceled() {
					super.fireEditingCanceled();
				}

			});
		}
		table.getColumnModel().getColumn(0).setPreferredWidth(3*(width / (columnCount + 4)));
		table.getColumnModel().getColumn(1).setPreferredWidth(3*(width / (columnCount + 4)));
		
		addRelations();
		sorter.setRowFilter(createCombinedFilter());
		
		switchRelationTypeBox.addActionListener(new SwitchRelationTypeActionListener());
		toggleProcessNameBox.addActionListener(new ToggleProcessNameActionListener());
		nameFilter.getDocument().addDocumentListener(new NameFilterDocumentListener());
		minSupport.getDocument().addDocumentListener(new MinMetricDocumentListener(minSupport, 2));
		minConfidence.getDocument().addDocumentListener(new MinMetricDocumentListener(minConfidence, 3));
		minLift.getDocument().addDocumentListener(new MinMetricDocumentListener(minLift, 4));
		minConviction.getDocument().addDocumentListener(new MinMetricDocumentListener(minConviction, 5));
		minCosine.getDocument().addDocumentListener(new MinMetricDocumentListener(minCosine, 6));
		minJaccard.getDocument().addDocumentListener(new MinMetricDocumentListener(minJaccard, 7));
		minPhi.getDocument().addDocumentListener(new MinMetricDocumentListener(minPhi, 8));
//		minSignificance.getDocument().addDocumentListener(new MinMetricDocumentListener(minSignificance, 9));
	}
	
	private void addRelations() {
		for (String processName1 : results.tsMinerOutputs.keySet()) {
			if (processName1.equals(CSMMiner.CSMLabel)) continue;
			
			for (String processName2 : results.tsMinerOutputs.keySet()) {
				if (processName2.equals(CSMMiner.CSMLabel) || processName2.equals(processName1)) continue;
				
				TSMinerTransitionSystem model1 = results.tsMinerOutputs.get(processName1).getTransitionSystem();
				TSMinerTransitionSystem model2 = results.tsMinerOutputs.get(processName2).getTransitionSystem();
				
				// FIXME: use a special attribute for state names
				for (State state1 : model1.getNodes()) {
					String state1Name = (String) state1.getAttributeMap().get(AttributeMap.TOOLTIP);
//					String state1Name = ((String) state1.getAttributeMap().get(AttributeMap.TOOLTIP)).substring(1,
//							((String) state1.getAttributeMap().get(AttributeMap.TOOLTIP)).length()-1);
					for (State state2 : model2.getNodes()) {
						String state2Name = (String) state2.getAttributeMap().get(AttributeMap.TOOLTIP);
//						String state2Name = ((String) state2.getAttributeMap().get(AttributeMap.TOOLTIP)).substring(1,
//								((String) state2.getAttributeMap().get(AttributeMap.TOOLTIP)).length()-1);
						Pair<State, State> pair = new Pair<>(state1, state2);
						if (results.support.containsKey(pair)) {
							processNamesPerRowCondition.add(processName1);
							processNamesPerRowConsequence.add(processName2);
							conditionNamesPerRow.add(state1Name);
							consequenceNamesPerRow.add(state2Name);
							graphElementsPerRowCondition.add(state1);
							graphElementsPerRowConsequence.add(state2);
							tableModel.addRow(new Object[] { state1Name, state2Name, results.support.get(pair),
									results.confidence.get(pair), results.lift.get(pair), results.conviction.get(pair),
									results.cosine.get(pair), results.jaccard.get(pair), results.phi.get(pair),
									results.significance.get(pair)});
							if (!symmetricElements.contains(new Pair<>(state2Name, state1Name))) {
								symmetricElements.add(new Pair<String, String>(state1Name, state2Name));
								relationTypePerRow.add(1);
							}
							else {
								relationTypePerRow.add(0);
							}
						}
					}
				}
				
				for (Transition transition : model1.getEdges()) {
					String transitionName = ((String) transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP))
							+ " ==> " + ((String) transition.getTarget().getAttributeMap().get(AttributeMap.TOOLTIP));
//					String transitionName = ((String) transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP)).substring(1,
//							((String) transition.getSource().getAttributeMap().get(AttributeMap.TOOLTIP)).length()-1)
//							+ " ==> " + ((String) transition.getTarget().getAttributeMap().get(AttributeMap.TOOLTIP)).substring(1,
//									((String) transition.getTarget().getAttributeMap().get(AttributeMap.TOOLTIP)).length()-1);
					for (State state : model2.getNodes()) {
						String stateName = (String) state.getAttributeMap().get(AttributeMap.TOOLTIP);
//						String stateName = ((String) state.getAttributeMap().get(AttributeMap.TOOLTIP)).substring(1,
//								((String) state.getAttributeMap().get(AttributeMap.TOOLTIP)).length()-1);
						Pair<Object, Object> pair = new Pair<Object, Object>(transition, state);
						if (results.support.containsKey(pair)) {
							processNamesPerRowCondition.add(processName1);
							processNamesPerRowConsequence.add(processName2);
							conditionNamesPerRow.add(transitionName);
							consequenceNamesPerRow.add(stateName);
							graphElementsPerRowCondition.add(transition);
							graphElementsPerRowConsequence.add(state);
							tableModel.addRow(new Object[] { transitionName, stateName, results.support.get(pair),
									results.confidence.get(pair), results.lift.get(pair), results.conviction.get(pair),
									results.cosine.get(pair), results.jaccard.get(pair), results.phi.get(pair),
									results.significance.get(pair)});
							relationTypePerRow.add(2);
						}
						
						pair = new Pair<Object, Object>(state, transition);
						if (results.support.containsKey(pair)) {
							processNamesPerRowCondition.add(processName2);
							processNamesPerRowConsequence.add(processName1);
							conditionNamesPerRow.add(stateName);
							consequenceNamesPerRow.add(transitionName);
							graphElementsPerRowCondition.add(state);
							graphElementsPerRowConsequence.add(transition);
							tableModel.addRow(new Object[] { stateName, transitionName, results.support.get(pair),
									results.confidence.get(pair), results.lift.get(pair), results.conviction.get(pair),
									results.cosine.get(pair), results.jaccard.get(pair), results.phi.get(pair),
									results.significance.get(pair)});
							relationTypePerRow.add(3);
						}
					}
				}
			}
		}
	}
	
	private RowFilter<Object, Object> createRelationTypeFilter() {
		RowFilter<Object, Object> filter = new RowFilter<Object,Object>() {

			public boolean include(javax.swing.RowFilter.Entry<? extends Object, ? extends Object> entry) {
				if (switchRelationTypeBox.getSelectedIndex() == 0) {
					return relationTypePerRow.get((Integer) entry.getIdentifier()) <= 1;
				}
				return switchRelationTypeBox.getSelectedIndex() == relationTypePerRow.get((Integer) entry.getIdentifier());
			}
			
		};
		
		return filter;
	}
	
	private RowFilter<Object, Object> createNameFilter() {
		RowFilter<Object, Object> filter = new RowFilter<Object,Object>() {

			public boolean include(javax.swing.RowFilter.Entry<? extends Object, ? extends Object> entry) {
				if (nameFilter.getText().equals("Filter by name")) return true;
				return entry.getStringValue(0).contains(nameFilter.getText()) || entry.getStringValue(1).contains(nameFilter.getText());
			}
			
		};
		
		return filter;
	}
	
	private RowFilter<Object, Object> createMinValueFilter() {
		RowFilter<Object, Object> filter = null;
		List<RowFilter<Object,Object>> filters = new ArrayList<>();
		for (Integer index : minValuesPerColumn.keySet()) {
			List<RowFilter<Object,Object>> greaterEqual = new ArrayList<>();
			greaterEqual.add(RowFilter.numberFilter(ComparisonType.AFTER, minValuesPerColumn.get(index), index));
			greaterEqual.add(RowFilter.numberFilter(ComparisonType.EQUAL, minValuesPerColumn.get(index), index));
			filters.add(RowFilter.orFilter(greaterEqual));
		}
		
		if (filters.size() > 0) {
			filter = RowFilter.andFilter(filters);
		}
		
		return filter;
	}
	
	private RowFilter<Object, Object> createCombinedFilter() {
		List<RowFilter<Object,Object>> filters = new ArrayList<>();
		filters.add(createRelationTypeFilter());
		filters.add(createNameFilter());
		
		RowFilter<Object, Object> valueFilter = createMinValueFilter();
		if (valueFilter != null) {
			filters.add(valueFilter);
		}
		
		return RowFilter.andFilter(filters);
	}
	
	private class SwitchRelationTypeActionListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			sorter.setRowFilter(createCombinedFilter());
		}
		
	}
	
	private class ToggleProcessNameActionListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			for (int i = 0; i < tableModel.getRowCount(); i++) {
				if (toggleProcessNameBox.isSelected()) {
					tableModel.setValueAt(processNamesPerRowCondition.get(i) + ": " + conditionNamesPerRow.get(i), i, 0);
					tableModel.setValueAt(processNamesPerRowConsequence.get(i) + ": " + consequenceNamesPerRow.get(i), i, 1);
				}
				else {
					tableModel.setValueAt(conditionNamesPerRow.get(i), i, 0);
					tableModel.setValueAt(consequenceNamesPerRow.get(i), i, 1);
				}
			}
			
			sorter.setRowFilter(createCombinedFilter());
		}
		
	}
	
	private class NameFilterDocumentListener implements DocumentListener {

		public void insertUpdate(DocumentEvent e) {
			sorter.setRowFilter(createCombinedFilter());
		}

		public void removeUpdate(DocumentEvent e) {
			sorter.setRowFilter(createCombinedFilter());
		}

		public void changedUpdate(DocumentEvent e) {
			sorter.setRowFilter(createCombinedFilter());
		}
		
	}
	
	private class MinMetricDocumentListener implements DocumentListener {
		
		private ProMTextField textField;
		private Integer columnIndex;
		
		public MinMetricDocumentListener(ProMTextField textField, int columnIndex) {
			this.textField = textField;
			this.columnIndex = columnIndex;
		}

		public void insertUpdate(DocumentEvent e) {
			updateFilter();
		}

		public void removeUpdate(DocumentEvent e) {
			updateFilter();
		}

		public void changedUpdate(DocumentEvent e) {
			updateFilter();
		}
		
		public void updateFilter() {
			try {
				Float minValue = Float.parseFloat(textField.getText());
				minValuesPerColumn.put(columnIndex, minValue);
			}
			catch (Exception e) {
				minValuesPerColumn.remove(columnIndex);
			}
			
			sorter.setRowFilter(createCombinedFilter());
		}
		
	}
	
	private class RowListener implements ListSelectionListener {

		public void valueChanged(ListSelectionEvent event) {
			if (table.getSelectedRow() < 0) {
				return;
			}
			
			int modelIndex = table.getTable().convertRowIndexToModel(table.getSelectedRow());
			
			for (String name : results.connections.graphWrappers.keySet()) {
				if (name.equals(processNamesPerRowCondition.get(modelIndex)) || name.equals(processNamesPerRowConsequence.get(modelIndex))) {
					results.connections.graphWrappers.get(name).setVisible(true);
					(new GraphRefreshHelper(results.connections.topComponent, results.connections.graphPanels.get(name).getGraph())).start();
				}
				else {
					results.connections.graphWrappers.get(name).setVisible(false);
				}
			}
			results.connections.topComponent.validate();
			
			if (event.getValueIsAdjusting()) {
				return;
			}
			
			StateModelVisualisationListener listener = connections.modelListeners.get(processNamesPerRowCondition.get(modelIndex));
			listener.graphPanel.getGraph().setHighlightColor(Color.RED);
			listener.resetNodeAndEdgeColours();
			listener.resetStatistics();
			
			if (relationTypePerRow.get(modelIndex) == 2) {
				Transition transitionElement = (Transition) graphElementsPerRowCondition.get(modelIndex);
				transitionElement.getAttributeMap().put(AttributeMap.EDGECOLOR, Color.RED);
				String source = transitionElement.getSource().getIdentifier().toString();
				String target = transitionElement.getTarget().getIdentifier().toString();
				String sourceName = source.substring(1, source.length()-1);
				String targetName = target.substring(1, target.length()-1);
				
				// Only works under assumption that states are identified with their name as in the event log
				PerspectiveTransition transition = new PerspectiveTransition(processNamesPerRowCondition.get(modelIndex),
						sourceName, targetName);
				StateCounts stateCounts = results.transitionsCooccurringStates.get(processNamesPerRowCondition.get(modelIndex),
						transitionElement);
				
				if (stateCounts != null) {
					listener.updateNodeColoursForTransition(transitionElement, stateCounts);
					listener.updateEdgeColoursForTransition(transition, stateCounts);
				}
				else if (processNamesPerRowCondition.get(modelIndex).equals(CSMMiner.CSMLabel)) {
					listener.updateColoursForSynchronousProductTransition((transitionElement.getSource()),
							(transitionElement.getTarget()));
				}
				
				listener.updateTransitionStatistics(transitionElement);
			}
			else {
				listener.updateNodeColoursForState((State) graphElementsPerRowCondition.get(modelIndex));
				listener.updateEdgeColorsForState((State) graphElementsPerRowCondition.get(modelIndex));
				listener.updateStateStatistics((State) graphElementsPerRowCondition.get(modelIndex));
			}
			
			for (Object selectable : listener.graphPanel.getGraph().getSelectionModel().getSelectables()) {
				if (selectable instanceof ProMGraphCell) {
					if (((ProMGraphCell) selectable).getNode().equals(graphElementsPerRowCondition.get(modelIndex))) {
						listener.graphPanel.getGraph().setSelectionCell(selectable);
					}
				}
				if (selectable instanceof ProMGraphEdge) {
					if (((ProMGraphEdge) selectable).getEdge().equals(graphElementsPerRowCondition.get(modelIndex))) {
						listener.graphPanel.getGraph().setSelectionCell(selectable);
					}
				}
			}
			
			graphElementsPerRowConsequence.get(modelIndex).getAttributeMap().put(AttributeMap.STROKECOLOR, Color.BLUE);
			graphElementsPerRowConsequence.get(modelIndex).getAttributeMap().put(AttributeMap.EDGECOLOR, Color.BLUE);
			
			listener.clearOldSelection();
			listener.refreshAllGraphs();
			
			String interactionDescription = "";
			switch (relationTypePerRow.get(modelIndex)) {
				case 0: case 1:
					interactionDescription = 100*(float)tableModel.getValueAt(modelIndex, 3) + "% of the total time spent in [" +
							tableModel.getValueAt(modelIndex, 0) + "] is spent while being in [" + tableModel.getValueAt(modelIndex, 1) +
							"]";
					break;
				case 2:
					Transition transitionElement = (Transition) graphElementsPerRowCondition.get(modelIndex);
					String source = transitionElement.getSource().getIdentifier().toString();
					String target = transitionElement.getTarget().getIdentifier().toString();
					String sourceName = source.substring(1, source.length()-1);
					String targetName = target.substring(1, target.length()-1);
					
					if (toggleProcessNameBox.isSelected()) {
						interactionDescription = "Transitions from [" + processNamesPerRowCondition.get(modelIndex) + ": " + sourceName +
								"] to [" + targetName + "] occur " + 100*(float)tableModel.getValueAt(modelIndex, 3) +
								"% of the times while being in [" + tableModel.getValueAt(modelIndex, 1) + "]";
					}
					else {
						interactionDescription = "Transitions from [" + sourceName +
								"] to [" + targetName + "] occur " + 100*(float)tableModel.getValueAt(modelIndex, 3) +
								"% of the times while being in [" + tableModel.getValueAt(modelIndex, 1) + "]";
					}
					
					break;
				case 3:
					transitionElement = (Transition) graphElementsPerRowConsequence.get(modelIndex);
					source = transitionElement.getSource().getIdentifier().toString();
					target = transitionElement.getTarget().getIdentifier().toString();
					sourceName = source.substring(1, source.length()-1);
					targetName = target.substring(1, target.length()-1);
					
					float normal = 100 * ((float)tableModel.getValueAt(modelIndex, 3) / (float)tableModel.getValueAt(modelIndex, 4));
					if (toggleProcessNameBox.isSelected()) {
						interactionDescription = "A transition from [" + processNamesPerRowConsequence.get(modelIndex) + ": " + sourceName +
								"] goes " + 100 * (float)tableModel.getValueAt(modelIndex, 3) + "% of the times to [" + targetName +
								"] while being in [" + tableModel.getValueAt(modelIndex, 0) +"] (compared to " + normal + "% on average)";
					}
					else {
						interactionDescription = "A transition from [" + sourceName +
								"] goes " + 100 * (float)tableModel.getValueAt(modelIndex, 3) + "% of the times to [" + targetName +
								"] while being in [" + tableModel.getValueAt(modelIndex, 0) +"] (compared to " + normal + "% on average)";
					}
			}
			
			((JLabel)results.connections.interactionPane.getComponent(0)).setText(interactionDescription);
			
			results.connections.graphStatisticsWrapper.setBottomComponent(results.connections.interactionPane);
			results.connections.interactionPane.repaint();
		}
	}
}
