package org.processmining.csmminer;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.xeslite.external.XFactoryExternalStore;

public class LogProcessor {
	
	static XFactory factory = new XFactoryExternalStore.MapDBDiskImpl();

	public static List<String> computePerspectiveNames(XLog log) {
		Set<String> perspectiveNames = new HashSet<>();
		
		for (XTrace trace : log) {
			for (XEvent event : trace) {
				perspectiveNames.add(getEventPerspective(event));
			}
		}
		
		return new ArrayList<>(perspectiveNames);
	}
	
	public static Map<String, XLog> createPerspectiveSubLogs(XLog log, List<String> perspectiveNames) {
		Map<String,XLog> perspectiveSubLogs = new HashMap<>();
		
		for (String perspectiveName : perspectiveNames) {
			XLog perspectiveSubLog = factory.createLog((XAttributeMap) log.getAttributes().clone());
			perspectiveSubLog.getClassifiers().addAll(log.getClassifiers());
			perspectiveSubLog.getGlobalTraceAttributes().addAll(log.getGlobalTraceAttributes());
			
			for (int i = 0; i < log.size(); i++) {
				XTrace trace = factory.createTrace((XAttributeMap) log.get(i).getAttributes().clone());
				
				XEvent firstTraceEvent = log.get(i).get(0);
				String firstTraceEventPerspectiveName = getEventPerspective(firstTraceEvent);
				String initialState = getPerspectiveInitialState(trace, perspectiveName);
				
				// FIXME: if perspectives do match then don't create artificial if initial state is not known?
				if (!firstTraceEventPerspectiveName.equals(perspectiveName) ||
						!initialState.equals(XConceptExtension.instance().extractName(firstTraceEvent))) {
					// Create initial state with timestamp equal to first change if this perspective has not started as first,
					// or create initial state without timestamp if initial state attribute is mismatched from first event
					XEvent initialEvent = factory.createEvent();
					XConceptExtension.instance().assignName(initialEvent, initialState);
					
					if (!firstTraceEventPerspectiveName.equals(perspectiveName)) {
						Date initialTime = XTimeExtension.instance().extractTimestamp(firstTraceEvent);
						if (initialTime != null) {
							XTimeExtension.instance().assignTimestamp(initialEvent, initialTime);
						}
					}
					
					trace.add(initialEvent);
				}
				
				String previousEventName = null;
				for (XEvent event : log.get(i)) {
					String eventPerspectiveName = getEventPerspective(event);
					
					if (perspectiveName.equals(eventPerspectiveName)) {
						String eventName = XConceptExtension.instance().extractName(event);
						if (!eventName.equals(previousEventName)) {
							trace.add((XEvent) event.clone());
							previousEventName = eventName;
						}
					}
				}
				
				// FIXME: add artificial end events?
				
				perspectiveSubLog.add(trace);
			}
			
			perspectiveSubLogs.put(perspectiveName, perspectiveSubLog);
		}
		
		return perspectiveSubLogs;
	}
	
	public static XLog createCompositeLog(XLog log, List<String> perspectiveNames, boolean removeRepeatStates) {
		XLog compositeLog = factory.createLog((XAttributeMap) log.getAttributes().clone());
		compositeLog.getClassifiers().addAll(log.getClassifiers());
		compositeLog.getGlobalTraceAttributes().addAll(log.getGlobalTraceAttributes());
		Map<String,String> perspectiveStates = new HashMap<>();
		
		for (int i = 0; i < log.size(); i++) {
			XTrace trace = log.get(i);
			XTrace compositeTrace = factory.createTrace((XAttributeMap) log.get(i).getAttributes().clone());
			
			boolean initialStateMatches = false;
			String firstEventName = XConceptExtension.instance().extractName(log.get(i).get(0));
			String compositeStateName = "|";
			for (String perspectiveName : perspectiveNames) {
				String initialState = getPerspectiveInitialState(trace, perspectiveName);
				perspectiveStates.put(perspectiveName, initialState);
				compositeStateName += initialState + "|";
				
				// FIXME: all perspectives to which the first event (state change) is assigned need to have a matching initial state
				// Otherway around: if firstEvent assigned to perspectiveName && initialState not equals firstEventName => create artificial 
				if (initialState.equals(firstEventName)) {
					// FIXME: only if first event assigned to same perspective! perspectiveName in getEventPerspectives(firstEvent)
					initialStateMatches = true;
				}
			}
			
			// FIXME: see above, but! if no initial states are known then don't create artificial event? Linked to FIXME in sublogcreation
			if (!initialStateMatches) {
				XEvent initialEvent = factory.createEvent();
				XConceptExtension.instance().assignName(initialEvent, compositeStateName);
				compositeTrace.add(initialEvent);
			}
			
			String previousName = null;
			for (int j = 0; j < trace.size(); j++) {
				XEvent event = trace.get(j);
				String eventPerspectiveName = getEventPerspective(event);
				String eventPerspectiveState = XConceptExtension.instance().extractName(event);
				perspectiveStates.put(eventPerspectiveName, eventPerspectiveState);
				
				XEvent compositeState = (XEvent) event.clone();
				compositeStateName = "|";
				
				for (String perspectiveName : perspectiveNames) {
					compositeStateName += perspectiveStates.get(perspectiveName) + "|";
				}
				
				XConceptExtension.instance().assignName(compositeState, compositeStateName);
				
				if (!compositeStateName.equals(previousName) || !removeRepeatStates) {
					compositeTrace.add(compositeState);
					previousName = compositeStateName;
				}
			}
			
			compositeLog.add(compositeTrace);
		}
		
		return compositeLog;
	}
	
	public static XLog transformLog(XLog log, String processName, Set<State> removedStates, List<Set<State>> groupedStates) {
		// TODO: modify the sub-logs directly instead of creating new ones
		XLog newLog = (XLog) log.clone();

		for (XTrace trace : newLog) {
			Set<XEvent> removedEvents = new HashSet<>();
			String initialState = getPerspectiveInitialState(trace, processName);

			for (State state : removedStates) {
				if (state.getAttributeMap().get(AttributeMap.TOOLTIP).equals(initialState)) {
					trace.getAttributes().remove(CSMMiner.processInitialStateAttributeLabel + processName);

					for (XEvent event : trace) {
						String eventProcessName = event.getAttributes().get(CSMMiner.processNameAttributeLabel).toString();
						String eventName = XConceptExtension.instance().extractName(event);

						if (processName.equals(eventProcessName) && !eventName.equals(initialState) &&
								trace.getAttributes().containsKey(CSMMiner.processInitialStateAttributeLabel + processName)) {
							trace.getAttributes().put(CSMMiner.processInitialStateAttributeLabel + processName,
									factory.createAttributeLiteral(CSMMiner.processInitialStateAttributeLabel + processName,
											eventName, null));
							break;
						}
					}
				}
			}

			for (Set<State> states : groupedStates) {
				if (states.size() > 1) {
					String newEventName = "";

					for (State state : states) {
						newEventName += state.getAttributeMap().get(AttributeMap.TOOLTIP) + "+";
					}

					newEventName = newEventName.substring(0, newEventName.length()-1);

					if (newEventName.contains(initialState) && trace.getAttributes().containsKey(
							CSMMiner.processInitialStateAttributeLabel + processName)) {
						trace.getAttributes().put(CSMMiner.processInitialStateAttributeLabel + processName,
								factory.createAttributeLiteral(CSMMiner.processInitialStateAttributeLabel + processName,
										newEventName, null));
					}
				}
			}

			XEvent previousEvent = null;
			for (XEvent event : trace) {
				String eventProcessName = event.getAttributes().get(CSMMiner.processNameAttributeLabel).toString();
				String eventName = XConceptExtension.instance().extractName(event);

				if (processName.equals(eventProcessName)) {
					for (State state : removedStates) {
						if (state.getAttributeMap().get(AttributeMap.TOOLTIP).equals(eventName)) {
							removedEvents.add(event);
						}
					}

					for (Set<State> states : groupedStates) {
						if (states.size() > 1) {
							String newEventName = "";

							for (State state : states) {
								newEventName += state.getAttributeMap().get(AttributeMap.TOOLTIP) + "+";
							}

							newEventName = newEventName.substring(0, newEventName.length()-1);

							if (newEventName.contains(eventName)) {
								XConceptExtension.instance().assignName(event, newEventName);
							}
						}
					}

					if (previousEvent != null && XConceptExtension.instance().extractName(event).equals(
							XConceptExtension.instance().extractName(previousEvent))) {
						removedEvents.add(event);
					}

					if (!removedEvents.contains(event)) {
						previousEvent = event;
					}
				}
			}

			trace.removeAll(removedEvents);
		}

		return newLog;
	}
	
	// FIXME: idea: retrieve map of events to perspectives from the results object, if this is null then use event attributes, return set
	public static String getEventPerspective(XEvent event) {
		XAttribute perspectiveNameAttribute = event.getAttributes().get(CSMMiner.processNameAttributeLabel);
		
		String perspectiveName = CSMMiner.unknownPerspectiveLabel;
		if (perspectiveNameAttribute != null) {
			perspectiveName = perspectiveNameAttribute.toString();
		}
		
		return perspectiveName;
	}
	
	public static String getPerspectiveInitialState(XTrace trace, String perspectiveName) {
		XAttribute initialStateAttribute = trace.getAttributes().get(CSMMiner.processInitialStateAttributeLabel + perspectiveName);
		
		String initialState = perspectiveName + "NotStarted";
		if (initialStateAttribute != null) {
			initialState = initialStateAttribute.toString();
		}
		
		return initialState;
	}
}
