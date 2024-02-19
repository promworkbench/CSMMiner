package org.processmining.csmminer.relations;

public class PerspectiveTransition {
	public String perspectiveName;
	public String fromState;
	public String toState;
	
	public PerspectiveTransition(String perspectiveName, String fromState, String toState) {
		this.perspectiveName = perspectiveName;
		this.fromState = fromState;
		this.toState = toState;
	}

	public String toString() {
		return "PerspectiveTransition [perspectiveName=" + perspectiveName + ", fromState=" + fromState + ", toState=" + toState
				+ "]";
	}

	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fromState == null) ? 0 : fromState.hashCode());
		result = prime * result + ((perspectiveName == null) ? 0 : perspectiveName.hashCode());
		result = prime * result + ((toState == null) ? 0 : toState.hashCode());
		return result;
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PerspectiveTransition other = (PerspectiveTransition) obj;
		if (fromState == null) {
			if (other.fromState != null)
				return false;
		} else if (!fromState.equals(other.fromState))
			return false;
		if (perspectiveName == null) {
			if (other.perspectiveName != null)
				return false;
		} else if (!perspectiveName.equals(other.perspectiveName))
			return false;
		if (toState == null) {
			if (other.toState != null)
				return false;
		} else if (!toState.equals(other.toState))
			return false;
		return true;
	}
	
}
