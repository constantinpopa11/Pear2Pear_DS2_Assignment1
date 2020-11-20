package agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.SealedObject;
import communication.DiscretePropagation;
import communication.MulticastMessage;
import communication.Perturbation;
import communication.UnicastMessage;
import communication.Perturbation.Type;
import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.engine.watcher.Watch;
import repast.simphony.engine.watcher.WatcherTriggerSchedule;
import repast.simphony.parameter.Parameters;
import repast.simphony.query.space.continuous.ContinuousWithin;
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
import security.AsymmetricCryptography;
import Utils.DataCollector;
import Utils.Options;

public class Relay {

	//Relay__II private Map<Integer, ArrayList<Perturbation>> bag; //Out-of-order perturbations go here, waiting to be delivered later
	private Map<Integer, ArrayList<Perturbation>> log; //append-only log
	private ContinuousSpace<Object> space; //the space where relays are placed
	private Grid<Object> grid; //an abstraction for the continuous space using a grid
	private int id; //Globally unique id of the relay
	private int clock = 0; //Incrementally growing id for the emitted perturbations
	private Map<Integer, Integer> frontier; //reference of next perturbation per peer to be delivered
	private HashSet<String> seenARQs;
	private Map<Integer, List<String>> subscriptions;
	
	
	//Used to probability of perturbation count parameter
	private double probabilityOfPerturbation = Options.PROBABILITY_OF_PERTURBATION;
	
	public Relay(ContinuousSpace<Object> space, Grid<Object> grid, int id) {
		this.log = new HashMap<>();
		//Relay__II this.bag = new HashMap<>();
		this.frontier = new HashMap<>();
		this.seenARQs = new HashSet<>();
		this.space = space;
		this.grid = grid;
		this.id = id;
		
		//random way to generate subscriptions
		if(id % 7 == 0) {
			subscriptions = new HashMap<>();
			List<String> topics = new ArrayList<>();
			topics.add("science");
			topics.add("literature");
			//this relay subscribes to group 0 for the topics science and literature
			//subscriptions can have also no topics (empty topics list), in this case the node 
			//will receive all the messages from the group, so no filtering will be made
			subscriptions.put(0, topics);
		}
			
		
	}

	@ScheduledMethod(start=1, interval=1, priority=100) 
	public void step() {
		double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
		//TODO add threshold parameter
		if(coinToss <= probabilityOfPerturbation) { //propagate a perturbation
			System.out.println("Relay(" + id + "): generating perturbation"
					+ "<" + id + ", " + this.clock + ", " + new String("ciao") + ">");
			
			Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.VALUE_BROADCAST, new String("ciao"));
			forward(perturbation);
			
			if(this.id == Options.NODE_A_LATENCY)
				DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
			
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
	
	private void forward(Perturbation p) {
		System.out.println("Relay(" + id + "): forwarding perturbation"
				+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() + ">");
		
		// get the grid location of this Relay
		NdPoint spacePt = space.getLocation(this);
		GridPoint pt = grid.getLocation(this);
		Context<Object> context = ContextUtils.getContext(this);
		
		//The propagation of a perturbation/wave is simulated by generating 8 perturbation clones
		//and propagating them along the 9 directions/angles (0, 45, 90, 135...)
		//Each perturbation clone has its own propagation speed in order to simulate the propagation delay
		//I.e. each propagation clone travels and its own speed and it can reach the maximum range faster than others
		//The clone is called Discrete Propagation and it's a "piece" of a Perturbation
		for(int i=0; i<DiscretePropagation.PROPAGATION_ANGLES.length; i++) {
			DiscretePropagation propagation = new DiscretePropagation(
					p,
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
	@ScheduledMethod(start=1, interval=10, priority=50) //TODO: decide interval
	public void automaticRetransmissionMechanism() {
		//TODO: implement
		for(Map.Entry<Integer, ArrayList<Perturbation>> perSourceLog : log.entrySet()) {
			List <Perturbation> perturbations = perSourceLog.getValue();
			Perturbation latestPerturbation = perturbations.get(perturbations.size() - 1);
			System.out.println("Relay(" + id + "): broadcasting ARQ for perturbation " 
					+ "<src=" + latestPerturbation.getSource() + ", "
					+ "ref=" + (latestPerturbation.getReference()+1) + ">");
			final String uuid = UUID.randomUUID().toString();
			seenARQs.add(uuid);
			forward(new Perturbation(latestPerturbation.getSource(), 
					latestPerturbation.getReference() + 1, Type.ARQ, uuid)); //TODO:what should payload value be?
		}
		
	}

	//When a perturbation propagates, relays get notified so they check 
	//if the perturbations is in their "range" (broadcast domain)
	//A perturbation is going to be sensed when it is in the range of the relay (within value)
//	@Watch(watcheeClassName = "communication.DiscretePropagation",
//			watcheeFieldNames = "propagated",
//			query = "within 3",
//			whenToTrigger = WatcherTriggerSchedule.IMMEDIATE)
	@ScheduledMethod(start=1, interval=1, priority=1)
	public void sense() {
		Context<Object> context = ContextUtils.getContext(this);
		List<Perturbation> perturbations = new ArrayList<Perturbation>();
		
		
		//pick up the perturbations in the same cell as the relay
		GridPoint pt = grid.getLocation(this);
		
		//old way to find nearby perturbations
		//collect all the perturbations in this cell
//		for(Object obj : grid.getObjectsAt(pt.getX(), pt.getY())) {
//			if(obj instanceof DiscretePropagation) {
//				DiscretePropagation propagation = (DiscretePropagation) obj;
//				perturbations.add(propagation.getPerturbation());
//			}
//		}
		
		//thread safe method to inspect the nearby propagations
		ContinuousWithin<Object> nearbyQuery = new ContinuousWithin(context, this, 2.0);
		CopyOnWriteArrayList<Object> nearbyObjects = new CopyOnWriteArrayList<>();
		nearbyQuery.query().forEach(nearbyObjects::add);

		for(Object obj : nearbyObjects) {
			if(obj instanceof DiscretePropagation) {
				DiscretePropagation propagation = (DiscretePropagation) obj;
				Perturbation p = propagation.getPerturbation();

				if(p.getType() == Type.ARQ) {
					int src = p.getSource();
					int ref = p.getReference();
					if(!seenARQs.contains((String)p.getPayload())) {
						System.out.println("Relay(" + id + "): sensed retransimission request for P="
								+ "<src=" + src + ", ref=" + ref +">");
						seenARQs.add((String)p.getPayload());
						if(log.get(src) != null) {
							for(Perturbation Q : log.get(src)) 
								if(Q.getReference() == ref)
									forward(Q);
						}
					}

				} else if((frontier.get(p.getSource()) == null
						|| p.getReference() >= frontier.get(p.getSource())) 
						&& p.getSource() != id && p.getType() != Type.ARQ) { 
						//Relay__II&& !isInBag(p)) {//don't sense self-generated perturbations
					
					//Relay__II addToBag(p);
					
					System.out.println("Relay(" + id + "): sensed perturbation"
							+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() + ">");
					
					Integer nextRef = frontier.get(p.getSource());
					if(nextRef == null) 
						nextRef = p.getReference(); //TODO:this might not be permanent
					if(nextRef == p.getReference()) {
						forward(p);
						deliver(p);
						frontier.put(p.getSource(), nextRef + 1);
					}
						
					//Relay__II(the entire loop)
					//go through the bag until you do not make any new change
//							boolean changes = true;
//							while(changes) {
//								changes = false;
						//Relay__II Iterator<Perturbation> deferredPerturbations = bag.get(p.getSource()).iterator();
//								while (deferredPerturbations.hasNext()) {
//								    Perturbation Q = deferredPerturbations.next();
//								    Integer nextRef = frontier.get(Q.getSource());
//									if(nextRef == null) 
//										nextRef = Q.getReference(); //TODO:this might not be permanent
//									if(nextRef == Q.getReference())
//										changes = true;
//										forward(Q);
//										deliver(Q);
//										frontier.put(Q.getSource(), nextRef + 1);
//										//TODO: update thread safe method  removeFromBag then uncomment
//										//removeFromBag(Q);
//										deferredPerturbations.remove(); //temporary workaround
//								}
//							}
				}
				
			}
		}
	}
		
	
	//check if the perturbation is present in the bag
	//Relay__II
//	private boolean isInBag(Perturbation p) {
//		boolean result = false;
//		if(bag.containsKey(p.getSource())) {
//			result = bag.get(p.getSource()).contains(p);
//		}
//		return result;
//	}
	
	//add a new perturbation to bag
	//Relay__II
//	private void addToBag(Perturbation p) {
//		if(!bag.containsKey(p.getSource())) {
//			bag.put(p.getSource(), new ArrayList<Perturbation>());
//		}
//		bag.get(p.getSource()).add(p.clone());
//	}
	
	//remove perturbation from bag
	//Relay__II
//	private void removeFromBag(Perturbation p) {
//		bag.get(p.getSource()).remove(p);
//	}
	
	//deliver a perturbation
	private void deliver(Perturbation p) {
		//TODO: check type of perturbation, payload etc
		//TODO: is perturbation an ARQ?
		//TODO: forward perturbation
		//TODO: deliver old perturbations from the buffer if possible
		System.out.println("Relay(" + id + "): delivering perturbation"
				+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() + ">");
		
		//Inspect the payload
		if(p.getType() == Type.UNICAST_MESSAGE) {
			UnicastMessage m = (UnicastMessage)p.getPayload();
			if(m.getDestination() == this.id) {
				System.out.println("Relay(" + id + "): Relay(" + p.getSource() + ") sent me a private message");
			}
		} else if(p.getType() == Type.VALUE_BROADCAST) {
			//nothing
		} else if(p.getType() == Type.MULTICAST_MESSAGE) {
			MulticastMessage m = (MulticastMessage)p.getPayload();
			if(subscriptions.get(m.getGroup()) != null) {
				List<String> topics = subscriptions.get(m.getGroup());
				if(topics == null || topics.contains(m.getTopic())) {
					
					System.out.println("Relay(" + id + "): received a new message "
							+ "for the subscription in the group " + m.getGroup());
				}
			}
		} else if(p.getType() == Type.ENCRYPTED_UNICAST) {
			SealedObject encryptedMessage = (SealedObject)p.getPayload();
			UnicastMessage decryptedMessage = AsymmetricCryptography.decryptPayload(
					encryptedMessage, security.KeyManager.PRIVATE_KEYS[id]);
			
			if(decryptedMessage != null) {
				System.out.println("Relay(" + id + "): Relay(" + p.getSource() + ") sent me an encrypted private message");
			}
		}
		
		//If not present, add the new source in log
		if(!log.containsKey(p.getSource()))
			log.put(p.getSource(), new ArrayList<Perturbation>());
		
		//Add the perturbation to the associated source's list
		log.get(p.getSource()).add(p);
		
		//If this node is in charge of collecting data about latency and the sender of the perturbation is too, collect data
		if(this.id == Options.NODE_B_LATENCY && p.getSource() == Options.NODE_A_LATENCY) {
			DataCollector.saveLatency(p, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencyReceiver.csv");
		}
		
	}
	
	//TODO: implement group_send, private_send, publish(?) methods
	private void privateSend(int destination, Object value) {
		forward(new Perturbation(this.id, clock++, Type.UNICAST_MESSAGE, value));
	}
	
	private void groupSend(int groupId, int topic, Object value) {
		forward(new Perturbation(this.id, clock, Type.MULTICAST_MESSAGE, value));
	}
	
	@Override
	public String toString() {
		String result = "";
		result += "Relay: " + this.id + " --- ";
		for(Map.Entry<Integer, ArrayList<Perturbation>> logLine : log.entrySet()) {
			result += " Source(" + logLine.getKey() + ") -> ";
			for(Perturbation p : logLine.getValue()) {
				result += " | <" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload() +"> ";
			}
		}
		return result;
	}
	
}

