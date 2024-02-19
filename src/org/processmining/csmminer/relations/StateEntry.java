package org.processmining.csmminer.relations;

public class StateEntry implements Comparable<StateEntry> {

	public int id;
	public long duration;

	public StateEntry(int id, long duration) {
		this.id = id;
		this.duration = duration;
	}

	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		return result;
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StateEntry other = (StateEntry) obj;
		if (id != other.id)
			return false;
		return true;
	}

	public int compareTo(StateEntry entry) {
		return (int) (duration - entry.duration);
	}
	
	public String toString() {
		return "" + duration;
	}

}
