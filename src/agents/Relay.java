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
import repast.simphony.space.continuous.AbstractContinuousSpace;
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

/*
 * This class encapsulates the behavior of the relay.
 * It contains both the active methods, such as the creation of perturbations,
 * as well as passive ones, when the relays acts as observer and senses the incoming perturbations.
 */
public class Relay {

	//Relay__II private Map<Integer, ArrayList<Perturbation>> bag; //Out-of-order perturbations go here, waiting to be delivered later
	private Map<Integer, ArrayList<Perturbation>> log; //append-only log, one log per source
	private ContinuousSpace<Object> space; //the space where relays are placed
	private Grid<Object> grid; //an abstraction for the continuous space using a grid
	public int id; //Globally unique id of the relay
	private int clock = 0; //Incrementally growing id for the emitted perturbations
	private Map<Integer, Integer> frontier; //reference of next perturbation per peer to be delivered
	private HashSet<String> seenARQs; //This is needed to filter out already seen ARQs and save computation time
	private Map<Integer, List<String>> subscriptions; //The groups and the topics this relay has subscribed to
	private HashSet<Perturbation> livePerturbations; //The set of perturbations this relay has forwarded AND are still alive

	//Relays generate perturbations with a given probability value
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
		
		/*
		* Random way to generate subscriptions and test the overall multicast messagging
		* Basically the nodes whose id is a multiple of 7 (0, 7, 14, 21...)
		* will be subscribe to the group with id=0 and will listen for the topics science and literature
		*/
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

	/*
	 * At each tick, the relays generate a new perturbation if the
	 * random generated number is smaller than 3 * "probabilityOfPerturbation"
	 * Then, based on the specific value it decides the type of perturbation.
	 * The payload of the perturbations is always the same, but it is not relevant 
	 * for our purposes, in terms of performance of the algorithm, since we assume
	 * that all the perturbations have the same size.
	 */
	@ScheduledMethod(start=1, interval=1, priority=50) 
	public void generatePerturbation() {
		double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
		if(coinToss <= probabilityOfPerturbation) { //propagate a value broadcast perturbation
			broadcast(new String("ciao"));
			
		} else if(coinToss > probabilityOfPerturbation && coinToss <= probabilityOfPerturbation * 2) { //private message
			//each relays sends a private message to relay with identifier equal to id+1
			int secretDestination = (id + 1) % Options.RELAY_COUNT;
			privateSend(secretDestination, new String("ciao"));
			
		} else if(coinToss > probabilityOfPerturbation * 2 && coinToss <= probabilityOfPerturbation * 3) {
			groupSend(0, "science", "1+1=2");
		}
	}
	
	
	//Method used to forward a perturbation 
	private void forward(Perturbation p) {
		System.out.println("Relay(" + id + "): forwarding perturbation"
				+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() + ">");
		
		// get the grid location of this Relay
		NdPoint spacePt = space.getLocation(this);
		GridPoint pt = grid.getLocation(this);
		Context<Object> context = ContextUtils.getContext(this);
		
		livePerturbations.add(p);
		
		/*
		* The propagation of a perturbation/wave is simulated by generating 8 perturbation clones
		* and propagating them along the 9 directions/angles (0, 45, 90, 135...)
		* Each propagation clone travels at a speed determined by the available bandwidth 
		* of the relay which forwarded it, so it can reach the maximum range faster than others propagations
		* The clone is called Discrete Propagation and it's a "piece" of a Perturbation
		*/
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
	
	
	/*
	 * This method represents the Automatic Retransmission Mechanism described
	 * in the paper (ARQ). In order to prevent massive flooding which such requests
	 * relays use a smart mechanism which prevents the simulation from blocking.(details below)
	 */
	@ScheduledMethod(start=1, interval=1, priority=20)
	public void automaticRetransmissionMechanism() {
		//Get the current tick number
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		/*
		* Relay activate this mechanism at different times. 
		* This is needed in order to reduce the flooding effect 
		* when all the relays start propagating ARQs at the same time.
		* e.g: Relay 1, 11, 21, 31....91, 101, 111, 121 etc send ARQ at tick 1, 11, 21, 31.....
		* Hence, relays whose id end with 1, send ARQ during ticks which also end with number 1
		*/
		if(tickCount % 10 == id % 10) {
			//For each known source, send a request for the next expected perturbation
			for(Map.Entry<Integer, ArrayList<Perturbation>> perSourceLog : log.entrySet()) {
				//Avoid sending ARQs for your own perturbations
				if(perSourceLog.getKey() != this.id) {
					List <Perturbation> perturbations = perSourceLog.getValue();
					//Find out which was the last perturbation for that source
					Perturbation latestPerturbation = perturbations.get(perturbations.size() - 1);
					System.out.println("Relay(" + id + "): broadcasting ARQ for perturbation " 
							+ "<src=" + latestPerturbation.getSource() + ", "
							+ "ref=" + (latestPerturbation.getReference()+1) + ">");
					//This is needed in order to identify ARQs, since src and ref are not enough.
					//Multiple relays might send ARQs for the same perturbation, and in this can 
					//we need a way to distinguish them. UUIDs is the solution to this problem
					final String uuid = UUID.randomUUID().toString();
					seenARQs.add(uuid);
					forward(new Perturbation(latestPerturbation.getSource(), 
							latestPerturbation.getReference() + 1, Type.RETRANSMISSION_REQUEST, uuid)); 
				}
			}
		}
	}

	/*
	 * At each tick, relays "look" around them to see if there are any 
	 * new perturbations in their local domain broadcast
	 */
	@ScheduledMethod(start=1, interval=1, priority=80)
	public void sense() {
		Context<Object> context = ContextUtils.getContext(this);
		List<Perturbation> perturbations = new ArrayList<Perturbation>();
		
		//Build a query which returns all the perturbations in this relay's range
		ContinuousWithin<Object> nearbyQuery = new ContinuousWithin(context, this, 5.0);
		//thread safe method to inspect the nearby propagations
		CopyOnWriteArrayList<Object> nearbyObjects = new CopyOnWriteArrayList<>();
		nearbyQuery.query().forEach(nearbyObjects::add);

		//Iterate through the found perturbations
		for(Object obj : nearbyObjects) {
			if(obj instanceof DiscretePropagation && ((DiscretePropagation) obj).propagated) {
				DiscretePropagation propagation = (DiscretePropagation) obj;
				Perturbation p = propagation.getPerturbation();
				Relay forwarder = propagation.getForwarder();
				
				if(p.getType() == Type.RETRANSMISSION_REQUEST) {
					int src = p.getSource();
					int ref = p.getReference();
					if(!seenARQs.contains((String)p.getPayload())) {
						System.out.println("Relay(" + id + "): sensed retransimission request for P="
								+ "<src=" + src + ", ref=" + ref +">");
						//add the ARQ to the set so it won't be processed twice
						seenARQs.add((String)p.getPayload());
						//If the requested perturbation is in the log, forward it
						if(log.get(src) != null) {
							for(Perturbation Q : log.get(src)) 
								if(Q.getReference() == ref)
									forward(Q);
						}
					}

				//Check if the perturbation is the next expected perturbation
				} else if((frontier.get(p.getSource()) == null
						|| p.getReference() >= frontier.get(p.getSource())) 
						&& p.getSource() != id && p.getType() != Type.RETRANSMISSION_REQUEST) { 
						//Relay__II&& !isInBag(p)) {//don't sense self-generated perturbations
					
					//Relay__II addToBag(p);
					
					System.out.println("Relay(" + id + "): sensed perturbation"
							+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() 
							+ "> fowarded by " + forwarder.getId());
					
					//calculate the next expected perturbation reference
					Integer nextRef = frontier.get(p.getSource());
					//in case it is null, it means this is the first perturbation for this source
					if(nextRef == null) 
						nextRef = p.getReference(); 
					if(nextRef == p.getReference()) {
						forward(p);
						deliver(p);
						frontier.put(p.getSource(), nextRef + 1);//update frontier

						//add an edge between the relay who forwarded the perturbation and the receiver
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
//										nextRef = Q.getReference(); 
//									if(nextRef == Q.getReference())
//										changes = true;
//										forward(Q);
//										deliver(Q);
//										frontier.put(Q.getSource(), nextRef + 1);
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
	
	//Deliver a perturbation
	private void deliver(Perturbation p) {
		//Filter out the perturbations generated by this relay
		if(p.getSource() != this.id) {
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
				//Check if I'm subscribed to the group and/or topic
				if(subscriptions.get(m.getGroup()) != null) {
					List<String> topics = subscriptions.get(m.getGroup());
					if(topics == null || topics.contains(m.getTopic())) {
						
						System.out.println("Relay(" + id + "): received a new message "
								+ "for the subscription in the group " + m.getGroup());
					}
				}
			} else if(p.getType() == Type.ENCRYPTED_UNICAST) {
				//Attempt to decrypt the message
				SealedObject encryptedMessage = (SealedObject)p.getPayload();
				UnicastMessage decryptedMessage = AsymmetricCryptography.decryptPayload(
						encryptedMessage, security.KeyManager.PRIVATE_KEYS[id]);
				
				//If the result is null, it means the private key is not the correct one,
				//therefore the perturbation is addressed to a different relay.
				if(decryptedMessage != null) {
					System.out.println("Relay(" + id + "): Relay(" + p.getSource() + ") sent me an encrypted private message");
				} else {
					System.out.println("Relay(" + id + "): Encrypted message from Relay " +
							p.getSource() + " couldn't be decrypted.");
				}
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
	
	//Perturbation broadcast
	private void broadcast(Object value) {
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
	
	//Helper method for generating a private message which can then be forwarded using broadcast primitives
	private void privateSend(int destination, Object value) {
		System.out.println("Relay(" + id + "): generating perturbation"
				+ "<" + id + ", " + this.clock + ", P.M.>");
		
		//Create the payload and encrypt it
		UnicastMessage m = new UnicastMessage(destination, value);
		SealedObject secret = AsymmetricCryptography.encryptPayload(m, KeyManager.PUBLIC_KEYS[destination]);
		
		//Generate perturbation 
		Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.ENCRYPTED_UNICAST, secret);
		forward(perturbation);
		deliver(perturbation);
		
		//Write generated message for latency measurement
		if(this.id == Options.NODE_A_LATENCY)
			DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
	}
	
	//Helper method for generating a group message which can then be forwarded using broadcast primitives
	private void groupSend(int groupId, String topic, Object value) {
				System.out.println("Relay(" + id + "): generating perturbation"
						+ "<" + id + ", " + this.clock + ", G.M.>");
				
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
			DataCollector.saveIsolation(RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "IsolatedRelays.csv");
		
		return "";
	}
	
	//When a perturbation reaches its maximum range, it release the used bandwidth
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

