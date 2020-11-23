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
import repast.simphony.space.graph.Network;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import repast.simphony.util.ContextUtils;
import repast.simphony.util.SimUtilities;
import security.AsymmetricCryptography;
import security.KeyManager;
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
	private HashSet<Perturbation> livePerturbations;

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
		livePerturbations = new HashSet<>();
		subscriptions = new HashMap<>();
		
		
		//random way to generate subscriptions
		//Basically the nodes whose id is a multiple of 7 will be subscribed to these topics
		if(id % 7 == 0) {
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
		//double coinToss2 = RandomHelper.nextDoubleFromTo(0, 1);
		//double coinToss3 = RandomHelper.nextDoubleFromTo(0, 1);
		//TODO add threshold parameter
		if(coinToss <= probabilityOfPerturbation) { //propagate a value broadcast perturbation
			broadcast(new String("ciao"));
			
		} else if(coinToss > probabilityOfPerturbation && coinToss <= probabilityOfPerturbation * 2) { //private message
			//each relays sends a private message to relay with id+1
			int secretDestination = (id + 1) % Options.RELAY_COUNT;
			privateSend(secretDestination, new String("ciao"));
			
		} else if(coinToss > probabilityOfPerturbation * 2 && coinToss <= probabilityOfPerturbation * 3) {
			groupSend(0, "science", "1+1=2");
		}
	}
	
	private void forward(Perturbation p) {
		System.out.println("Relay(" + id + "): forwarding perturbation"
				+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() + ">");
		
		// get the grid location of this Relay
		NdPoint spacePt = space.getLocation(this);
		GridPoint pt = grid.getLocation(this);
		Context<Object> context = ContextUtils.getContext(this);
		
		livePerturbations.add(p);
		
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
					this);
			context.add(propagation);
			//Finally place the perturbation in the space
			//Initially the perturbation has the same position as the source, 
			//then it moves (propagates) at each interval step
			space.moveTo(propagation, spacePt.getX(), spacePt.getY());
			grid.moveTo(propagation, pt.getX(), pt.getY());
		}
	}
	
	
	//Automatic retransmission requests 
	@ScheduledMethod(start=1, priority=50, interval=1) //TODO: decide interval
	public void automaticRetransmissionMechanism() {
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		//Relay activate this mechanism at different times. 
		//This is needed in order to reduce the flooding effect 
		//when all the relays start propagating ARQs at the same time.
		//e.g: Relay 1, 11, 21, 31....91, 101, 111, 121 etc send ARQ at tick 1, 11, 21, 31.....
		if(tickCount % 10 == id % 10) {
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
		ContinuousWithin<Object> nearbyQuery = new ContinuousWithin(context, this, 5.0);
		CopyOnWriteArrayList<Object> nearbyObjects = new CopyOnWriteArrayList<>();
		nearbyQuery.query().forEach(nearbyObjects::add);

		for(Object obj : nearbyObjects) {
			if(obj instanceof DiscretePropagation) {
				DiscretePropagation propagation = (DiscretePropagation) obj;
				Perturbation p = propagation.getPerturbation();
				Relay forwarder = propagation.getForwarder();

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

						Network<Object> net = (Network<Object>) context.getProjection("delivery network");
						net.addEdge(this, forwarder);
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
			} else {
				System.out.println("Relay(" + id + "): Encrypted message from Relay " +
						p.getSource() + " couldn't be decrypted.");
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
	
	private void broadcast(Object value) {
		//TODO: smarter payload value?
		System.out.println("Relay(" + id + "): generating perturbation"
				+ "<" + id + ", " + this.clock + ", val>");
		
		//Generate perturbation and deliver to yourself
		Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.VALUE_BROADCAST, value);
		forward(perturbation);
		deliver(perturbation);
		
		//Write generated message for latency measurement
		if(this.id == Options.NODE_A_LATENCY)
			DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
	}
	
	private void privateSend(int destination, Object value) {
		//TODO: smarter payload value?
		System.out.println("Relay(" + id + "): generating perturbation"
				+ "<" + id + ", " + this.clock + ", P.M.>");
		
		UnicastMessage m = new UnicastMessage(destination, value);
		SealedObject secret = AsymmetricCryptography.encryptPayload(m, KeyManager.PUBLIC_KEYS[destination]);
		
		//Generate perturbation and deliver to yourself
		Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.ENCRYPTED_UNICAST, secret);
		forward(perturbation);
		deliver(perturbation);
		
		//Write generated message for latency measurement
		if(this.id == Options.NODE_A_LATENCY)
			DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
	}
	
	private void groupSend(int groupId, String topic, Object value) {
		//TODO: smarter payload value?
				System.out.println("Relay(" + id + "): generating perturbation"
						+ "<" + id + ", " + (this.clock+1) + ", G.M.>");
				
		MulticastMessage m = new MulticastMessage(groupId, topic, value);
		Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.MULTICAST_MESSAGE, m);
		forward(perturbation);
		deliver(perturbation);
		
		//Write generated message for latency measurement
		if(this.id == Options.NODE_A_LATENCY)
			DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
	
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
	
	public String saveIsolation() {
		
		final Map<Integer, ArrayList<Perturbation>> temp;
		temp = new HashMap<>();
		
		for(Map.Entry<Integer, ArrayList<Perturbation>> logLine : log.entrySet()) {
			temp.put(logLine.getKey(), logLine.getValue());
		}
		
		temp.remove(this.id);
		
		if(temp.isEmpty())
			DataCollector.saveIsolation(this.id, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "IsolatedRelays.csv");
		
		return "";
	}
	
	public void releaseBandwidth(Perturbation p) {
		livePerturbations.remove(p);
	}
	
	
	//Method to calculate the effective bandwidth based on how many perturbations
	//this relay has forwarded. The greater the number of perturbations, the slower
	//the propagation speed among the perturbations
	public double getFairBandwidth() {
		double totalSize = livePerturbations.size() * Options.PERTURBATION_SIZE;
		double fairbandwidth = Options.BANDWIDTH / totalSize;
		
		//Perturbations can't propagate faster than a given value, so there is an upper bound
		if(fairbandwidth > Options.MAX_PROPAGATION_SPEED)
			return Options.MAX_PROPAGATION_SPEED;
		else
			return fairbandwidth;
	}
	
	public int getId() {
		return id;
	}
}

