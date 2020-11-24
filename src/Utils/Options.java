package Utils;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;

public class Options {
	
	public static double PROBABILITY_OF_PERTURBATION;
	
	public static int RELAY_COUNT;
	public static int MAX_PROPAGATION_DISTANCE;
	public static String TOPOLOGY;
	public static int ENVIRONMENT_DIMENSION;
	public static double BANDWIDTH; 
	public static double PERTURBATION_SIZE;
	public static double MAX_PROPAGATION_SPEED;
	
	public static int NODE_A_BROADCAST;
	public static int NODE_B_BROADCAST;
	
	public static void load() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		
		PROBABILITY_OF_PERTURBATION = params.getDouble("PROBABILITY_OF_PERTURBATION");
		RELAY_COUNT = params.getInteger("RELAY_COUNT");
		MAX_PROPAGATION_DISTANCE = params.getInteger("MAX_PROPAGATION_DISTANCE");
		TOPOLOGY = params.getString("TOPOLOGY");
		ENVIRONMENT_DIMENSION = params.getInteger("ENVIRONMENT_DIMENSION");
		BANDWIDTH =  params.getInteger("BANDWIDTH");
		MAX_PROPAGATION_SPEED = 1.5;
		PERTURBATION_SIZE = 10.0;
		
	}
}
