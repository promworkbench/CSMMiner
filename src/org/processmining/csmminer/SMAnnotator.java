package org.processmining.csmminer;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deckfour.xes.extension.std.XExtendedEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.payload.event.EventPayloadTransitionSystem;
import org.processmining.plugins.transitionsystem.miner.util.TSEventCache;
import org.processmining.plugins.tsanalyzer.AnnotatedTransitionSystem;
import org.processmining.plugins.tsanalyzer.StatisticsAnnotationProperty;
import org.processmining.plugins.tsanalyzer.TSAnalyzer;
import org.processmining.plugins.tsanalyzer.annotation.Statistics;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyStateAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyStateStatistics;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyStatistics;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyTransitionAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.frequency.FrequencyTransitionStatistics;
import org.processmining.plugins.tsanalyzer.annotation.time.TimeStateAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.time.TimeStateStatistics;
import org.processmining.plugins.tsanalyzer.annotation.time.TimeStatistics;
import org.processmining.plugins.tsanalyzer.annotation.time.TimeTransitionAnnotation;
import org.processmining.plugins.tsanalyzer.annotation.time.TimeTransitionStatistics;

public class SMAnnotator extends TSAnalyzer {

	// The context of this miner.
	private final PluginContext context;

	//types of annotations available (extend this enum to include other annotations)
	public enum annotations {Time , Frequency};

	// the transition system to annotate
	private final EventPayloadTransitionSystem transitionSystem;

	// eventCache is used for efficient access to events in XTrace
	private final TSEventCache eventCache;

	// statistics is a hashmap that stores the different annotations to be performed, represented by a string
	private final Map<SMAnnotator.annotations,Statistics> statistics;

	// the log to be used to annotate the transition system
	private final XLog log;

	// annotated transition system (result)
	private final AnnotatedTransitionSystem ats;
	
	private boolean useProgressCounter;
	
	public SMAnnotator(PluginContext context, EventPayloadTransitionSystem ts, XLog log, boolean useProgressCounter) {
		super(context, ts, log);
		this.context = context;
		this.transitionSystem = ts;
		this.log = log;
		this.eventCache = new TSEventCache();
		
		this.statistics = new HashMap<SMAnnotator.annotations,Statistics>();
		
		this.statistics.put(annotations.Time, new TimeStatistics());
		this.statistics.put(annotations.Frequency,new FrequencyStatistics());
		
		this.useProgressCounter = useProgressCounter;
		
		ats = new AnnotatedTransitionSystem(ts);
	}
	
	
	/**
	 * Annotates the transition system according to the settings
	 */
	public AnnotatedTransitionSystem annotate() {
		
		if (useProgressCounter) {
			// Context  progress bar setup
			context.getProgress().setMinimum(0);
			context.getProgress().setMaximum(log.size());
			
			context.getProgress().setCaption("Annotating transition system with time");
			context.getProgress().setIndeterminate(false);
		}

		// now the real thing
		for (XTrace pi : log) {
		
			long startTime = getStartTime(pi);
			long endTime = getEndTime(pi);

			if ((startTime != -1) && (endTime != -1)) 
				for (int i = 0; i < pi.size(); i++)
					
					processTimeEvent(pi, i, startTime, endTime);
					
			processFrequencyEvent(pi);
			
			if (useProgressCounter) {
				context.getProgress().inc(); // increase the progress bar
			}
		}
		
		createTimeAnnotationsFromStatistics(); // create time annotations from collected statistics
		createFrequencyAnnotationsFromStatistics(); // create frequency annotations from collected statistics
			
		return ats;
	}
	
	/**
	 * This method processes one event from a trace by collecting all necessary
	 * statistical time (i.e., elapsed, remaining, etc. times) data.
	 * 
	 * @param pi
	 *            the trace to which the event belongs
	 * @param i
	 *            the index of the event to be processed
	 * @param startTime
	 *            the starting time of this trace
	 * @param endTime
	 *            the ending time of this trace
	 */
	private void processTimeEvent(final XTrace pi, final int i, final long startTime, final long endTime) {
		/**
		 * Get the timestamp of the event we are processing
		 */
		Date currentTimestamp = XExtendedEvent.wrap(eventCache.get(pi, i)).getTimestamp();
		if (currentTimestamp != null) {

			long currentTime = currentTimestamp.getTime();

			/**
			 * Get the transition that corresponds to this event. Also get the
			 * source and the target for this transition.
			 */
			Transition transition = transitionSystem.getTransition(pi, i);
			if (transition != null) {
				State source = transition.getSource();
				State target = transition.getTarget();

				/**
				 * create statistics for the transition, source and target
				 */
				TimeTransitionStatistics transitionStatistics = (TimeTransitionStatistics) statistics.get(annotations.Time).getStatistics(transition);
				TimeStateStatistics sourceStatistics = (TimeStateStatistics) statistics.get(annotations.Time).getStatistics(source);
				TimeStateStatistics targetStatistics = (TimeStateStatistics) statistics.get(annotations.Time).getStatistics(target);

				/**
				 * annotate the source only for the first event in the trace
				 */
				if (i == 0) {
					sourceStatistics.getRemaining().addValue(endTime - currentTime);
					sourceStatistics.getElapsed().addValue(0);
					sourceStatistics.getSoujourn().addValue(0);
				}

				/**
				 * annotate target with elapsed and remaining time
				 */
				targetStatistics.getElapsed().addValue(currentTime - startTime);
				targetStatistics.getRemaining().addValue(endTime - currentTime);

				/**
				 * process soujourn time
				 */
				if ((i != pi.size() - 1)) {
					Date nextTimestamp = getExtendedEvent(pi, i + 1).getTimestamp();
					if (nextTimestamp != null) {
						double soujourn = nextTimestamp.getTime() - currentTime;
						targetStatistics.getSoujourn().addValue(soujourn);
					}
				} else {
					targetStatistics.getSoujourn().addValue(0);
				}

				/**
				 * annotate the transition with the duration time
				 */
				if (i == 0) {
					transitionStatistics.getDuration().addValue(0.0);
				} else {
					Date previousTimestamp = getExtendedEvent(pi, i - 1).getTimestamp();
					if (previousTimestamp != null) {
						transitionStatistics.getDuration().addValue(currentTime - previousTimestamp.getTime());
					}
				}
			}
		}
	}
	
	/**
	 * This method processes one event from a trace by collecting all necessary
	 * statistical frequencies data.
	 * 
	 * @param pi
	 *            the trace to which the event belongs
	 * 	 
	 **/
	private void processFrequencyEvent(final XTrace pi) {
		
		Map<State,Integer> stateObservations = new HashMap<State,Integer>();
		Map<Transition,Integer> transitionObservations = new HashMap<Transition,Integer>();
		
		//initialize with 0s
		
		for(State s : transitionSystem.getNodes())
			stateObservations.put(s, 0);
		
		for(Transition t : transitionSystem.getEdges())
			transitionObservations.put(t, 0);
		
		for(int i = 0 ; i < pi.size() ; i++)
		{
			Transition transition = transitionSystem.getTransition(pi, i);
			if (transition != null) 
			{
				State source = transition.getSource();
				State target = transition.getTarget();
				
				if(i == 0) //annotate the source only for the first event, this avoids double-counting states
				{
					if(stateObservations.containsKey(source))
						stateObservations.put(source, (stateObservations.get(source) + 1)); //adds 1 to the observations of the source state
					else
						stateObservations.put(source, 1);
				}
				//annotate target states
				if(stateObservations.containsKey(target))
					stateObservations.put(target, (stateObservations.get(target) + 1)); //adds 1 to the observations of the target state
				else
					stateObservations.put(target, 1);
				
				//annotate transitions
				if(transitionObservations.containsKey(transition))
					transitionObservations.put(transition, (transitionObservations.get(transition) + 1)); //adds 1 to the observations of the transition
				else
					transitionObservations.put(transition, 1);
			}
		}
		
		int observations;
		FrequencyStateStatistics stateStatistics;		
		for(State state : stateObservations.keySet()) //for each state annotate the number of observations in that trace and if the state was observed in the trace (1) or not (0)
		{
			stateStatistics = (FrequencyStateStatistics) statistics.get(annotations.Frequency).getStatistics(state);
			observations = stateObservations.get(state);
			
			stateStatistics.getObservations().addValue(observations);
			
			if(observations > 0)
				stateStatistics.getTraces().addValue(1);
			else
				stateStatistics.getTraces().addValue(0);
		}
		
		FrequencyTransitionStatistics transitionStatistics;
		for(Transition transition : transitionObservations.keySet()) //same for transitions
		{
			transitionStatistics = (FrequencyTransitionStatistics) statistics.get(annotations.Frequency).getStatistics(transition);
			observations = transitionObservations.get(transition);
			
			transitionStatistics.getObservations().addValue(observations);
			
			if(observations > 0)
				transitionStatistics.getTraces().addValue(1);
			else
				transitionStatistics.getTraces().addValue(0);
		}			 
	}
	
	/**
	 * Goes through all gathered statistics for each state and transition and
	 * creates time annotations from the statistics.
	 * 
	 * @return the annotation of the transition system
	 */
	private void createTimeAnnotationsFromStatistics() {
		/**
		 * create annotation for each state
		 */
		for (Entry<State, TimeStateStatistics> entry : ((TimeStatistics)statistics.get(annotations.Time)).getStates()) {
			TimeStateAnnotation stateAnnotation = ats.getTime_StateAnnotation(entry.getKey());
			annotateTimeState(stateAnnotation, entry.getValue());
		}
		/**
		 * create annotation for each transition
		 */
		for (Entry<Transition, TimeTransitionStatistics> entry : ((TimeStatistics)statistics.get(annotations.Time)).getTransitions()) {
			TimeTransitionAnnotation transitionAnnotation = ats.getTime_TransitionAnnotation(entry.getKey());
			annotateTimeTransition(transitionAnnotation, entry.getValue());
		}
	}
	
	/**
	 * Goes through all gathered statistics for each state and transition and
	 * creates frequency annotations from the statistics.
	 * 
	 * @return the annotation of the transition system
	 */
	private void createFrequencyAnnotationsFromStatistics() {
		/**
		 * create annotation for each state
		 */
		for (Entry<State, FrequencyStateStatistics> entry : ((FrequencyStatistics)statistics.get(annotations.Frequency)).getStates()) {
			FrequencyStateAnnotation stateAnnotation = ats.getFrequency_StateAnnotation(entry.getKey());
			annotateFrequencyState(stateAnnotation, entry.getValue());
		}
		/**
		 * create annotation for each transition
		 */
		for (Entry<Transition, FrequencyTransitionStatistics> entry : ((FrequencyStatistics)statistics.get(annotations.Frequency)).getTransitions()) {
			FrequencyTransitionAnnotation transitionAnnotation = ats.getFrequency_TransitionAnnotation(entry.getKey());
			annotateFrequencyTransition(transitionAnnotation, entry.getValue());
		}
	}
	
	/**
	 * Time annotations for states and transitions
	 */
	private void annotateTimeState(TimeStateAnnotation stateAnnotation, TimeStateStatistics statistics) {
		annotateStatisticsProperty(stateAnnotation.getSoujourn(), statistics.getSoujourn());
		annotateStatisticsProperty(stateAnnotation.getRemaining(), statistics.getRemaining());
		annotateStatisticsProperty(stateAnnotation.getElapsed(), statistics.getElapsed());
	}	
	private void annotateTimeTransition(TimeTransitionAnnotation transitionAnnotation,
			TimeTransitionStatistics statistics) {
		annotateStatisticsProperty(transitionAnnotation.getDuration(), statistics.getDuration());
	}
	
	/**
	 * Frequency annotations for states and transitions
	 */
	private void annotateFrequencyState(FrequencyStateAnnotation stateAnnotation, FrequencyStateStatistics statistics) {
		annotateStatisticsProperty(stateAnnotation.getObservations(), statistics.getObservations());
		annotateStatisticsProperty(stateAnnotation.getTraces(), statistics.getTraces());
	}	
	private void annotateFrequencyTransition(FrequencyTransitionAnnotation transitionAnnotation,FrequencyTransitionStatistics statistics) {
		annotateStatisticsProperty(transitionAnnotation.getObservations(), statistics.getObservations());
		annotateStatisticsProperty(transitionAnnotation.getTraces(), statistics.getTraces());
	}

	/**
	 * Creates annotation form one statistics property (i.e, average, standard
	 * deviation, variance, etc.)
	 * 
	 * @param prop
	 *            annotation to be created
	 * @param stat
	 *            statistics with time values
	 */
	private void annotateStatisticsProperty(StatisticsAnnotationProperty prop, DescriptiveStatistics stat) {
		prop.setValue((float) stat.getMean());
		prop.setAverage((float) stat.getMean());
		prop.setStandardDeviation((float) stat.getStandardDeviation());
		prop.setMin((float) stat.getMin());
		prop.setMax((float) stat.getMax());
		prop.setSum((float) stat.getSum());
		prop.setVariance((float) stat.getVariance());
		prop.setFrequency((float) stat.getN());
		prop.setMedian((float) stat.getPercentile(50));
	}
	
	/**
	 * Gets the time stamp of the first event in a trace.
	 * 
	 * @param pi
	 *            the trace
	 * @return the timestamp of the first event in the trace
	 */
	private long getStartTime(XTrace pi) {
		try {
			for (int i = 0; i < pi.size(); i++) {
				Date timestamp = getExtendedEvent(pi, i).getTimestamp();
				if (timestamp != null) {
					return timestamp.getTime();
				}
			}
		} catch (Exception ce) {
		}
		return -1;
	}

	/**
	 * Gets the time stamp of the last event in a trace.
	 * 
	 * @param pi
	 *            the trace
	 * @return the timestamp of the last event in the trace
	 */
	private long getEndTime(XTrace pi) {
		try {
			for (int i = 0; i < pi.size(); i++) {
				Date timestamp = getExtendedEvent(pi, pi.size() - i - 1).getTimestamp();
				if (timestamp != null) {
					return timestamp.getTime();
				}
			}
		} catch (Exception ce) {
		}
		return -1;
	}
	
	/**
	 * Converts the i-the element of a trace into XExtendedEvent.
	 * 
	 * @param pi
	 *            the trace
	 * @return it-th element of the trace as XExtendedEvent
	 */
	private XExtendedEvent getExtendedEvent(XTrace trace, int index) {
		return XExtendedEvent.wrap(eventCache.get(trace, index));
	}

}
