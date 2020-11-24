package communication;

import java.io.Serializable;

/*
 * This class is used to encapsulate point-to-point message,
 * from one relay to another. The encrypted communication uses
 * the same class, but it encrypts the object after creating it
 */
public class UnicastMessage implements Serializable{

	private static final long serialVersionUID = 1L;
	private int destination; //who is the message for
	private Object value; //actual content of the message
	
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
