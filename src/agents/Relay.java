package agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import communication.DiscretePropagation;
import communication.Perturbation;
import communication.UnicastMessage;
import communication.Perturbation.Type;
import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.engine.watcher.Watch;
import repast.simphony.engine.watcher.WatcherTriggerSchedule;
import repast.simphony.parameter.Parameters;
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

public class Relay {

	private Map<Integer, ArrayList<Perturbation>> bag; //Out-of-order perturbations go here, waiting to be delivered later
	private Map<Integer, ArrayList<Perturbation>> log; //append-only log
	private ContinuousSpace<Object> space; //the space where relays are placed
	private Grid<Object> grid; //an abstraction for the continuous space using a grid
	private int id; //Globally unique id of the relay
	private int perturbationCounter = 0; //Incrementally growing id for the emitted perturbations
	private Map<Integer, Integer> frontier; //reference of the last delivered perturbation per peer
	
	//Used to probability of perturbation count parameter
	Parameters params = RunEnvironment.getInstance().getParameters();
	private double probabilityOfPerturbation = params.getDouble("probabilityOfPerturbation");
	
	public Relay(ContinuousSpace<Object> space, Grid<Object> grid, int id) {
		this.log = new HashMap<>();
		this.bag = new HashMap<>();
		this.frontier = new HashMap<>();
		this.space = space;
		this.grid = grid;
		this.id = id;
		
	}

	@ScheduledMethod(start=1, interval=1) 
	public void step() {
		double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
		//TODO add threshold parameter
		if(coinToss <= probabilityOfPerturbation) { //propagate a perturbation
			forward(new Perturbation(this.id, this.perturbationCounter++, Type.VALUE_BROADCAST, new String("ciao")));

			//code that might be recycled later
//			Network<Object> net = Network<Object>) context .
//					getProjection(" infection network ");
//			net.addEdge this, zombie);

			// use the GridCellNgh class to create GridCells for
			// the surrounding neighborhood .
			//GridCellNgh<Relay> nghCreator = new GridCellNgh<Relay>(grid, pt, Relay.class, 1, 1);
			// import repast.simphony.query.space.grid.GridCell
			//List<GridCell<Relay>> gridCells = nghCreator.getNeighborhood(true);
			//SimUtilities.shuffle(gridCells, RandomHelper.getUniform());

			//		GridPoint pointWithMostHumans = null ;
			//		int maxCount = -1;
			//		for(GridCell<Relay> cell : gridCells) {
			//			if(cell.size()> maxCount) {
			//				pointWithMostHumans = cell.getPoint();
			//				maxCount = cell.size();
			//			}
			//		}
		}
	}
	
	private void forward(Perturbation perturbation) {
		// get the grid location of this Relay
		NdPoint spacePt = space.getLocation(this);
		GridPoint pt = grid.getLocation(this);
		Context<Object> context = ContextUtils.getContext(this);
		
		//The propagation of a wave is simulated by generating 8 perturbations
		//and propagating them along the 9 directions/angles (0, 45, 90, 135...)
		//Each perturbation has its own propagation speed in order to simulate the propagation delay
		//I.e. each propagations travels and its own speed and it can reach the maximum range faster than others
		for(int i=0; i<DiscretePropagation.PROPAGATION_ANGLES.length; i++) {
			DiscretePropagation propagation = new DiscretePropagation(
					perturbation,
					space, grid,
					DiscretePropagation.PROPAGATION_ANGLES[i], 
					RandomHelper.nextDoubleFromTo(0.3, 0.7)); //TODO: add as parameter
			context.add(propagation);
			//Finally place the perturbation in the space
			//Initially the perturbation has the same position as the source, 
			//then it moves (propagates) at each interval step
			space.moveTo(propagation, spacePt.getX(), spacePt.getY());
			grid.moveTo(propagation, pt.getX(), pt.getY());
		}
	}
	
	
	//Automatic retransmission requests 
	@ScheduledMethod(start=1, interval=3) //TODO: decide interval
	public void automaticRetransmissionMechanism() {
		//TODO: implement
	}

	@Watch(watcheeClassName = "communication.DiscretePropagation",
			watcheeFieldNames = "propagated",
			query = "colocated",
			whenToTrigger = WatcherTriggerSchedule.IMMEDIATE)
	//When a perturbation propagates, relays get notified so they check 
	//if the perturbations is in their "range" (broadcast domain)
	//A perturbation is going to be sensed when it is found in the same cell of the relay
	private void sense() {
		
		//pick up the perturbations in the same cell as the relay
		GridPoint pt = grid.getLocation(this);
		
		//collect all the perturbations in this cell
		List<Perturbation> perturbations = new ArrayList<Perturbation>();
		for(Object obj : grid.getObjectsAt(pt.getX(), pt.getY())) {
			if(obj instanceof DiscretePropagation) {
				DiscretePropagation propagation = (DiscretePropagation) obj;
				perturbations.add(propagation.getPerturbation());
			}
		}
		
		//sense perturbations
		for(Perturbation p : perturbations) {
			
			//add to bag if you find a new perturbation
			if(p.getReference() >= frontier.get(p.getSource()) && !isInBag(p)) {
				addToBag(p);
				
				//go through the bag until you do not make any new change
				boolean changes = true;
				while(changes) {
					changes = false;
					for(Map.Entry<Integer, ArrayList<Perturbation>> entry : bag.entrySet()) {
						for(Perturbation Q : entry.getValue()) {
							changes = true;
							forward(Q);
							deliver(Q);
							frontier.put(Q.getSource(), frontier.get(Q.getSource()) + 1);
							removeFromBag(Q);
						}
					}
				}
			}
		}
	}
	
	//check if the perturbation is present in the bag
	private boolean isInBag(Perturbation p) {
		boolean result = false;
		if(bag.containsKey(p.getSource())) {
			result = bag.get(p.getSource()).contains(p);
		}
		return result;
	}
	
	//add a new perturbation to bag
	private void addToBag(Perturbation p) {
		if(!bag.containsKey(p.getSource())) {
			bag.put(p.getSource(), new ArrayList<Perturbation>());
		}
		bag.get(p.getSource()).add(p);
	}
	
	//remove perturbation from bag
	private void removeFromBag(Perturbation p) {
		bag.get(p.getSource()).remove(p);
	}
	
	//deliver a perturbation
	private void deliver(Perturbation p) {
		//TODO: check type of perturbation, payload etc
		//TODO: is perturbation an ARQ?
		//TODO: forward perturbation
		//TODO: deliver old perturbations from the buffer if possible
		System.out.println("Sensed " + p.toString());
	}
	
	//TODO: implement group_send, private_send, publish methods
	private void privateSend(int destination, Object value) {
		forward(new Perturbation(this.id, perturbationCounter++, Type.UNICAST_MESSAGE, value));
	}
	
	private void groupSend(int groupId, int topic, Object value) {
		forward(new Perturbation(this.id, perturbationCounter, Type.MULTICAST_MESSAGE, value));
	}
	
	
}

