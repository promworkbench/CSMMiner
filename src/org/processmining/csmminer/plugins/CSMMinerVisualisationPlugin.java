package org.processmining.csmminer.plugins;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.Visualizer;
import org.processmining.csmminer.CSMMiner;
import org.processmining.csmminer.CSMMinerResults;
import org.processmining.csmminer.visualisation.GraphRefreshHelper;
import org.processmining.csmminer.visualisation.ImprovedSplitPaneDivider;
import org.processmining.csmminer.visualisation.MetricsPanel;
import org.processmining.csmminer.visualisation.ProgressUI;
import org.processmining.csmminer.visualisation.StateModelVisualisation;
import org.processmining.csmminer.visualisation.StateModelVisualisationListener;
import org.processmining.csmminer.visualisation.VisualisationConnections;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.framework.util.ui.widgets.ProMList;
import org.processmining.framework.util.ui.widgets.ProMScrollPane;
import org.processmining.framework.util.ui.widgets.ProMSplitPane;
import org.processmining.framework.util.ui.widgets.ProMTableWithoutPanel;
import org.processmining.framework.util.ui.widgets.WidgetColors;
import org.processmining.models.jgraph.visualization.ProMJGraphPanel;
import org.processmining.plugins.log.ui.logdialog.SlickerOpenLogSettings;
import org.processmining.plugins.utils.ProvidedObjectHelper;

import com.fluxicon.slickerbox.factory.SlickerFactory;

public class CSMMinerVisualisationPlugin {
	
	final int numberProcessesShown = 3;
	final GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
	final int processWidth = gd.getDisplayMode().getWidth() / numberProcessesShown;
	
	public VisualisationConnections connections;
	
	@Plugin(
			name = "@0 Show CSM Miner Results",
			level = PluginLevel.Regular,
			returnLabels = { "Visualisation of CSM Results" },
			returnTypes = { JComponent.class },
			parameterLabels = { "Composite State Machine Miner Results" },
			userAccessible = true)
	@Visualizer
	public JComponent visualize(UIPluginContext context, CSMMinerResults results) {
		if (results.tsMinerOutputs == null) {
			ProvidedObjectHelper.setFavorite(context, results, false);
			return (new SlickerOpenLogSettings()).showLogVis(context, results.log);
		}
		
		connections = new VisualisationConnections();
		results.connections = connections;
		
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.LINE_AXIS));
		wrapper.setBackground(Color.LIGHT_GRAY);
		
		// Create state models and their wrappers
		wrapper.add(createGraphWrapper(context, results, CSMMiner.CSMLabel));
		for (String processName : results.tsMinerOutputs.keySet()) {
			if (!processName.equals(CSMMiner.CSMLabel)) {
				wrapper.add(createGraphWrapper(context, results, processName));
			}
		}

		// Add mouse Listeners
		for (String processName : connections.graphPanels.keySet()) {
			StateModelVisualisationListener listener = new StateModelVisualisationListener(results, processName);
			connections.graphPanels.get(processName).getGraph().addMouseListener(listener);
			connections.modelListeners.put(processName, listener);
		}

		// Embed whole in a horizontal scroll pane
		ProMScrollPane scrollPane = new ProMScrollPane(wrapper); 
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		
		// Create config panel
		GridBagLayout configLayout = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		JPanel configPanel = new JPanel(configLayout);
		configPanel.setBackground(Color.LIGHT_GRAY);
		
		// Create model switch
		ProMComboBox<String> highlightSwitch = new ProMComboBox<>(new DefaultComboBoxModel<String>(
				new String[] { StateModelVisualisationListener.cooccurrenceHighlighting,
						StateModelVisualisationListener.stateEntryPerformanceHighlighting,
						StateModelVisualisationListener.tracePerformanceHighlighting } ));
		connections.highlightSwitch = highlightSwitch;

		highlightSwitch.setMinimumSize(new Dimension(160, 30));
		gbc.fill = GridBagConstraints.HORIZONTAL;
		configPanel.add(highlightSwitch, gbc);
		
		// Create model listModel
		DefaultListModel<String> modelNames = new DefaultListModel<>();
		modelNames.addElement(CSMMiner.CSMLabel);
		for (String name : results.perspectiveNames) {
			modelNames.addElement(name);
		}
		ProMList<String> modelList = new ProMList<>("Model List", modelNames);
		modelList.getList().setSelectionInterval(0, results.perspectiveNames.size());
		modelList.addListSelectionListener(new ModelListSelectionListener(modelNames));
		modelList.setMinimumSize(new Dimension(100, 100));
		((ProMScrollPane) modelList.getComponent(2)).setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1;
		gbc.weighty = 1;
		configPanel.add(modelList, gbc);
		
		// Put together the model listModel and the scroll pane
		JSplitPane modelPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		modelPane.setBackground(Color.LIGHT_GRAY);
		modelPane.setTopComponent(configPanel);
		modelPane.setBottomComponent(scrollPane);
		modelPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		modelPane.setUI(new BasicSplitPaneUI() {
			@Override
			public BasicSplitPaneDivider createDefaultDivider() {
				final BasicSplitPaneDivider divider = new ImprovedSplitPaneDivider(this);
				divider.setBackground(WidgetColors.COLOR_ENCLOSURE_BG);
				divider.setForeground(Color.LIGHT_GRAY);
				return divider;
			}
		});
		
		ProgressUI progressUI = new ProgressUI();
		JLayer<JComponent> graphLayer = new JLayer<>(modelPane, progressUI);
		connections.progressUI = progressUI;
		
		// Create statistics pane
		DefaultTableModel tableModel = createTableModel();
		ProMTableWithoutPanel statisticsPane = new ProMTableWithoutPanel(tableModel);
		setTableAppearance(statisticsPane);
		statisticsPane.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));
		connections.statisticsPane = statisticsPane;
		
		// Create interaction explanation pane
		JLabel label = new JLabel("Interaction description", SwingConstants.CENTER);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		label.setFont(new Font("Dialog", Font.BOLD, 15));
		JPanel interactionPane = SlickerFactory.instance().createRoundedPanel(1, new Color(160, 160, 160));
		interactionPane.add(label);
		connections.interactionPane = interactionPane;
		
		// Create relation metrics pane
		DefaultTableModel metricsTableModel = new DefaultTableModel() {
			public Class getColumnClass(int column) {
				switch (column) {
					case 0: case 1:
						return String.class;
					case 2: case 3: case 4: case 5: case 6: case 7: case 8: case 9:
						return Double.class;
					default:
						return Object.class;
				}
			}
		};
		MetricsPanel metricsPane = new MetricsPanel(metricsTableModel, results, connections, processWidth * numberProcessesShown);
		connections.metricsPane = metricsPane;
		
		// Create graph and statistics wrapper
		JSplitPane graphStatisticsWrapper = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		graphStatisticsWrapper.setBackground(Color.LIGHT_GRAY);
		graphStatisticsWrapper.setTopComponent(graphLayer);
		graphStatisticsWrapper.setBottomComponent(statisticsPane);
		graphStatisticsWrapper.setResizeWeight(1.0);
		graphStatisticsWrapper.setDividerSize(0);
		graphStatisticsWrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		connections.graphStatisticsWrapper = graphStatisticsWrapper;
		
		ProMSplitPane topComponent = new ProMSplitPane(ProMSplitPane.VERTICAL_SPLIT);
		topComponent.setTopComponent(graphStatisticsWrapper);
		topComponent.setBottomComponent(metricsPane);
		topComponent.setResizeWeight(1.0);
		topComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		connections.topComponent = topComponent;
		
		modelList.getList().setSelectionInterval(1, results.perspectiveNames.size());
		
		return topComponent;
	}
	
	public DefaultTableModel createTableModel() {
		DefaultTableModel tableModel = new DefaultTableModel();
		
		tableModel.addColumn("Name");
		tableModel.addColumn("Count");
		tableModel.addColumn("Traces");
		tableModel.addColumn("Average Per Trace");
		tableModel.addColumn("Support (Count)");
		tableModel.addColumn("Median Sojourn Time");
		tableModel.addColumn("Average Sojourn Time Per Trace");
		tableModel.addColumn("Support (Time)");
		
		tableModel.addRow(new Object[] {"Name", "Count", "Traces", "Average Per Trace", "Support (Count)", "Median Sojourn Time",
				"Average Sojourn Time Per Trace", "Support (Time)"});
		tableModel.addRow(new Object[] {""});
		
		return tableModel;
	}
	
	public void setTableAppearance(ProMTableWithoutPanel table) {
		table.setMinimumSize(new Dimension(processWidth * numberProcessesShown,0));
		table.setPreferredSize(new Dimension(processWidth * numberProcessesShown,30));
		TableColumnModel columnModel = table.getColumnModel();
		columnModel.getColumn(0).setPreferredWidth(2*(processWidth * numberProcessesShown) / (columnModel.getColumnCount()+1));
		
		for (int i = 1; i < columnModel.getColumnCount(); i++) {
			columnModel.getColumn(i).setPreferredWidth((processWidth * numberProcessesShown) / (columnModel.getColumnCount()+1));
		}
	}
	
	public JPanel createGraphWrapper(UIPluginContext context, CSMMinerResults results, String processName) {
		ProMJGraphPanel graphPanel = (ProMJGraphPanel) new StateModelVisualisation().visualize(context, results, processName);
		graphPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		graphPanel.setBackground(Color.white);
		connections.graphPanels.put(processName, graphPanel);
		
		JLabel label = new JLabel(processName, SwingConstants.CENTER);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		label.setFont(new Font("Dialog", Font.BOLD, 14));
		
		JPanel graphWrapper = new JPanel();
		graphWrapper.setLayout(new BoxLayout(graphWrapper, BoxLayout.PAGE_AXIS));
		graphWrapper.add(label);
		graphWrapper.add(graphPanel);
		graphWrapper.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.black));
		graphWrapper.setPreferredSize(new Dimension(processWidth,0));
		
		if (processName.equals(CSMMiner.CSMLabel)) {
			graphWrapper.setBackground(Color.lightGray);
		}
		else {
			graphWrapper.setBackground(Color.white);
		}
		
		connections.graphWrappers.put(processName, graphWrapper);
		
		return graphWrapper;
	}
	
	private class ModelListSelectionListener implements ListSelectionListener {

		private DefaultListModel<String> listModel;
		
		public ModelListSelectionListener(DefaultListModel<String> modelNames) {
			listModel = modelNames;
			for (int i = 0; i < modelNames.size(); i++) {
				connections.visibleModels.put(modelNames.getElementAt(i), true);
			}
		}
		
		public void valueChanged(ListSelectionEvent e) {
			JList list = (JList) e.getSource();
			for (int i = 0; i < listModel.size(); i++) {
				connections.visibleModels.put(listModel.getElementAt(i), list.isSelectedIndex(i));
				connections.graphWrappers.get(listModel.getElementAt(i)).setVisible(list.isSelectedIndex(i));
			}
			connections.topComponent.validate();
			connections.metricsPane.table.clearSelection();
			connections.graphStatisticsWrapper.setBottomComponent(connections.statisticsPane);
			connections.statisticsPane.repaint();
			
			if (!e.getValueIsAdjusting()) {
				for (String name : connections.graphWrappers.keySet()) {
					if (connections.visibleModels.get(name)) {
						if (connections.graphPanels.get(name).getGraph().getVisibleRect().height == 0) {
							(new GraphRefreshHelper(connections.topComponent, connections.graphPanels.get(name).getGraph())).start();
						}
					}
				}
			}
		}
		
	}
	
}
