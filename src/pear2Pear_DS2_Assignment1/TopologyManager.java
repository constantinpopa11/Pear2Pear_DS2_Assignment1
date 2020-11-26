package pear2Pear_DS2_Assignment1;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import Utils.Options;
import agents.Relay;
import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.continuous.SimpleCartesianAdder;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.util.ContextUtils;
import security.KeyManager;


public class TopologyManager {
	
	private static List<NdPoint> availableLocations = null; //available locations i.e crashed nodes locations
	private static ContinuousSpace<Object> space = null; //the space where relays are placed
	private static Context<Object> context; //simulation context
	private static int nextId; //next available relay id
	private static int currentRelayNum; //currently alive relays
	

    // static method to initialize the topology manager
    public static void initialize(Context<Object> ctx) { 
     
    	availableLocations = new ArrayList<>(); //instantiate the list
    	context = ctx;
    	
    	//Prepare the space for the relays
		ContinuousSpaceFactory spaceFactory =
				ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		
		/*
		 * O - ring
		 * * - (extended) star (please note this is not a comment mistake, the "*" symbol is used as an option
		 * R - Random
		 * | - Line
		 */	 
		String topology = Options.TOPOLOGY;
		
		//Instantiate space based on topology
		if(topology.compareTo("R") == 0) {
			space = spaceFactory.createContinuousSpace(
					"space", context,
					new RandomCartesianAdder<Object>(), //random location
					new repast.simphony.space.continuous.StrictBorders(), 
					Options.ENVIRONMENT_DIMENSION, Options.ENVIRONMENT_DIMENSION
					);
		} else {
			space = spaceFactory.createContinuousSpace(
					"space", context,
					new SimpleCartesianAdder<Object>(), //location has still to be decided in this case
					new repast.simphony.space.continuous.StrictBorders(), 
					Options.ENVIRONMENT_DIMENSION, Options.ENVIRONMENT_DIMENSION
					);
		}
		
		//Create and add the relays to the space
		for (int i = 0; i < Options.MAX_RELAY_COUNT; i ++) {
			context.add(new Relay(i));
		}
    	nextId = Options.MAX_RELAY_COUNT; //initially the next id is the initial amount of relays
    	currentRelayNum = Options.MAX_RELAY_COUNT;
     
    }
	
	public static void removeRelay(Relay relay) {
		availableLocations.add(space.getLocation(relay)); //mark the location of the crashed node as available
		currentRelayNum--;
		//Remove the edges which are connected to the crashed node
		Network<Object> net = (Network<Object>) context.getProjection("delivery network"); 
		CopyOnWriteArrayList<RepastEdge<Object>> edges = new CopyOnWriteArrayList<>(); //thread-safe method
		net.getOutEdges(relay).forEach(edges::add);
		//Iterate through edges and remove them
		for(RepastEdge<Object> edge : edges) {
			net.removeEdge(edge);
		}
		//Finally remove the relay
		context.remove(relay);
		
	}
	
	/*
	 * Since each relay has a given probability of crashing, the expected
	 * value of crashed relay per tick is equal to relay_number * prob_crash
	 * In order to compensate the number of crashed relays, we need to run
	 * this method a number of times, each time having a small probability
	 * to add a new relay to the context.
	 */
	@ScheduledMethod(start = 1, interval = 1)
	public static void addNewRelays() {
		for(int i=0; i<Options.MAX_RELAY_COUNT; i++) {
			double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
			//Check if there are available locations
			if(coinToss <= Options.JOIN_PROBABILITY && availableLocations.size() > 0) {
				double x = -1, y = -1;
				//Pick a random locations among the available ones
				if(Options.TOPOLOGY.compareTo("R") == 0) {
					 x = RandomHelper.nextDoubleFromTo(1, Options.ENVIRONMENT_DIMENSION-1);
					 y = RandomHelper.nextDoubleFromTo(1, Options.ENVIRONMENT_DIMENSION-1);
				} else {
					int index = RandomHelper.nextIntFromTo(0, availableLocations.size()-1);
					NdPoint spacePt = availableLocations.get(index);
					availableLocations.remove(spacePt);
					x = spacePt.getX();
					y = spacePt.getY();
				}
				
				//Generate private and public keys for the new relay
				KeyManager.generateKeys(nextId);
				System.out.println("Relay(" + nextId + ") is joining the context...");
				//Place the new relay in the context
				Relay relay = new Relay(nextId++);
				context.add(relay);
				space.moveTo(relay, x, y);
				currentRelayNum++;
			}
		}
	}
	
	//Position the relays in a structured way, in order to form a specific topology
	public static void buildTopology() {
		int n = Options.MAX_RELAY_COUNT;
		String topology = Options.TOPOLOGY;
		
		//Ring topology
		if(topology.compareTo("O") == 0) {
			double radius = (Options.ENVIRONMENT_DIMENSION * 0.9) / 2; //ring radius
			double offset = (Options.ENVIRONMENT_DIMENSION / 2) - 0.01; //useful for centering everything
			int k = 0; //counter
			
			for (Object obj : context) {
				//Calculate coordinates for each relay position
				double x = radius * Math.cos((k * 2 * Math.PI) / n) + offset;
				double y = radius * Math.sin((k * 2 * Math.PI) / n) + offset;
				space.moveTo(obj, x, y);
				k++;
			}
		} else if(topology.compareTo("*") == 0) { //Extended star topology
			int layer = 0; //a star is composed by multiple layers of nodes
			int k = 0;
			double offset = (Options.ENVIRONMENT_DIMENSION / 2) - 0.01; //useful for centering everything
			
			
			int tempSum = 0, layerNum = -1; //find out the number of layers
			for(int i=1; i<n && layerNum == -1; i++) {
				tempSum += i;
				if((n - 1) - (4 * tempSum) <= 0)
					layerNum = i;
			}
			
			double interval = (Options.ENVIRONMENT_DIMENSION * 0.45) / layerNum;
			for (Object obj : context) {
				//125 is the maximum amount of relays that can be used during simulation

				double layerSize = 4 * layer;
				
				if(layer == 0) { //This is the (first) central relay
					double centerX = Options.ENVIRONMENT_DIMENSION / 2;
					double centerY = Options.ENVIRONMENT_DIMENSION / 2;
					space.moveTo(obj, centerX, centerY);
					layer++;
				} else { //all the other relays follow this rule
					double radius = interval * layer;
					
					double x = radius * Math.cos((k * 2 * Math.PI) / layerSize) + offset;
					double y = radius * Math.sin((k * 2 * Math.PI) / layerSize) + offset;
					space.moveTo(obj, x, y);
					k++;
					
					if(k == layerSize) {
						layer++;
						k = 0;
					}
				}
			}
		} else if(topology.compareTo("|") == 0) { // Line topology
			double interval = (Options.ENVIRONMENT_DIMENSION * 0.9) / (n-1); //distance adjacent relays
			double y = Options.ENVIRONMENT_DIMENSION / 2;
			double start = (Options.ENVIRONMENT_DIMENSION - ((n-1) * interval)) / 2; //position of first relay
			
			int k = 0;
			
			for (Object obj : context) {
				double x = start + (k * interval);
				space.moveTo(obj, x, y);
				k++;
			}			
		} else {
			return;
		}
	}
	
	public static ContinuousSpace<Object> getSpace() {
		return space;
	}
	
	public static int getCurrentRelayNum() {
		return currentRelayNum;
	}
	
	public static int getUniqueRelaysNum() {
		return nextId;
	}
	
}
