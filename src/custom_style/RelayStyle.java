package custom_style;

import java.awt.Color;
import java.awt.Font;

import agents.Relay;
import communication.DiscretePropagation;
import communication.Perturbation.Type;
import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;
import saf.v3d.scene.Position;

public class RelayStyle extends DefaultStyleOGL2D{
	@Override
    public Color getColor(Object o) {
		if(((Relay)o).isCrashed()) 
			return Color.RED;
		else
			return Color.BLUE;
    }
	
	@Override
	public String getLabel(Object o) {
		return Integer.toString(((Relay)o).getId());
	}
	
	@Override
	public Position getLabelPosition(Object object) {
		return Position.SOUTH;
	}
	
	@Override
	public Font getLabelFont(Object object) {
		return (new Font ("Arial", Font.PLAIN, 22));
	}
	
	
}