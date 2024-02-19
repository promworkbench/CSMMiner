package org.processmining.csmminer.plugins;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Iterator;

import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.csmminer.CSMMiner;
import org.processmining.csmminer.statelogcreator.StateLogCreatorParametersPanel;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginCategory;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.xeslite.external.XFactoryExternalStore;

@Plugin(
		name = "State Log Creator",
		level = PluginLevel.Regular,
		categories = { PluginCategory.Filtering },
		parameterLabels = { "Event Log" },
		returnLabels = { "State Log" },
		returnTypes = { XLog.class },
		userAccessible = true,
		help = "Creates a State Log as input for the Composite State Machine Miner, as described in: "
				+ "\"Guided Interaction Exploration in Artifact-centric Process Models\" "
				+ "by M.L. van Eck, N. Sidorova, W.M.P. van der Aalst."
				+ "<br/><font color=\"rgb(120,120,120)\">CSM Miner</font><br/>"
				+ "Assigns each event in the state log an attribute \"" + CSMMiner.processNameAttributeLabel + "\" "
				+ "specifying to which perspective it belongs.",
		mostSignificantResult = 1)
public class StateLogCreatorPlugin {
	
	static XFactory factory = new XFactoryExternalStore.MapDBDiskImpl();
	public static String pluginName = "State Log Creator";

	@UITopiaVariant(
			uiLabel = "State Log Creator",
			affiliation = "Eindhoven University of Technology",
			author = "Maikel L. van Eck",
			email = "m.l.v.eck@tue.nl",
			pack = "CSMMiner")
	@PluginVariant(variantLabel = "Create a State Log for the CSM Miner", requiredParameterLabels = { 0 })
	public XLog createStateLog(final UIPluginContext context, XLog log) {
		StateLogCreatorParametersPanel parameters = new StateLogCreatorParametersPanel(log);
		
		InteractionResult result = context.showConfiguration("State Log Creator Parameters", parameters);
		if (result.equals(InteractionResult.CANCEL)) {
			context.getFutureResult(0).cancel(true);
			return null;
		}
		
		String logName = XConceptExtension.instance().extractName(log);
		context.getFutureResult(0).setLabel(MessageFormat.format("{0} (State Log) @{1}", logName != null ? logName : "NULL",
				DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date())));
		return createStateLog(context, log, parameters);
	}

	public XLog createStateLog(UIPluginContext context, XLog log, StateLogCreatorParametersPanel parameters) {
		XLog stateLog = (XLog) log.clone();
		parameters.updateStateNames();
		
		for (XTrace trace : stateLog) {
			XAttributeMap traceAttributes = trace.getAttributes();
			for (Iterator<String> iterator = traceAttributes.keySet().iterator(); iterator.hasNext();) {
				String attribute = iterator.next();
				if (attribute.contains(CSMMiner.processInitialStateAttributeLabel)) {
					iterator.remove();
				}
			}
			
			for (XEvent event : trace) {
				String eventName = XConceptExtension.instance().extractName(event);
				event.getAttributes().put(CSMMiner.processNameAttributeLabel, factory.createAttributeLiteral(
						CSMMiner.processNameAttributeLabel, parameters.stateArtifactMap.get(eventName), null));
			}
		}
		
		return stateLog;
	}
}
