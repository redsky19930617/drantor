package org.owntracks.android.support;

import android.content.Context;
import android.preference.PreferenceManager;

import org.owntracks.android.App;

import java.util.Date;

/**
 * Created by alexander on 6/25/15.
 */
public class Statistics {

    public static final String REFERENCE = "REFERENCE";
    public static final String APP_START = "APP_START";

    public static final String SERVICE_PROXY_START = "SERVICE_PROXY_START";
    public static final String SERVICE_LOCATOR_PLAY_CONNECTED = "SERVICE_LOCATOR_PLAY_CONNECTED";
    public static final String SERVICE_LOCATOR_BACKGROUND_LOCATION_LAST_CHANGE = "SERVICE_LOCATOR_BACKGROUND_LOCATION_LAST_CHANGE";
    public static final String SERVICE_LOCATOR_BACKGROUND_LOCATION_CHANGES = "SERVICE_LOCATOR_BACKGROUND_LOCATION_CHANGES";

    public static final String SERVICE_BROKER_LOCATION_PUBLISH_INIT = "SERVICE_BROKER_LOCATION_PUBLISH_INIT";
    public static final String SERVICE_BROKER_LOCATION_PUBLISH_INIT_QOS0_DROP = "SERVICE_BROKER_LOCATION_PUBLISH_INIT_QOS0_DROP";

    public static final String SERVICE_BROKER_LOCATION_PUBLISH_INIT_QOS12_QUEUE = "SERVICE_BROKER_LOCATION_PUBLISH_INIT_QOS12_QUEUE";


    public static final String SERVICE_BROKER_LOCATION_PUBLISH_SUCCESS = "SERVICE_BROKER_LOCATION_PUBLISH_SUCCESS";

    public static final String SERVICE_BROKER_QUEUE_LENGTH = "SERVICE_BROKER_QUEUE_LENGTH";
    public static final String SERVICE_BROKER_CONNECTS = "SERVICE_BROKER_CONNECTS";


    public static void incrementCounter(Context c, String key) {
        PreferenceManager.getDefaultSharedPreferences(c).edit().putInt(key, PreferenceManager.getDefaultSharedPreferences(c).getInt(key, 0)+1).commit();
    }

    public static int getCounter(Context c, String key) {
        return PreferenceManager.getDefaultSharedPreferences(c).getInt(key, 0);
    }

    public static void setInt(Context c, String key, int value) {
        PreferenceManager.getDefaultSharedPreferences(c).edit().putInt(key, value).commit();
    }

    public static void setTime(Context c, String key) {
        PreferenceManager.getDefaultSharedPreferences(c).edit().putLong(key, (new Date().getTime())).commit();
    }

    public static Date getTime(Context c, String key) {
       return new Date(PreferenceManager.getDefaultSharedPreferences(c).getLong(key, 0));
    }

    public static void clearCounters(Context c) {
        setTime(c, REFERENCE);
        setInt(c,SERVICE_LOCATOR_BACKGROUND_LOCATION_CHANGES, 0);
        setInt(c,SERVICE_BROKER_LOCATION_PUBLISH_INIT, 0);
        setInt(c,SERVICE_BROKER_LOCATION_PUBLISH_INIT_QOS0_DROP, 0);
        setInt(c,SERVICE_BROKER_LOCATION_PUBLISH_INIT_QOS12_QUEUE, 0);
        setInt(c,SERVICE_BROKER_LOCATION_PUBLISH_SUCCESS, 0);
        setInt(c,SERVICE_BROKER_CONNECTS, 0);

    }
}
