package org.owntracks.android.services;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;
import org.owntracks.android.activities.ActivityLauncher;
import org.owntracks.android.App;
import org.owntracks.android.R;
import org.owntracks.android.activities.ActivityMessages;
import org.owntracks.android.db.ContactLink;
import org.owntracks.android.db.ContactLinkDao;
import org.owntracks.android.db.MessageDao;
import org.owntracks.android.messages.CardMessage;
import org.owntracks.android.messages.ConfigurationMessage;
import org.owntracks.android.messages.MsgMessage;
import org.owntracks.android.messages.TransitionMessage;
import org.owntracks.android.model.Contact;
import org.owntracks.android.model.GeocodableLocation;
import org.owntracks.android.messages.LocationMessage;
import org.owntracks.android.support.Events;
import org.owntracks.android.support.Preferences;
import org.owntracks.android.support.ReverseGeocodingTask;
import org.owntracks.android.support.StaticHandler;
import org.owntracks.android.support.StaticHandlerInterface;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.Geofence;

import de.greenrobot.dao.query.Query;
import de.greenrobot.dao.query.QueryBuilder;
import de.greenrobot.event.EventBus;

public class ServiceApplication implements ProxyableService,
		StaticHandlerInterface {
    private static final String TAG = "ServiceApplication";

    public static final int NOTIFCATION_ID = 1338;
    public static final int NOTIFCATION_ID_TICKER = 1339;
    public static final int NOTIFCATION_ID_CONTACT_TRANSITION = 1340;
    public static final int NOTIFCATION_ID_MESSAGE = 1341;

    final static String NOTIFCATION_ID_CONTACT_TRANSITION_GROUP = "org.owntracks.android.group.transition";
    final static String NOTIFCATION_ID_MESSAGE_GROUP = "org.owntracks.android.group.message";

    public static final String INTENT_ACTION_CANCEL_TRANSITION_NOTIFICATION = "org.owntracks.android.intent.INTENT_ACTION_CANCEL_TRANSITION_NOTIFICATION";
    public static final String INTENT_ACTION_CANCEL_MESSAGE_NOTIFICATION = "org.owntracks.android.intent.INTENT_ACTION_CANCEL_MESSAGE_NOTIFICATION";



    private static SharedPreferences sharedPreferences;
	private SharedPreferences.OnSharedPreferenceChangeListener preferencesChangedListener;
	private NotificationManager notificationManager;
	private static NotificationCompat.Builder notificationBuilder;
    private static NotificationCompat.Builder notificationBuilderTicker;

    private static boolean playServicesAvailable;
	private GeocodableLocation lastPublishedLocation;
	private Date lastPublishedLocationTime;
	private boolean even = false;
	private Handler handler;
	//private int mContactCount;
	private ServiceProxy context;
    private HandlerThread notificationThread;
    private Handler notificationHandler;
    private SimpleDateFormat transitionDateFormater;

    private LinkedList<Spannable> contactTransitionNotifications;
    private LinkedList<Spannable> messageNotifications;

    private HashSet<Uri> contactTransitionNotificationsContactUris;

    @Override
	public void onCreate(ServiceProxy context) {
		this.context = context;
		checkPlayServices();
        this.notificationThread = new HandlerThread("NOTIFICATIONTHREAD");
        this.notificationThread.start();
        this.notificationHandler = new Handler(this.notificationThread.getLooper());
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.contactTransitionNotifications = new LinkedList<Spannable>();
        this.messageNotifications = new LinkedList<Spannable>();

        this.contactTransitionNotificationsContactUris = new HashSet<>();
		notificationBuilder = new NotificationCompat.Builder(context);
        notificationBuilderTicker = new NotificationCompat.Builder(context);

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.transitionDateFormater = new SimpleDateFormat("HH:mm", context.getResources().getConfiguration().locale);

		this.preferencesChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(
					SharedPreferences sharedPreference, String key) {
				if (key.equals(Preferences.getKey(R.string.keyNotification)) || key.equals(Preferences
                        .getKey(R.string.keyNotificationGeocoder)) || key.equals(Preferences
                        .getKey(R.string.keyNotificationLocation)))
					handleNotification();
			}

		};

		this.handler = new StaticHandler(this);

		sharedPreferences
				.registerOnSharedPreferenceChangeListener(this.preferencesChangedListener);
		handleNotification();

		//this.mContactCount = getContactCount();
		//context.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, this.mObserver);

	}

	@Override
	public void onDestroy() {
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        if (ServiceApplication.INTENT_ACTION_CANCEL_TRANSITION_NOTIFICATION.equals(intent.getAction())) {
            clearTransitionNotifications();
        } else if(ServiceApplication.INTENT_ACTION_CANCEL_MESSAGE_NOTIFICATION.equals(intent.getAction())) {
            clearMessageNotifications();
        }
        return 0;
    }



    @Override
	public void handleHandlerMessage(Message msg) {
		switch (msg.what) {
		case ReverseGeocodingTask.GEOCODER_RESULT:
			geocoderAvailableForLocation(((GeocodableLocation) msg.obj));
			break;
		}
	}

    public void onEventMainThread(Events.ClearLocationMessageReceived e) {
        App.removeContact(e.getContact());
    }

    public void onEvent(Events.MsgMessageReceived e) {
        MsgMessage mm = e.getMessage();
        String externalId = e.getTopic() + "$" + mm.getTst();

        org.owntracks.android.db.Message m = App.getMessageDao().queryBuilder().where(MessageDao.Properties.ExternalId.eq(externalId)).unique();
        if(m == null) {
            m = new org.owntracks.android.db.Message();
            m.setIcon(mm.getIcon());
            m.setPriority(mm.getPrio());
            m.setIcon(mm.getIcon());
            m.setIconUrl(mm.getIconUrl());
            m.setUrl(mm.getUrl());
            m.setExternalId(externalId);
            m.setDescription(mm.getDesc());
            m.setTitle(mm.getTitle());
            m.setTst(mm.getTst());
            if(mm.getMttl() != 0)
            m.setExpiresTst(mm.getTst()+mm.getMttl());

            if(e.getTopic() == Preferences.getBroadcastMessageTopic())
                m.setChannel("broadcast");
            else if(e.getTopic().startsWith(Preferences.getDeviceTopic(true)))
                m.setChannel("direct");
            else
                try { m.setChannel(e.getTopic().split("/")[1]); } catch (IndexOutOfBoundsException exception) {  m.setChannel("undefined");  }

            App.getMessageDao().insert(m);
            EventBus.getDefault().post(new Events.MessageAdded(m));
        }
    }


    public void onEvent(Events.MessageAdded e) {
        if(App.isInForeground())
            return;

        String channel = "#"+e.getMessage().getChannel();
        Spannable message = new SpannableString(channel + ": "+ e.getMessage().getDescription());
        message.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, channel.length()+1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        messageNotifications.push(message);


        if(messageCancelIntent != null)
            messageCancelIntent.cancel();

        this.messageCancelIntent = ServiceProxy.getPendingIntentForService(
                this.context, ServiceProxy.SERVICE_APP,
                ServiceApplication.INTENT_ACTION_CANCEL_MESSAGE_NOTIFICATION, null);

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (Spannable text : this.messageNotifications ) {
            style.addLine(text);
        }


        String title = "Messages";
        style.setBigContentTitle(title);



        Intent resultIntent = new Intent(this.context, ActivityMessages.class);

        resultIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                this.context, 0, resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        notificationBuilder.setContentIntent(resultPendingIntent);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.context)
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(style)
                .setContentText(this.messageNotifications.getFirst()) // InboxStyle doesn't show text when only one line is added. In this case ContentText is shown
                .setContentTitle(title) // InboxStyle doesn't show title only one line is added. In this case ContentTitle is shown
                .setGroup(NOTIFCATION_ID_MESSAGE_GROUP)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setNumber(messageNotifications.size())
                .setWhen(e.getMessage().getTst()*1000)
                .setContentIntent(resultPendingIntent)
                .setDeleteIntent(this.messageCancelIntent);


        if(android.os.Build.VERSION.SDK_INT >= 21) {
            builder.setColor(context.getResources().getColor(R.color.primary));
            builder.setPriority(Notification.PRIORITY_MIN);
            builder.setCategory(Notification.CATEGORY_SERVICE);
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        notificationManager.notify(NOTIFCATION_ID_MESSAGE, builder.build());

    }


    private Contact lazyUpdateContactFromMessage(String topic, GeocodableLocation l, String trackerId) {
        Log.v(TAG, "lazyUpdateContactFromMessage for: " +topic);
        org.owntracks.android.model.Contact c = App.getContact(topic);

        if (c == null) {
            c = App.getInitializingContact(topic);


            if(c == null) {
                Log.v(TAG, "creating new contact without card: " + topic);
                c = new org.owntracks.android.model.Contact(topic);
            } else {
                Log.v(TAG, "creating unintialized contact with card: " + topic);
            }
            resolveContact(c);
            c.setLocation(l);
            c.setTrackerId(trackerId);
            App.addContact(c);
        } else {
            c.setLocation(l);
            c.setTrackerId(trackerId);
            EventBus.getDefault().post(new Events.ContactUpdated(c));
        }
        return c;
    }

    private String getBaseTopic(String forStr, String topic) {
        if(topic.endsWith(forStr))
            return topic.substring(0, (topic.length()  - forStr.length()));
        else
            return topic;
    }

    private String getBaseTopicForEvent(String topic) {
        return getBaseTopic(Preferences.getPubTopicEventsPart(), topic);
    }

    private String getBaseTopicForInfo(String topic) {
        return getBaseTopic(Preferences.getPubTopicInfoPart(), topic);
    }

    public void onEventMainThread(Events.TransitionMessageReceived e) {
        Contact c = lazyUpdateContactFromMessage(getBaseTopicForEvent(e.getTopic()), e.getGeocodableLocation(), e.getTransitionMessage().getTrackerId());

        if(e.getTransitionMessage().isRetained() && Preferences.getNotificationOnTransitionMessage()) {
            Log.v(TAG, "transition: " + e.getTransitionMessage().getTransition());
            addTransitionMessageNotification(e, c);
        }



    }


    public void onEventMainThread(Events.CardMessageReceived e) {
        String topic = getBaseTopicForInfo(e.getTopic());
        Contact c = App.getContact(topic);
        Log.v(TAG, "card message received for: " + topic);

        if(App.getInitializingContact(topic) != null) {
                Log.v(TAG, "ignoring second card for uninitialized contact " + topic);
                return;
        }
        if(c == null) {
            Log.v(TAG, "initializing card for: " + topic);

            c = new Contact(topic);
            c.setCardFace(e.getCardMessage().getFace());
            c.setCardName(e.getCardMessage().getName());

            App.addUninitializedContact(c);
         } else {

            Log.v(TAG, "updating card for existing contact: " + topic);
            c.setCardFace(e.getCardMessage().getFace());
            c.setCardName(e.getCardMessage().getName());
            EventBus.getDefault().post(new Events.ContactUpdated(c));
        }
    }

    public void onEventMainThread(Events.LocationMessageReceived e) {
        lazyUpdateContactFromMessage(e.getTopic(), e.getGeocodableLocation(), e.getLocationMessage().getTrackerId());
	}

    public void onEventMainThread(Events.ConfigurationMessageReceived e){

        Preferences.fromJsonObject(e.getConfigurationMessage().toJSONObject());

        // Reconnect to broker after new configuration has been saved.
        Runnable r = new Runnable() {

            @Override
            public void run() {
                ServiceProxy.getServiceBroker().reconnect();
            }
        };
        new Thread(r).start();

    }

	private Notification notification;
	private PendingIntent notificationIntent;

    private PendingIntent transitionCancelIntent;
    private PendingIntent messageCancelIntent;

	/**
	 * @category NOTIFICATION HANDLING
	 */
	private void handleNotification() {
		this.context.stopForeground(true);

		if (this.notificationManager != null)
			this.notificationManager.cancelAll();

		if (Preferences.getNotification() || !playServicesAvailable)
			createNotification();

	}

	private void createNotification() {
		notificationBuilder = new NotificationCompat.Builder(this.context);

		Intent resultIntent = new Intent(this.context, ActivityLauncher.class);
		resultIntent.setAction("android.intent.action.MAIN");
		resultIntent.addCategory("android.intent.category.LAUNCHER");

		resultIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent resultPendingIntent = PendingIntent.getActivity(
				this.context, 0, resultIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		notificationBuilder.setContentIntent(resultPendingIntent);

		this.notificationIntent = ServiceProxy.getBroadcastIntentForService(
				this.context, ServiceProxy.SERVICE_LOCATOR,
				ServiceLocator.RECEIVER_ACTION_PUBLISH_LASTKNOWN_MANUAL, null);
		notificationBuilder.addAction(R.drawable.ic_report_notification, this.context.getString(R.string.publish), this.notificationIntent);
		updateNotification();
	}

	private static void showPlayServicesNotAvilableNotification() {
		NotificationCompat.Builder nb = new NotificationCompat.Builder(
				App.getContext());
		NotificationManager nm = (NotificationManager) App.getContext()
				.getSystemService(Context.NOTIFICATION_SERVICE);

		//nb.setContentTitle(App.getContext().getString(R.string.app_name))
		//		.setSmallIcon(R.drawable.ic_notification)
		//		.setContentText("Google Play Services are not available")
				//.setPriority(NotificationCompat.PRIORITY_MIN);
		//nm.notify(Defaults.NOTIFCATION_ID, nb.build());

	}

	public void updateTicker(String text, boolean vibrate) {
        // API >= 21 doesn't have a ticker
        if(android.os.Build.VERSION.SDK_INT >= 21) {
            notificationBuilderTicker.setPriority(NotificationCompat.PRIORITY_HIGH);
            notificationBuilderTicker.setColor(context.getResources().getColor(R.color.primary));
            notificationBuilderTicker.setSmallIcon(R.drawable.ic_notification);
            notificationBuilderTicker.setCategory(Notification.CATEGORY_SERVICE);
            notificationBuilderTicker.setVisibility(Notification.VISIBILITY_PUBLIC);
            notificationBuilderTicker.setContentTitle(context.getString(R.string.app_name));
            notificationBuilderTicker.setContentText(text + ((this.even = !this.even) ? " " : ""));
            notificationBuilderTicker.setAutoCancel(true);



        } else {
            notificationBuilderTicker.setSmallIcon(R.drawable.ic_notification);
            notificationBuilderTicker.setTicker(text + ((this.even = !this.even) ? " " : ""));

        }
        Log.v(TAG, "vibrate: " + vibrate);
        if(vibrate == true) {
            notificationBuilderTicker.setVibrate(new long[]{0, 500}); // 0 ms delay, 500 ms vibration
        } else {
            notificationBuilderTicker.setVibrate(new long[]{0, 0}); // 0 ms delay, 500 ms vibration
        }

		// Clear ticker
        this.notificationManager.notify(NOTIFCATION_ID_TICKER, notificationBuilderTicker.build());

		// if the notification is not enabled, the ticker will create an empty
		// one that we get rid of
        if (!Preferences.getNotification()) {
            this.notificationManager.cancel(NOTIFCATION_ID_TICKER);
        } else {

            notificationHandler.postDelayed(new Runnable() {

                public void run() {
                    notificationManager.cancel(NOTIFCATION_ID_TICKER);
                }}, 1500);

        }
	}

	public void updateNotification() {
		if (!Preferences.getNotification() || !playServicesAvailable)
			return;

		String title;
		String subtitle;
		long time = 0;

		if ((this.lastPublishedLocation != null) && Preferences.getNotificationLocation()) {
			time = this.lastPublishedLocationTime.getTime();

			if ((this.lastPublishedLocation.getGeocoder() != null) && Preferences.getNotificationGeocoder()) {
				title = this.lastPublishedLocation.toString();
			} else {
				title = this.lastPublishedLocation.toLatLonString();
			}
		} else {
			title = this.context.getString(R.string.app_name);
		}


        subtitle = ServiceBroker.getStateAsString(this.context);

        notificationBuilder.setContentTitle(title).setSmallIcon(R.drawable.ic_notification).setContentText(subtitle);

        if(android.os.Build.VERSION.SDK_INT >= 21) {
            notificationBuilder.setColor(context.getResources().getColor(R.color.primary));
            notificationBuilder.setPriority(Notification.PRIORITY_MIN);
            notificationBuilder.setCategory(Notification.CATEGORY_SERVICE);
            notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        if (time != 0)
			notificationBuilder.setWhen(this.lastPublishedLocationTime.getTime());

		this.notification = notificationBuilder.build();
		this.context.startForeground(NOTIFCATION_ID, this.notification);
	}


    public void addTransitionMessageNotification(Events.TransitionMessageReceived e, Contact c) {
        String location = e.getTransitionMessage().getDescription();
        if(location == null) {
            location = "a location";
        }

        String name = c.getDisplayName();

        if(name == null) {
            name = e.getTransitionMessage().getTrackerId();
        }

        if(name == null) {
            name = e.getTopic();
        }

        String transition;
        if(e.getTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            transition = context.getString(R.string.transitionentering);
        } else {
            transition = context.getString(R.string.transitionleaving);
        }
        String dateStr = transitionDateFormater.format(new Date());
        Spannable message = new SpannableString(dateStr + ": "+ name + " " + transition + " " + location);
        message.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, dateStr.length()+1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        contactTransitionNotifications.push(message);

        if(c.getLinkLookupUri() != null)
            this.contactTransitionNotificationsContactUris.add(c.getLinkLookupUri());

        if(transitionCancelIntent != null)
            transitionCancelIntent.cancel();

        this.transitionCancelIntent = ServiceProxy.getPendingIntentForService(
                this.context, ServiceProxy.SERVICE_APP,
                ServiceApplication.INTENT_ACTION_CANCEL_TRANSITION_NOTIFICATION, null);

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (Spannable text : this.contactTransitionNotifications) {
            style.addLine(text);
        }


        String title = "Waypoint transitions";
        style.setBigContentTitle(title);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.context)
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(style)
                .setContentText(this.contactTransitionNotifications.getFirst()) // InboxStyle doesn't show text when only one line is added. In this case ContentText is shown
                .setContentTitle(title) // InboxStyle doesn't show title only one line is added. In this case ContentTitle is shown
                .setGroup(NOTIFCATION_ID_CONTACT_TRANSITION_GROUP)
                .setAutoCancel(true)
                .setShowWhen(false)
                .setNumber(this.contactTransitionNotifications.size())
                .setDeleteIntent(this.transitionCancelIntent);

        for(Uri uri : this.contactTransitionNotificationsContactUris) {
            builder.addPerson(uri.toString());
        }

        if(android.os.Build.VERSION.SDK_INT >= 21) {
            builder.setColor(context.getResources().getColor(R.color.primary));
            builder.setPriority(Notification.PRIORITY_MIN);
            builder.setCategory(Notification.CATEGORY_SERVICE);
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        notificationManager.notify(NOTIFCATION_ID_CONTACT_TRANSITION, builder.build());
    }




    private void clearTransitionNotifications() {
        this.contactTransitionNotifications.clear(); // no need for synchronized, both add and clear are run on main thread
        this.contactTransitionNotificationsContactUris.clear();
        notificationManager.cancel(NOTIFCATION_ID_CONTACT_TRANSITION);

    }

    public void clearMessageNotifications() {
        this.messageNotifications.clear(); // no need for synchronized, both add and clear are run on main thread
        notificationManager.cancel(NOTIFCATION_ID_MESSAGE);
    }


    public void onEventMainThread(Events.StateChanged.ServiceBroker e) {
		updateNotification();
	}

	private void geocoderAvailableForLocation(GeocodableLocation l) {
		if (l == this.lastPublishedLocation)
			updateNotification();
	}

	public void onEvent(Events.WaypointTransition e) {
        if(Preferences.getNotificationTickerOnWaypointTransition()) {
            updateTicker(context.getString(e.getTransition() == Geofence.GEOFENCE_TRANSITION_ENTER ? R.string.transitionEntering : R.string.transitionLeaving) + " " + e.getWaypoint().getDescription(), Preferences.getNotificationVibrateOnWaypointTransition());
        }
	}

	public void onEvent(Events.PublishSuccessfull e) {
		if ((e.getExtra() != null) && (e.getExtra() instanceof LocationMessage) && !e.wasQueued()) {
			LocationMessage l = (LocationMessage) e.getExtra();

			this.lastPublishedLocation = l.getLocation();
			this.lastPublishedLocationTime = l.getLocation().getDate();

			if (Preferences.getNotificationGeocoder() && (l.getLocation().getGeocoder() == null))
				(new ReverseGeocodingTask(this.context, this.handler)).execute(new GeocodableLocation[] { l.getLocation() });

			updateNotification();

			if (Preferences.getNotificationTickerOnPublish() && !l.getSupressTicker())
				updateTicker(this.context.getString(R.string.statePublished), Preferences.getNotificationVibrateOnPublish());

		}
	}

	public static boolean checkPlayServices() {
		playServicesAvailable = ConnectionResult.SUCCESS == GooglePlayServicesUtil.isGooglePlayServicesAvailable(App.getContext());

		if (!playServicesAvailable)
			showPlayServicesNotAvilableNotification();

		return playServicesAvailable;

	}

	public void updateAllContacts() {
        for (Contact c : App.getCachedContacts().values()) {
            resolveContact(c);
            EventBus.getDefault().post(new Events.ContactUpdated(c));
        }

	}

	/*
	 * Resolves username and image either from a locally saved mapping or from
	 * synced cloud contacts. If no mapping is found, no name is set and the
	 * default image is assumed
	 */
	void resolveContact(Contact c) {

		long contactId = getContactId(c);
		boolean found = false;

		if (contactId <= 0) {
			setContactImageAndName(c, null, null);
            c.setHasLink(false);
			return;
		}

        // Resolve image and name from contact id
		Cursor cursor = this.context.getContentResolver().query(RawContacts.CONTENT_URI, null,ContactsContract.Data.CONTACT_ID + " = ?", new String[] { contactId + "" }, null);
		if (!cursor.isAfterLast()) {

			while (cursor.moveToNext()) {
				Bitmap image = Contact.resolveImage(this.context.getContentResolver(), contactId);
				String displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));


                setContactImageAndName(c, image, displayName);
                c.setHasLink(true);
                c.setLinkLookupURI(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contactId));
				found = true;
				break;
			}
		}

		if (!found) {
            setContactImageAndName(c, null, null);
            c.setHasLink(false);
        }
		cursor.close();

	}

	void setContactImageAndName(Contact c, Bitmap image, String name) {
		c.setLinkName(name);
		c.setLinkFace(image);
	}

	private long getContactId(Contact c) {

        ContactLink cl = queryContactLink(c);
        return cl != null ? cl.getContactId() : 0;
	}
    private ContactLink queryContactLink(Contact c) {
        QueryBuilder qb = App.getContactLinkDao().queryBuilder();

        Query query = qb.where(
                qb.and(
                        ContactLinkDao.Properties.Topic.eq(c.getTopic()),
                        ContactLinkDao.Properties.ModeId.eq(Preferences.getModeId())
                )
        ).build();

        return (ContactLink)query.unique();
    }


	public void linkContact(Contact c, long contactId) {
		ContactLink cl = new ContactLink(null, c.getTopic(), contactId, Preferences.getModeId());
		App.getContactLinkDao().insertOrReplace(cl);

		resolveContact(c);
		EventBus.getDefault().postSticky(new Events.ContactUpdated(c));
	}

    public void unlinkContact(Contact c) {
        ContactLink cl = queryContactLink(c);
        if(cl != null)
            App.getContactLinkDao().delete(cl);
        c.setLinkName(null);
        c.setLinkFace(null);
        c.setHasLink(false);
        EventBus.getDefault().postSticky(new Events.ContactUpdated(c));
    }

    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String msg = new String(message.getPayload());
        Log.v(TAG, "Received message: " + topic + " : " + msg);

        String type;
        JSONObject json;

        try {
            json = new JSONObject(msg);
            type = json.getString("_type");
        } catch (Exception e) {
            if(msg.isEmpty()) {
                Log.v(TAG, "Empty message received");

                Contact c = App.getContact(topic);
                if(c != null) {
                    Log.v(TAG, "Clearing contact location");

                    EventBus.getDefault().postSticky(new Events.ClearLocationMessageReceived(c));
                    return;
                }
            }

            Log.e(TAG, "Received invalid message: " + msg);
            return;
        }

        if (type.equals("location")) {
            try {
                LocationMessage lm = new LocationMessage(json);
                lm.setRetained(message.isRetained());
                EventBus.getDefault().postSticky(new Events.LocationMessageReceived(lm, topic));
            } catch (Exception e) {
                Log.e(TAG, "Message hat correct type but could not be handled. Message was: " + msg + ", error was: " + e.getMessage());
            }
        } else if (type.equals("card")) {
            CardMessage card = new CardMessage(json);
            EventBus.getDefault().postSticky(new Events.CardMessageReceived(card, topic));
        } else if (type.equals("transition")) {
            TransitionMessage tm = new TransitionMessage(json);
            tm.setRetained(message.isRetained());
            EventBus.getDefault().postSticky(new Events.TransitionMessageReceived(tm, topic));
        } else if (type.equals("msg")) {
            MsgMessage mm = new MsgMessage(json);
            EventBus.getDefault().post(new Events.MsgMessageReceived(mm, topic));
        } else if(type.equals("cmd") && topic.equals(Preferences.getPubTopicCommands())) {
            String action;
            try {
                action = json.getString("action");
            } catch (Exception e) {
                return;
            }

            switch (action) {
                case "reportLocation":
                    if (!Preferences.getRemoteCommandReportLocation()) {
                        Log.i(TAG, "ReportLocation remote command is disabled");
                        return;
                    }
                    ServiceProxy.getServiceLocator().publishResponseLocationMessage();

                    break;
                default:
                    Log.v(TAG, "Received cmd message with unsupported action (" + action + ")");
                    break;
            }

        } else if (type.equals("configuration") && topic.equals(Preferences.getPubTopicCommands()) ) {
            // read configuration message and post event only if Remote Configuration is enabled and this is a private broker
            if (!Preferences.getRemoteConfiguration() || Preferences.isModePublic()) {
                Log.i(TAG, "Remote Configuration is disabled");
                return;
            }
            ConfigurationMessage cm = new ConfigurationMessage(json);
            cm.setRetained(message.isRetained());
            EventBus.getDefault().post(new Events.ConfigurationMessageReceived(cm, topic));

        } else {
            Log.d(TAG, "Ignoring message (" + type + ") received on topic " + topic);
        }
    }
}
