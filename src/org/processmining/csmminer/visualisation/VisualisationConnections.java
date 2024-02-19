package org.processmining.csmminer.visualisation;

import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.framework.util.ui.widgets.ProMSplitPane;
import org.processmining.framework.util.ui.widgets.ProMTableWithoutPanel;
import org.processmining.models.jgraph.visualization.ProMJGraphPanel;

public class VisualisationConnections {

	public ProMTableWithoutPanel statisticsPane;
	public MetricsPanel metricsPane;
	public Map<String,ProMJGraphPanel> graphPanels;
	public Map<String,StateModelVisualisationListener> modelListeners;
	public ProgressUI progressUI;
	public ProMSplitPane topComponent;
	public JSplitPane graphStatisticsWrapper;
	public JPanel interactionPane;
	public Map<String,JPanel> graphWrappers;
	public Map<String,Boolean> visibleModels;
	public ProMComboBox<String> highlightSwitch;

	public VisualisationConnections() {
		graphPanels = new HashMap<>();
		modelListeners = new HashMap<>();
		graphWrappers = new HashMap<>();
		visibleModels = new HashMap<>();
	}
}
