package org.processmining.csmminer.visualisation;

import org.processmining.framework.util.ui.widgets.ProMSplitPane;
import org.processmining.models.jgraph.ProMJGraph;

public class GraphRefreshHelper extends Thread {

	private ProMSplitPane topComponent;
	private ProMJGraph graph;

	public GraphRefreshHelper(ProMSplitPane topComponent, ProMJGraph graph) {
		super();
		this.topComponent = topComponent;
		this.graph = graph;
	}
	
	public void run() {
		while (true) {
			topComponent.validate();
			
			try {
				sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			topComponent.validate();
			
			if (graph.getVisibleRect().height == 0) break;
		}
	}
	
}
