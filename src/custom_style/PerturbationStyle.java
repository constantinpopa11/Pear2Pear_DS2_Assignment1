package custom_style;


import java.awt.Color;

import communication.DiscretePropagation;
import communication.Perturbation.Type;
import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;

public class PerturbationStyle extends DefaultStyleOGL2D{
	@Override
    public Color getColor(Object o) {
		Type type = ((DiscretePropagation)o).getPerturbation().getType();
        if(type == Type.ENCRYPTED_UNICAST)
        	return Color.BLACK;
        else if(type == Type.MULTICAST_MESSAGE)
        	return Color.CYAN;
        else if(type == Type.UNICAST_MESSAGE)
        	return Color.PINK;
        else if(type == Type.RETRANSMISSION_REQUEST)
        	return Color.RED;
        else
        	return Color.ORANGE;
    }
}
