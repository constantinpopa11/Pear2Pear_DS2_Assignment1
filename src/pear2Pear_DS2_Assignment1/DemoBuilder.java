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
		
		ContinuousSpaceFactory spaceFactory =
				ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		ContinuousSpace<Object> space =
				spaceFactory.createContinuousSpace(
						"space", context,
						new RandomCartesianAdder<Object>(),
						new repast.simphony.space.continuous.StrictBorders(), 50, 50
						);

		GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
		Grid<Object> grid = gridFactory.createGrid(
				"grid", context,
				new GridBuilderParameters <Object>(
						new StrictBorders(),
						new SimpleGridAdder<Object>(),
						true, 50, 50
						)
				);
		
		int relayCount = params.getInteger("relayCount");
		for (int i = 0; i < relayCount ; i ++) {
			context.add (new Relay(space , grid, i));
		}
		
		for (Object obj : context) {
			NdPoint pt = space.getLocation(obj);
			grid.moveTo(obj , (int)pt.getX(), (int)pt.getY());
		}
		
        KeyManager km;
        try {
            km = new KeyManager(1024, params.getInteger("relayCount"));
            km.createKeys();

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            System.err.println(e.getMessage());
        }


		return context ;
	}

}
