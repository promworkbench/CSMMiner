package org.processmining.csmminer.statelogcreator;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.csmminer.CSMMiner;
import org.processmining.framework.util.ui.widgets.ProMTable;

public class StateLogCreatorParametersPanel extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public ProMTable table;
	public DefaultTableModel tableModel;
	public Map<String,String> stateArtifactMap = new HashMap<>();

	public StateLogCreatorParametersPanel(XLog log) {
		super();
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		
		JLabel label = new JLabel("Assign each state to an artifact. Use Ctrl-C and Ctrl-V to copy and paste the focussed table cell.",
				SwingConstants.CENTER);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		label.setFont(new Font("Dialog", Font.BOLD, 14));
		add(label);
		
		tableModel = new ArtifactTableModel();
		tableModel.addColumn("State Name");
		tableModel.addColumn("Artifact Name");
		
		addStateNames(log);
		
		table = new ProMTable(tableModel);
		table.getTableHeader().setReorderingAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getTable().setTransferHandler(new CopyTableTransferHandler());
		table.setPreferredSize(new Dimension(750, 500));
		table.setRowSorter(new TableRowSorter<TableModel>(table.getTable().getModel()));
		((TableRowSorter)table.getRowSorter()).toggleSortOrder(0);
		
		add(table);
	}
	
	public void addStateNames(XLog log) {
		for (XTrace trace : log) {
			for (XEvent event : trace) {
				String eventName = XConceptExtension.instance().extractName(event);
				
				if (!stateArtifactMap.containsKey(eventName)) {
					stateArtifactMap.put(eventName, "");
					
					XAttribute artifactNameAttribute = event.getAttributes().get(CSMMiner.processNameAttributeLabel);
					if (artifactNameAttribute != null) {
						stateArtifactMap.put(eventName, artifactNameAttribute.toString());
					}
					
					tableModel.addRow(new Object[] { eventName, stateArtifactMap.get(eventName) } );
				}
			}
		}
	}
	
	public void updateStateNames() {
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			stateArtifactMap.put((String) tableModel.getValueAt(i, 0), (String) tableModel.getValueAt(i, 1));
		}
		
		for (String state : stateArtifactMap.keySet()) {
			if (stateArtifactMap.get(state).equals("")) {
				stateArtifactMap.put(state, CSMMiner.unknownPerspectiveLabel);
			}
		}
	}
	
	private class ArtifactTableModel extends DefaultTableModel {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public boolean isCellEditable(int row, int column){
			if (column == 0) {
				return false;
			}
			else return true;
		}
		
		public Class getColumnClass(int column) {
			switch (column) {
				case 0: case 1:
					return String.class;
				default:
					return Object.class;
			}
		}
	}

}
