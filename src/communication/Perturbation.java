package communication;

import java.util.List;

import agents.Relay;
import repast.simphony.context.Context;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.query.space.grid.GridCell;
import repast.simphony.query.space.grid.GridCellNgh;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.SpatialMath;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import repast.simphony.util.ContextUtils;
import repast.simphony.util.SimUtilities;

/*
 * This class abstracts the perturbation wave cited in the paper.
 */
public class Perturbation {
	public static enum Type {
		VALUE_BROADCAST, //simplest type of payload, just a value
		UNICAST_MESSAGE, //object encapsulating the id of the destination and a value
		MULTICAST_MESSAGE, //object encapsulating the group and/or the topic, plus the value
		RETRANSMISSION_REQUEST, //request for the next expected perturbation
		ENCRYPTED_UNICAST, //just like the previous unicast message, but encrypted
	}
	
	private int source; //the id of the source relay
	private int reference; //the id of the perturbation, unique per source
	private Type type; //type of the payload
	private Object payload; //can be a value or a unicast/multicast message
	
	public Perturbation(int source, int reference, Type type, Object payload) {	
		super();
		this.source = source;
		this.reference = reference;
		this.type = type;
		this.payload = payload;
	}
	
	//Create an identical copy of the Perturbation
	public Perturbation clone() {
		return new Perturbation(this.source, this.reference, this.type, this.payload);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null)
			return false;
		
		if(!(obj instanceof Perturbation))
			return false;
		
		Perturbation p = (Perturbation)obj;
		if(this.source == p.source && this.reference == p.reference && this.type == p.getType()) 
			return true;
		else
			return false;
	}
	
	@Override
	public String toString() {
		return "<" + source + ", " + reference + ">";
	}
	
	public int getSource() {
		return source;
	}
	public void setSource(int source) {
		this.source = source;
	}
	public int getReference() {
		return reference;
	}
	public void setReference(int reference) {
		this.reference = reference;
	}
	public Object getPayload() {
		return payload;
	}
	public void setPayload(Object payload) {
		this.payload = payload;
	}
	public Type getType() {
		return type;
	}
	public void setType(Type type) {
		this.type = type;
	}
}
