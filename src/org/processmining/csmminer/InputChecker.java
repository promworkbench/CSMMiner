package org.processmining.csmminer;

import java.util.HashSet;
import java.util.Set;

import javax.swing.JOptionPane;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XAttributeLiteral;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.csmminer.plugins.StateLogCreatorPlugin;

public class InputChecker {
	
	public static final int INVALID = 0;
	public static final int VALID = 1;
	public static final int NO_PROCESS_NAME= 2;
	
	private static Set<String> perspectives;
	private static boolean firstTrace;
	private static boolean firstEvent;
	private static boolean initialStateInformation;
	
	public synchronized static int checkStateLog(UIPluginContext context, XLog log) {
		perspectives = new HashSet<>();
		firstTrace = true;
		firstEvent = true;
		initialStateInformation = true;
		
		for (XTrace trace : log) {
			if (!checkTrace(trace)) return INVALID;
			
			for (XEvent event : trace) {
				int result = checkEvent(context, log, event, trace);
				if (result != VALID) return result;
			}
		}
		
		return VALID;
	}

	public static boolean checkTrace(XTrace trace) {
		XAttributeMap attributes = trace.getAttributes();
		Set<String> tracePerspectives = new HashSet<>();
		
		for (String attributeName : attributes.keySet()) {
			if (attributeName.contains(CSMMiner.processInitialStateAttributeLabel)) {
				String[] attributeNameParts = attributeName.split(":");
				String perspective = "";
				
				for (int i = 2; i < attributeNameParts.length; i++) {
					perspective += attributeNameParts[i];
				}
				
				tracePerspectives.add(perspective);
				
				if (firstTrace) {
					perspectives.add(perspective);
				}
				else {
					if (!perspectives.contains(perspective)) {
						JOptionPane.showMessageDialog(null,"The initial trace does not contain an attribute \"" +
								CSMMiner.processInitialStateAttributeLabel + "\" for perspective \"" + perspective + "\".");
						return false;
					}
				}
			}
		}
		
		for (String perspective : perspectives) {
			if (!tracePerspectives.contains(perspective)) {
				String traceName = XConceptExtension.instance().extractName(trace);
				JOptionPane.showMessageDialog(null,"Trace \"" + traceName + "\" does not contain an attribute \"" +
								CSMMiner.processInitialStateAttributeLabel + "\" for perspective \"" + perspective + "\".");
				return false;
			}
		}
		
		if (perspectives.isEmpty()) {
			initialStateInformation = false;
		}
		
		firstTrace = false;
		return true;
	}

	public static int checkEvent(UIPluginContext context, XLog log, XEvent event, XTrace trace) {
		XAttributeMap attributes = event.getAttributes();
		String eventName = XConceptExtension.instance().extractName(event);
		boolean eventHasPerspectiveName = false;
		
		for (String attributeName : attributes.keySet()) {
			if (attributeName.contains(CSMMiner.processNameAttributeLabel)) {
				eventHasPerspectiveName = true;
				String perspective = ((XAttributeLiteral) attributes.get(attributeName)).getValue();
				
				if (!perspectives.contains(perspective) && initialStateInformation) {
					String traceName = XConceptExtension.instance().extractName(trace);
					JOptionPane.showMessageDialog(null,"Event \"" + eventName + "\" in trace \"" + traceName + "\" contains an attribute "
							+ "\"" + CSMMiner.processNameAttributeLabel + "\" specifying the unknown perspective \"" + perspective + "\".");
					return INVALID;
				}
			}
		}
		
		if (!eventHasPerspectiveName) {
			if (initialStateInformation) {
				String traceName = XConceptExtension.instance().extractName(trace);
				JOptionPane.showMessageDialog(null,"Event \"" + eventName + "\" in trace \"" + traceName + "\" does not contain an attribute"
						+ " \"" + CSMMiner.processNameAttributeLabel + "\" specifying the perspective to which this state change belongs.");
				return INVALID;
			}
			else if (firstEvent) {
				int result = JOptionPane.showConfirmDialog(null,"There appear to be events without a \"" +
						CSMMiner.processNameAttributeLabel + "\" attribute. Do you want to cancel the CSM Miner and run the \"" +
						StateLogCreatorPlugin.pluginName + "\" plugin?", "Cancel Plugin?", JOptionPane.YES_NO_OPTION);
				if (result == 0) return NO_PROCESS_NAME;
			}
		}
		
		firstEvent = false;
		return VALID;
	}
	
}
