package us.dot.its.jpo.ode.model;


public class OdeMapMetadata extends OdeLogMetadata {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public enum MapSource {
		RSU, V2X, MMITSS, unknown
	}

	private MapSource mapSource;

	public MapSource getMapSource() {
		return mapSource;
	}
	public OdeMapMetadata() {
		super();
	}

	public OdeMapMetadata(OdeMsgPayload payload) {
		super(payload);
	}

	public OdeMapMetadata(OdeMsgPayload payload, SerialId serialId, String receivedAt) {

	}

	public void setMapSource(MapSource mapSource) {
		this.mapSource = mapSource;
	}
	

}
