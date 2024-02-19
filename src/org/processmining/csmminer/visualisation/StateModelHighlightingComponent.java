package org.processmining.csmminer.visualisation;

import info.clearthought.layout.TableLayout;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.processmining.csmminer.CSMMinerResults;
import org.processmining.framework.util.ui.scalableview.ScalableComponent;
import org.processmining.framework.util.ui.scalableview.ScalableViewPanel;
import org.processmining.framework.util.ui.scalableview.interaction.ViewInteractionPanel;
import org.processmining.models.graphbased.directed.DirectedGraphNode;
import org.processmining.models.graphbased.directed.transitionsystem.State;

import com.fluxicon.slickerbox.components.SlickerButton;

public class StateModelHighlightingComponent extends JPanel implements ViewInteractionPanel {

	private static final long serialVersionUID = 2502697744708095342L;
	
	private JPanel componentPanel;
	private SlickerButton switchCountTimeButton;
	
	public StateModelVisualisationListener modelListener;
	
	public StateModelHighlightingComponent(CSMMinerResults results, String processName) {
		
		modelListener = results.connections.modelListeners.get(processName);
		
		double size[][] = { { 5, 85, 5 }, { 5, 25, 5 } };
		componentPanel = new JPanel();
		componentPanel.setLayout(new TableLayout(size));
		componentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		componentPanel.setOpaque(true);
		
		switchCountTimeButton = new SlickerButton("Use Counts");
		switchCountTimeButton.setFont(switchCountTimeButton.getFont().deriveFont(11f));
		switchCountTimeButton.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 12));
		switchCountTimeButton.setOpaque(false);
		switchCountTimeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		switchCountTimeButton.setHorizontalAlignment(SwingConstants.LEFT);
		switchCountTimeButton.addActionListener(new SwitchCountTimeListener());
		componentPanel.add(switchCountTimeButton, "1, 1");
	}

	public JComponent getComponent() {
		return componentPanel;
	}

	public double getHeightInView() {
		return 25;
	}

	public String getPanelName() {
		return "Highlighting";
	}

	public double getWidthInView() {
		return 85;
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
	
	private class SwitchCountTimeListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			modelListener.useCountStatistics = !modelListener.useCountStatistics;
			if (modelListener.useCountStatistics) switchCountTimeButton.setText("Use Time");
			else switchCountTimeButton.setText("Use Counts");
			
			int i = 0;
			for (DirectedGraphNode state : modelListener.graphPanel.getSelectedNodes()) {
				if (i == modelListener.graphPanel.getSelectedNodes().size()-1) {
					modelListener.resetNodeAndEdgeColours();
					modelListener.resetStatistics();
					
					modelListener.updateNodeColoursForState((State) state);
					modelListener.updateEdgeColorsForState((State) state);
					modelListener.updateStateStatistics((State) state);
					
					modelListener.clearOldSelection();
					modelListener.refreshAllGraphs();
				}
				else {
					i++;
				}
			}
		}
		
	}

}
