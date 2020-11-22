package communication;

public class MulticastMessage {
	private int group;
	private String topic;
	private Object value;
	
	
	
	public MulticastMessage(int group, String topic, Object value) {
		super();
		this.group = group;
		this.topic = topic;
		this.value = value;
	}
	public int getGroup() {
		return group;
	}
	public void setGroup(int group) {
		this.group = group;
	}
	public String getTopic() {
		return topic;
	}
	public void setTopic(String topic) {
		this.topic = topic;
	}
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
}
