package pear2Pear_DS2_Assignment1;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import agents.Relay;
import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.continuous.SimpleCartesianAdder;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;
import repast.simphony.space.grid.StrictBorders;
import security.KeyManager;

public class DemoBuilder implements ContextBuilder<Object> {

	@Override
	public Context build(Context<Object> context) {
		context.setId("Pear2Pear_DS2_Assignment1");

		//Used to retrieve relay count parameter
		Parameters params = RunEnvironment.getInstance().getParameters();
		
		//TODO: new parameter for the topology
		// O - ring
		// * - (extended) star
		// R - Random
		// | - Line
		//String topology = params.getString("topology");
		String topology = "O";
		
		ContinuousSpaceFactory spaceFactory =
				ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		ContinuousSpace<Object> space;
		
		//Instantiate space based on topology
		if(topology.compareTo("R") == 0) {
			space = spaceFactory.createContinuousSpace(
					"space", context,
					new RandomCartesianAdder<Object>(), //random location
					new repast.simphony.space.continuous.StrictBorders(), 50, 50
					);
		} else {
			space = spaceFactory.createContinuousSpace(
					"space", context,
					new SimpleCartesianAdder<Object>(), //location has still to be decided in this case
					new repast.simphony.space.continuous.StrictBorders(), 50, 50
					);
		}
		
		GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
		Grid<Object> grid = gridFactory.createGrid(
				"grid", context,
				new GridBuilderParameters <Object>(
						new StrictBorders(),
						new SimpleGridAdder<Object>(),
						true, 50, 50
						)
				);
		
		//Create and add the relays to the space
		int relayCount = params.getInteger("relayCount");
		for (int i = 0; i < relayCount ; i ++) {
			context.add (new Relay(space , grid, i));
		}
		
		//Position the nodes in a specific way to create the needed topology
		buildTopology(context, space, params);
		
		//Move to the relative grid cell
		for (Object obj : context) {
			NdPoint pt = space.getLocation(obj);
			grid.moveTo(obj , (int)pt.getX(), (int)pt.getY());
		}
		
		//Generate public and private keys
        KeyManager km;
        try {
            km = new KeyManager(1024, params.getInteger("relayCount"));
            km.createKeys();

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            System.out.println(e.getMessage());
        }


		return context ;
	}
	
	private void buildTopology(Context<Object> context, ContinuousSpace<Object> space, Parameters params) {
		String topology = "*";
		int n = params.getInteger("relayCount");
		
		//Ring topology
		if(topology.compareTo("O") == 0) {
			//TODO: parametrize
			double radius = (50.0 * 0.8) / 2;
			double offset = (50 / 2) - 0.01;
			int k = 0;
			
			for (Object obj : context) {
				double x = radius * Math.cos((k * 2 * Math.PI) / n) + offset;
				double y = radius * Math.sin((k * 2 * Math.PI) / n) + offset;
				space.moveTo(obj, x, y);
				k++;
			}
		} else if(topology.compareTo("*") == 0) { //Extended star topology
			int layer = 0;
			int k = 0;
			double offset = (50 / 2) - 0.01;
			
			for (Object obj : context) {
				//125 is the maximum amount of relays that can be used during simulation

				double layerSize = 4 * layer;
				
				if(layer == 0) { //This is the (first) central relay
					double centerX = 50.0 / 2;
					double centerY = 50.0 / 2;
					space.moveTo(obj, centerX, centerY);
					layer++;
				} else { //all the other relays follow this rule
					double radius = 3 * layer; //TODO: 3 should be equal to whatever relay range is
					
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
			double interval = (50.0 * 0.95) / n;
			double y = 50.0 / 2;
			double start = (50.0 * 0.05) / 2 ;
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

}
