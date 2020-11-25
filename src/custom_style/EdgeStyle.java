package custom_style;

import java.awt.Color;

import agents.Relay;
import repast.simphony.relogo.BaseLink;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.visualizationOGL2D.DefaultEdgeStyleOGL2D;

public class EdgeStyle extends DefaultEdgeStyleOGL2D{
	public Color getColor(RepastEdge<?> edge) {
		Relay source = (Relay) edge.getSource();
		Relay target = (Relay) edge.getTarget();
		if(source.isCrashed() || target.isCrashed()) {
			return Color.GRAY;
		}
		
		return Color.GREEN;
    }
}
