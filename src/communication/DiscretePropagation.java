package communication;

import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.parameter.Parameters;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.grid.Grid;
import repast.simphony.util.ContextUtils;

import Utils.Options;

public class DiscretePropagation {
	//Each propagation travels in a certain direction,
	//given by its angle expressed in radiant
	//Propagation is the mean used by the Perturbation to reach other Relays
	//In other words, many discrete propagations are used to carry a perturbation along the 8 directions
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

	//When a perturbation has propagated for this maximum amount, it disappears from the medium
	public double MAX_PROPAGATION_DISTANCE;
	
	private Perturbation perturbation;
	private ContinuousSpace<Object> space; //the space where relays are placed
	private Grid<Object> grid; //an abstraction for the continuous space using a grid
	private double propagationAngle; //one of the 8 possible angles a perturbation can travel
	private double propagationSpeed; //how many units a perturbation can advance during a time interval
	//private NdPoint origin; //the initial position of the perturbation. I.E. the position of the sender 
	private double traveledDistance; //the distance the perturbation has propagated along, expressed in units
	public boolean propagated; //used for notifying the relays which sense the medium for incoming perturbations
	
	public DiscretePropagation(Perturbation perturbation, ContinuousSpace<Object> space, Grid<Object> grid,
			double propagationAngle, double propagationSpeed) {
		super();
		this.perturbation = perturbation;
		this.space = space;
		this.grid = grid;
		this.propagationAngle = propagationAngle;
		this.propagationSpeed = propagationSpeed;
		this.traveledDistance = 0.0;
		this.propagated = false;
		this.MAX_PROPAGATION_DISTANCE = Options.MAX_PROPAGATION_DISTANCE;
	}
	
	@ScheduledMethod(start=1, interval=1) 
	public void step() {
		//Get the grid location of this perturbation
		//GridPoint pt = grid.getLocation (this);
		//Get the space location of this perturbation
		NdPoint spacePt = space.getLocation(this);
		//Before propagating, check if the propagation hasn't reached the boundaries of the space
		//and its maximum propagation range, otherwise it should disappear from the display 
		if(spacePt.getX() + propagationSpeed < 50.0 //TODO: parametrize these also
			&& spacePt.getX() - propagationSpeed > 0.0
			&& spacePt.getY() + propagationSpeed < 50.0
			&& spacePt.getY() - propagationSpeed > 0.0
			&& traveledDistance < MAX_PROPAGATION_DISTANCE) {
		
			//If the target destination is more than the maximum propagation range,
			//take the difference and propagate by a value smaller than the initial propagation speed
			if(traveledDistance + propagationSpeed > MAX_PROPAGATION_DISTANCE) {
				propagationSpeed = MAX_PROPAGATION_DISTANCE - traveledDistance;
			}
			
			//Propagate further in the space (medium)
			space.moveByVector(this, propagationSpeed, propagationAngle, 0);
			spacePt = space.getLocation(this);
			grid.moveTo(this,(int)spacePt.getX(),(int)spacePt.getY());
			traveledDistance += propagationSpeed;
			propagated = true;
		} else {
			Context<Object> context = ContextUtils.getContext(this);
			context.remove(this);
		}
		
		
		//old code, probably will  never be used or maybe it will come in useful later, dont remove it yets
		// use the GridCellNgh class to create GridCells for
		// the surrounding neighborhood .
//		GridCellNgh<Relay> nghCreator = new GridCellNgh<Relay>(grid, pt, Relay.class, 1 , 1);
		// import repast.simphony.query.space.grid.GridCell
//		List<GridCell<Relay>> gridCells = nghCreator.getNeighborhood (true);
//		SimUtilities.shuffle (gridCells, RandomHelper.getUniform());

		//		GridPoint pointWithMostHumans = null ;
		//		int maxCount = -1;
		//		for (GridCell<Relay> cell : gridCells) {
		//			if (cell.size() > maxCount ) {
		//				pointWithMostHumans = cell.getPoint();
		//				maxCount = cell.size();
		//			}
		//		}
	}

	public Perturbation getPerturbation() {
		return perturbation;
	}

	public void setPerturbation(Perturbation perturbation) {
		this.perturbation = perturbation;
	}	
}
