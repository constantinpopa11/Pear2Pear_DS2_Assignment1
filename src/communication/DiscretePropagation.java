package communication;

import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.SpatialException;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.grid.Grid;
import repast.simphony.util.ContextUtils;
import Utils.Options;
import agents.Relay;
import communication.Perturbation.Type;
import pear2Pear_DS2_Assignment1.TopologyManager;

/*
 * A discrete propagation can be seen as a piece of perturbation which travels 
 * in a certain direction. In order to simulate a broadcast, we use multiple
 * propagations which follow different trajectories. The propagation represents
 * a way of carrying the Perturbation to the neighbors.
 */
public class DiscretePropagation {
	/*
	* Each propagation travels in a certain direction,
	* given by its angle expressed in radiant
	* Propagation is the mean used by the Perturbation to reach other Relays
	* In other words, many discrete propagations are used to carry a perturbation along the 8 directions
	*/
	public static final double[] PROPAGATION_ANGLES = {
        0,          //0 degrees   
		0.7854,     //45 degrees
		1.571,      //90
		2.356,      //135
		3.142,      //180
		3.927,      //225
		4.712,      //270
		5.498,      //315
	};

	public double maxDistance;//When a perturbation has propagated for this maximum amount, it disappears from the medium
	private Perturbation perturbation; //the perturbation which is being carried
	private double propagationAngle; //one of the 8 possible angles a perturbation can travel
	private double traveledDistance; //the distance the perturbation has propagated along, expressed in units
	public boolean propagated; //used for notifying the relays which sense the medium for incoming perturbations
	Relay forwarder; //object reference to the relay which forwarded (NOT generated) this perturbation
	
	public DiscretePropagation(Perturbation perturbation, double propagationAngle, Relay forwarder) {
		super();
		this.perturbation = perturbation;
		this.propagationAngle = propagationAngle;
		this.forwarder = forwarder;
		this.traveledDistance = 0.0;
		this.propagated = false;
		this.maxDistance = Options.MAX_PROPAGATION_DISTANCE;
	}
	
	@ScheduledMethod(start=1, interval=1, priority=99) 
	public void propagate() {
		//Get the grid location of this perturbation
		//GridPoint pt = grid.getLocation (this);
		//Get the space location of this perturbation
		NdPoint spacePt = TopologyManager.getSpace().getLocation(this);
		
		//Simulate perturbation delay
		double delayMultiplier = 1.0;
		double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
		if(coinToss <= Options.DELAY_PROBABILITY) {
			delayMultiplier = RandomHelper.nextDoubleFromTo(0, 1);
		}
		
		//Update propagation speed based on bandwidth and delay multiplier
		double propagationSpeed = forwarder.getFairBandwidth() * delayMultiplier;
		
		//Before propagating, check if the propagation hasn't reached the boundaries of the space
		//and its maximum propagation range, otherwise it should disappear from the display 
		if(traveledDistance < maxDistance) {
		
			//If the target destination is greater than the maximum propagation range,
			//take the difference and propagate by a value smaller than the initial propagation speed
			if(traveledDistance + propagationSpeed > maxDistance) {
				propagationSpeed = maxDistance - traveledDistance;
			}
			
			try {
				//Propagate further in the space (medium)
				TopologyManager.getSpace().moveByVector(this, propagationSpeed, propagationAngle, 0);
				spacePt = TopologyManager.getSpace().getLocation(this);
				traveledDistance += propagationSpeed;
				propagated = true;
			} catch(SpatialException e) {
				//When the propagation has traveled for a value equal to MAX_DISTANCE, it disappears
				Context<Object> context = ContextUtils.getContext(this);
				context.remove(this);
				//Release the used bandwidth
				if(!forwarder.isCrashed())
					forwarder.releaseBandwidth(perturbation);
			}
			
		} else {
			//When the propagation has traveled for a value equal to MAX_DISTANCE, it disappears
			Context<Object> context = ContextUtils.getContext(this);
			context.remove(this);
			//Release the used bandwidth
			if(!forwarder.isCrashed())
				forwarder.releaseBandwidth(perturbation);
		}
	}
	
	public Relay getForwarder() {
		return forwarder;
	}

	public Perturbation getPerturbation() {
		return perturbation;
	}

	public void setPerturbation(Perturbation perturbation) {
		this.perturbation = perturbation;
	}
}
