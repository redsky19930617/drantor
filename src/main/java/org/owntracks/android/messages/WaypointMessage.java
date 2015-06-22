package org.owntracks.android.messages;

import org.json.JSONException;

import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.owntracks.android.db.Waypoint;

public class WaypointMessage extends Message {
    private static final String TAG = "WaypointMessage";

    Waypoint waypoint;
    String trackerId;
	public WaypointMessage(Waypoint w) {
        super();

        this.waypoint = w;
    }

	@Override
	public String toString() {
        	return toJSONObject().toString();
	}

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("_type", "waypoint")
                    .put("lat", this.waypoint.getLatitude())
                    .put("lon", this.waypoint.getLongitude())
                    .put("tst", (long) (TimeUnit.MILLISECONDS.toSeconds(this.waypoint.getDate().getTime())))
                    .put("rad", this.waypoint.getRadius() != null ? this.waypoint.getRadius() : 0);

            if (this.trackerId != null && !this.trackerId.isEmpty()) 
                json.put("tid", this.trackerId);

            String desc = this.waypoint.getDescription();

            if(this.waypoint.getSsid() != null && !this.waypoint.getSsid().isEmpty())
                desc += "$"+this.waypoint.getSsid();


            json.put("desc", desc);




        }catch (JSONException e) {
        }

        return json;
    }

    public void setTrackerId(String tid) {
        this.trackerId = tid;
    }
    public String getTrackerId(){ return this.trackerId; }
}
