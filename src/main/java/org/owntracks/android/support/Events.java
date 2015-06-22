package org.owntracks.android.support;

import java.util.Date;

import org.owntracks.android.App;
import org.owntracks.android.db.Message;
import org.owntracks.android.db.Waypoint;
import org.owntracks.android.messages.CardMessage;
import org.owntracks.android.messages.ConfigurationMessage;
import org.owntracks.android.messages.MsgMessage;
import org.owntracks.android.messages.TransitionMessage;
import org.owntracks.android.messages.WaypointMessage;
import org.owntracks.android.model.Contact;
import org.owntracks.android.model.GeocodableLocation;
import org.owntracks.android.messages.LocationMessage;
import org.owntracks.android.services.ServiceBroker;
import org.owntracks.android.services.ServiceLocator;

import android.location.Location;

public class Events {

    public static class ModeChanged extends E {
        int newModeId;
        int oldModeId;

        public ModeChanged(int oldModeId, int newModeId) {
            this.newModeId = newModeId;
            this.oldModeId = oldModeId;
        }
        public int getNewModeId() {
            return newModeId;
        }
        public int getOldModeId() {
            return oldModeId;
        }

    }

	public static class WaypointTransition extends E {
		Waypoint w;
		int transition;

		public WaypointTransition(Waypoint w, int transition) {
			super();
			this.w = w;
			this.transition = transition;
		}

		public Waypoint getWaypoint() {
			return this.w;
		}

		public int getTransition() {
			return this.transition;
		}

	}

	public static class WaypointAddedByUser extends E {
		Waypoint w;

		public WaypointAddedByUser(Waypoint w) {
			super();
			this.w = w;
		}

		public Waypoint getWaypoint() {
			return this.w;
		}

	}
    public static class WaypointAdded extends E {
        Waypoint w;

        public WaypointAdded(Waypoint w) {
            super();
            this.w = w;
        }

        public Waypoint getWaypoint() {
            return this.w;
        }

    }


    public static class WaypointUpdated extends E {
        Waypoint w;

        public WaypointUpdated(Waypoint w) {
            super();
            this.w = w;
        }

        public Waypoint getWaypoint() {
            return this.w;
        }

    }

    public static class WaypointUpdatedByUser extends E {
        Waypoint w;

        public WaypointUpdatedByUser(Waypoint w) {
            super();
            this.w = w;
        }

        public Waypoint getWaypoint() {
            return this.w;
        }

    }


	public static class WaypointRemoved extends E {
		Waypoint w;

		public WaypointRemoved(Waypoint w) {
			super();
			this.w = w;
		}

		public Waypoint getWaypoint() {
			return this.w;
		}

	}

	public static abstract class E {
		Date date;

		public E() {
			this.date = new Date();
		}

		public Date getDate() {
			return this.date;
		}

	}

	public static class Dummy extends E {
		public Dummy() {
		}
	}

	public static class PublishSuccessfull extends E {
		Object extra;
        boolean wasQueued;

		public PublishSuccessfull(Object extra, boolean wasQueued) {
			super();
			this.extra = extra;
            this.wasQueued = wasQueued;
		}

		public Object getExtra() {
			return this.extra;
		}
        public boolean wasQueued() {return  this.wasQueued;}
	}

	public static class CurrentLocationUpdated extends E {
		GeocodableLocation l;

		public CurrentLocationUpdated(Location l) {
			super();
			this.l = new GeocodableLocation(l);
		}

		public CurrentLocationUpdated(GeocodableLocation l) {
			this.l = l;
		}

		public GeocodableLocation getGeocodableLocation() {
			return this.l;
		}

	}

	public static class ContactAdded extends E {
		Contact contact;

		public ContactAdded(Contact f) {
			super();
			this.contact = f;
		}

		public Contact getContact() {
			return this.contact;
		}

	}

    public static class ContactRemoved extends E{
        Contact contact;

        public ContactRemoved(Contact f) {
            super();
            this.contact = f;
        }

        public Contact getContact() {
            return this.contact;
        }
    }

    public static class ClearLocationMessageReceived extends E{
        Contact c;
        public ClearLocationMessageReceived(Contact c) {
            super();
            this.c = c;
        }

        public Contact getContact() {
            return c;
        }
    }

    public static class MsgMessageReceived {
        MsgMessage message;
        String topic;
        public MsgMessageReceived(MsgMessage message, String topic) {
            super();
            this.message = message;
            this.topic = topic;

        }
        public MsgMessage getMessage() {
            return this.message;
        }
        public String getTopic() {
            return this.topic;
        }

    }

    public static class CardMessageReceived extends E{
        String topic;
        CardMessage message;
        public CardMessageReceived(CardMessage m, String topic) {
            super();
            this.message = m;
            this.topic = topic;

        }

        public String getTopic() {
            return this.topic;
        }

        public CardMessage getCardMessage() {
            return this.message;
        }
    }




    public static class LocationMessageReceived extends E {
		private String t;
		private LocationMessage m;

		public LocationMessageReceived(LocationMessage m, String t) {
			super();
			this.t = t;
			this.m = m;
		}

		public String getTopic() {
			return this.t;
		}

		public LocationMessage getLocationMessage() {
			return this.m;
		}

		public GeocodableLocation getGeocodableLocation() {
			return this.m.getLocation();
		}
	}

    public static class WaypointMessageReceived extends E {
        private String t;
        private WaypointMessage m;

        public WaypointMessageReceived(WaypointMessage m, String t) {
            super();
            this.t = t;
            this.m = m;
        }

        public String getTopic() {
            return this.t;
        }

        public WaypointMessage getLocationMessage() {
            return this.m;
        }
    }

    public static class ConfigurationMessageReceived extends E {
        private String t;
        private ConfigurationMessage m;

        public ConfigurationMessageReceived(ConfigurationMessage m, String t) {
            super();
            this.t = t;
            this.m = m;
        }

        public String getTopic() {
            return this.t;
        }

        public ConfigurationMessage getConfigurationMessage() {
            return this.m;
        }
    }

    public static class TransitionMessageReceived extends E {
        private String t;
        private TransitionMessage m;

        public TransitionMessageReceived(TransitionMessage m, String t) {
            super();
            this.t = t;
            this.m = m;
        }

        public String getTopic() {
            return this.t;
        }

        public TransitionMessage getTransitionMessage() {
            return this.m;
        }
        public GeocodableLocation getGeocodableLocation() {
            return this.m.getLocation();
        }

        public int getTransition() {
            return this.m.getTransition();
        }

    }


    public static class ContactUpdated extends E {
		private Contact c;

		public ContactUpdated(Contact c) {
			super();
			this.c = c;
		}

		public Contact getContact() {
			return this.c;
		}

	}

    public static class MessageAdded extends E{
        private Message m;
        public MessageAdded(Message m) {
            this.m = m;
        }
        public Message getMessage(){
            return m;
        }
    }
	
	public static class BrokerChanged extends E {
		public BrokerChanged() {}
	}

	public static class StateChanged {
		public static class ServiceBroker extends E {
			private org.owntracks.android.services.ServiceBroker.State state;
			private Object extra;

			public ServiceBroker(org.owntracks.android.services.ServiceBroker.State state) {
				this(state, null);
			}

			public ServiceBroker(org.owntracks.android.services.ServiceBroker.State state,
					Object extra) {
				super();
				this.state = state;
				this.extra = extra;
			}

			public org.owntracks.android.services.ServiceBroker.State getState() {
				return this.state;
			}

			public Object getExtra() {
				return this.extra;
			}

		}


        public static class ServiceBeacon extends E {
            private org.owntracks.android.services.ServiceBeacon.State state;

            public ServiceBeacon(org.owntracks.android.services.ServiceBeacon.State state) {
                this.state = state;
            }

            public org.owntracks.android.services.ServiceBeacon.State getState() {
                return this.state;
            }

        }



    }



}
