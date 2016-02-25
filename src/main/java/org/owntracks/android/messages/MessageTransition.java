package org.owntracks.android.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.android.gms.location.Geofence;

import org.owntracks.android.support.IncomingMessageProcessor;
import org.owntracks.android.support.OutgoingMessageProcessor;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageTransition extends MessageBase{
    public static final String BASETOPIC_SUFFIX = "/event";
    public static final String EVENT_ENTER = "enter";
    public static final String EVENT_LEAVE = "leave";

    public static final String TRIGGER_BEACON = "b";
    public static final String TRIGGER_CIRCULAR = "c";

    public String getBaseTopicSuffix() {  return BASETOPIC_SUFFIX; }
    @JsonIgnore
    private int  transition = 0;

    private String desc;
    private String tid;
    private String trigger;
    private String event;
    private long tst;
    private long wtst;
    private float acc;
    private double lon;
    private double lat;

    @JsonIgnore
    public int getTransition() {
        return transition;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
        switch (event) {
            case EVENT_ENTER:
                transition = Geofence.GEOFENCE_TRANSITION_ENTER;
                break;
            case EVENT_LEAVE:
                transition = Geofence.GEOFENCE_TRANSITION_EXIT;
                break;
            default:
                transition = 0;
                break;
        }
    }

    @JsonIgnore
    public void setTransition(int transition) {
        this.transition = transition;
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER)
            event = EVENT_ENTER;
        else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT)
            event = EVENT_LEAVE;
        else
            event = null;
    }

    public long getTst() {
        return tst;
    }

    public void setTst(long tst) {
        this.tst = tst;
    }

    public long getWtst() {
        return wtst;
    }

    public void setWtst(long wtst) {
        this.wtst = wtst;
    }

    @Override
    public void processIncomingMessage(IncomingMessageProcessor handler) {
        handler.processMessage(this);
    }

    @Override
    public void processOutgoingMessage(OutgoingMessageProcessor handler) {
        handler.processMessage(this);
    }


    public void setLat(double lat) {
        this.lat = lat;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public void setAcc(float acc) {
        this.acc = acc;
    }

    public float getAcc() {
        return acc;
    }

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }
}
