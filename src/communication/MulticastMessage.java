package communication;

/*
 * This class encapsulates the data needed for group communication and
 * publish&subscribe architectures. They can use the same template because
 * the group identifier is present in both communication models.
 * Furthermore, the topic can be set or not. In case it is null,
 * it means that the message should be delivered to everyone in the group.
 */
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
