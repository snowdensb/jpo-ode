package us.dot.its.jpo.ode.plugin.j2735;

import us.dot.its.jpo.ode.plugin.asn1.Asn1Object;

public class J2735LaneAttributes extends Asn1Object {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private J2735LaneDirection directionalUse;
	private J2735LaneSharing shareWith;
	private J2735LaneTypeAttributes laneType;

	public J2735LaneDirection getDirectionalUse() {
		return directionalUse;
	}

	public void setDirectionalUse(J2735LaneDirection directionalUse) {
		this.directionalUse = directionalUse;
	}

	public J2735LaneSharing getShareWith() {
		return shareWith;
	}

	public void setShareWith(J2735LaneSharing shareWith) {
		this.shareWith = shareWith;
	}

	public J2735LaneTypeAttributes getLaneType() {
		return laneType;
	}

	public void setLaneType(J2735LaneTypeAttributes laneType) {
		this.laneType = laneType;
	}

}
