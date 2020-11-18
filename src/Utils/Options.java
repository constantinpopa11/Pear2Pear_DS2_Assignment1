package Utils;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;

public class Options {
	
	public static double PROBABILITY_OF_PERTURBATION;
	
	public static int RELAY_COUNT;
	public static int MAX_PROPAGATION_DISTANCE;
	public static int NODE_A_LATENCY;
	public static int NODE_B_LATENCY;
	
	public static void load() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		
		PROBABILITY_OF_PERTURBATION = params.getDouble("PROBABILITY_OF_PERTURBATION");
		RELAY_COUNT = params.getInteger("RELAY_COUNT");
		MAX_PROPAGATION_DISTANCE = params.getInteger("MAX_PROPAGATION_DISTANCE");
		NODE_A_LATENCY = params.getInteger("NODE_A_LATENCY");
		NODE_B_LATENCY = params.getInteger("NODE_B_LATENCY");
		
	}
}
