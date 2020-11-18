package communication;

import java.io.Serializable;

public class UnicastMessage implements Serializable{

	private static final long serialVersionUID = 1L;
	private int destination;
	private Object value;
	
	public UnicastMessage(int destination, Object value) {
		super();
		this.destination = destination;
		this.value = value;
	}
	
	public int getDestination() {
		return destination;
	}
	public void setDestination(int destination) {
		this.destination = destination;
	}
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
	
	
}
