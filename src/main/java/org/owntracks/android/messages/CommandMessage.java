package org.owntracks.android.messages;

import org.json.JSONException;
import org.json.JSONObject;

public class CommandMessage extends Message {
    private static final String TAG = "CommandMessage";

    public static final String ACTION_REPORT_LOCATION = "reportLocation";
    String action;
	public CommandMessage(String action) {
        super();
        this.action = action;
        this.ttl = 1; // if publish fails once, don't retry it
    }

	@Override
	public String toString() {
        	return toJSONObject().toString();
	}

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("_type", "cmd").put("action", this.action);

        }catch (JSONException e) {
        }

        return json;
    }

}
