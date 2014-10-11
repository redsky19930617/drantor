package st.alr.mqttitude;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;

import de.greenrobot.event.EventBus;
import st.alr.mqttitude.adapter.WaypointAdapter;
import st.alr.mqttitude.db.Waypoint;
import st.alr.mqttitude.db.WaypointDao;
import st.alr.mqttitude.model.Contact;
import st.alr.mqttitude.model.GeocodableLocation;
import st.alr.mqttitude.services.ServiceProxy;
import st.alr.mqttitude.support.Defaults;
import st.alr.mqttitude.support.Events;
import st.alr.mqttitude.support.ReverseGeocodingTask;
import st.alr.mqttitude.support.StaticHandler;
import st.alr.mqttitude.support.StaticHandlerInterface;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;

public class ActivityWaypoints extends FragmentActivity implements StaticHandlerInterface {
    private static final int MENU_WAYPOINT_REMOVE = 0;
    private ListView listView;
	private WaypointAdapter listAdapter;
    private WaypointDao dao;
    private GeocodableLocation currentLocation;
    private static final String BUNDLE_KEY_POSITION = "position";
    private Handler handler;
    private TextView waypointListPlaceholder;

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startService(new Intent(this, ServiceProxy.class));
		ServiceProxy.runOrBind(this, new Runnable() {
			@Override
			public void run() {
				Log.v("ActivityWaypoints", "ServiceProxy bound");
			}
		});

		setContentView(R.layout.activity_waypoint);

        this.dao = App.getWaypointDao();
        this.handler = new StaticHandler(this);

		this.listAdapter = new WaypointAdapter(this, new ArrayList<Waypoint>(dao.loadAll()));

		this.listView = (ListView) findViewById(R.id.waypoints);
		this.listView.setAdapter(this.listAdapter);

        this.waypointListPlaceholder = (TextView) findViewById(R.id.waypointListPlaceholder);
		this.listView.setEmptyView(waypointListPlaceholder);
        this.listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				LocalWaypointDialog localWaypointDialog = LocalWaypointDialog.newInstance(position);
				getFragmentManager().beginTransaction().add(localWaypointDialog, "localWaypointDialog").commit();

			}
		});
		if (this.listAdapter.getCount() == 0)
			this.listView.setVisibility(View.GONE);

	}


    private void requestWaypointGeocoder(Waypoint w, boolean force){
        if(w.getGeocoder() == null || force) {

            GeocodableLocation l = new GeocodableLocation("Waypoint");
            l.setLatitude(w.getLatitude());
            l.setLongitude(w.getLongitude());
            l.setExtra(w);
            (new ReverseGeocodingTask(this, handler)).execute(l);
        }


    }

    @Override
    public void handleHandlerMessage(Message msg) {
        if ((msg.what == ReverseGeocodingTask.GEOCODER_RESULT) && ((GeocodableLocation)msg.obj).getExtra() instanceof  Waypoint) {
            Log.v("handler", "result");

            // Gets the geocoder from the returned array of [Geocoder, Waypoint] and assigns the geocoder to the waypoint
            Waypoint w = (Waypoint) ((GeocodableLocation)msg.obj).getExtra();
            w.setGeocoder(((GeocodableLocation)msg.obj).getGeocoder());
            this.dao.update(w);
            this.listAdapter.updateItem(w);
         }
    }


    @Override
	public void onDestroy() {
		ServiceProxy.runOrBind(this, new Runnable() {

            @Override
            public void run() {
                ServiceProxy.closeServiceConnection();

            }
        });
		super.onDestroy();
	}

	protected void add(Waypoint w) {
        this.dao.insert(w);
        this.listAdapter.addItem(w);
        requestWaypointGeocoder(w, false);
        EventBus.getDefault().post(new Events.WaypointAdded(w));

		if (this.listView.getVisibility() == View.GONE)
			this.listView.setVisibility(View.VISIBLE);

	}

    protected void update(Waypoint w) {
        this.dao.update(w);
        this.listAdapter.updateItem(w);
        EventBus.getDefault().post(new Events.WaypointUpdated(w));
        requestWaypointGeocoder(w, true);
    }


	protected void remove(Waypoint w) {
        this.listAdapter.removeItem(w);
        this.dao.delete(w);
        EventBus.getDefault().post(new Events.WaypointRemoved(w));
	}

	public WaypointAdapter getListAdapter() {
		return this.listAdapter;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_waypoint, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.add:
			LocalWaypointDialog LocalWaypointDialog = new LocalWaypointDialog();
			getFragmentManager().beginTransaction().add(LocalWaypointDialog, "LocalWaypointDialog").commit();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

    @Override
    public void onResume() {
        super.onResume();
        registerForContextMenu(this.listView);
    }

    @Override
    public void onPause() {
        unregisterForContextMenu(this.listView);
        super.onPause();

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, android.view.ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId()==R.id.waypoints) {
            menu.add(Menu.NONE, MENU_WAYPOINT_REMOVE, 1,getString(R.string.waypointRemove));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case MENU_WAYPOINT_REMOVE:
                remove( (Waypoint) listAdapter.getItem(info.position));
                break;
        }
        return true;
    }


    public static class LocalWaypointDialog extends DialogFragment implements StaticHandlerInterface {
        private TextView description;
        private TextView latitude;
        private TextView longitude;
        private TextView radius;
        private Spinner transitionType;
        private TextView currentLocationText;
        private LinearLayout currentLocationWrapper;
        private LinearLayout waypointGeofenceSettings;

        private static Handler handler;


        CheckBox share;
        GeocodableLocation location;

        Waypoint w;

        private void show(Waypoint w) {
			this.description.setText(w.getDescription());
			this.latitude.setText(w.getLatitude().toString());
			this.longitude.setText(w.getLongitude().toString());
			if (w.getRadius() != null)
				this.radius.setText(w.getRadius().toString());
            this.share.setChecked(w.getShared());

            setWaypointGeofenceSettingsVisibility(w.getRadius() != null);


			Log.v(this.toString(),
					"w.getTransitionType() " + w.getTransitionType());
			switch (w.getTransitionType()) {
			case Geofence.GEOFENCE_TRANSITION_ENTER:
				this.transitionType.setSelection(0);
				break;
			case Geofence.GEOFENCE_TRANSITION_EXIT:
				this.transitionType.setSelection(1);
				break;
			default:
				this.transitionType.setSelection(2);
				break;
			}

		}

        @Override
        public void onStart() {
            super.onStart();
            EventBus.getDefault().registerSticky(this);
        }

        @Override
        public void onStop() {

            EventBus.getDefault().unregister(this);
            super.onStop();
        }

        public void onEventMainThread(Events.CurrentLocationUpdated e) {
            updateCurrentLocation(e.getGeocodableLocation(), true);
        }

        private void updateCurrentLocation(GeocodableLocation l, boolean updateGeocoderIfNotResolved){
            if(updateGeocoderIfNotResolved && l!= null && l.getGeocoder() == null) {
                (new ReverseGeocodingTask(getActivity(), handler)).execute(l);
            }

            ((ActivityWaypoints)getActivity()).currentLocation = l;
            currentLocationText.setText(l.toString());

        }

        @Override
        public void handleHandlerMessage(Message msg) {

            if ((msg.what == ReverseGeocodingTask.GEOCODER_RESULT) && (msg.obj != null))
                updateCurrentLocation((GeocodableLocation)msg.obj, false);
        }


        private void setWaypointGeofenceSettingsVisibility(boolean visible) {
            this.waypointGeofenceSettings.setVisibility(visible ? View.VISIBLE : View.GONE);

        }

		private View getContentView() {
			View view = getActivity().getLayoutInflater().inflate(
					R.layout.fragment_waypoint, null);

			this.description = (TextView) view.findViewById(R.id.description);
			this.latitude = (TextView) view.findViewById(R.id.latitude);
			this.longitude = (TextView) view.findViewById(R.id.longitude);
			this.radius = (TextView) view.findViewById(R.id.radius);
			this.transitionType = (Spinner) view
					.findViewById(R.id.transitionType);
            this.currentLocationWrapper = (LinearLayout) view.findViewById(R.id.currentLocationWrapper);
            this.currentLocationText = (TextView) view.findViewById(R.id.currentLocation);
            this.waypointGeofenceSettings = (LinearLayout) view.findViewById(R.id.waypointGeofenceSettings);
            this.share = (CheckBox) view.findViewById(R.id.share);

			if (this.w != null)
				show(this.w);

			TextWatcher requiredForSave = new TextWatcher() {
				@Override
				public void onTextChanged(CharSequence s, int start,
						int before, int count) {
				}

				@Override
				public void beforeTextChanged(CharSequence s, int start,
						int count, int after) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					conditionallyEnableSaveButton();
				}
			};

            TextWatcher requiredForGeofence = new TextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start,  int before, int count) {
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start,  int count, int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    setWaypointGeofenceSettingsVisibility(radius.getText().length() > 0);
                }
            };

            this.description.addTextChangedListener(requiredForSave);
			this.latitude.addTextChangedListener(requiredForSave);
			this.longitude.addTextChangedListener(requiredForSave);
            this.radius.addTextChangedListener(requiredForGeofence);

			this.currentLocationWrapper.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
                    if (((ActivityWaypoints)getActivity()).currentLocation != null) {
                        LocalWaypointDialog.this.latitude.setText("" + ((ActivityWaypoints)getActivity()).currentLocation.getLatitude());
                        LocalWaypointDialog.this.longitude.setText("" + ((ActivityWaypoints)getActivity()).currentLocation.getLongitude());
                    } else {
                        Toast.makeText(getActivity(), "No current location is available", Toast.LENGTH_SHORT).show();
                    }
                }
            });


			return view;
		}

        public static LocalWaypointDialog newInstance(int position) {
			LocalWaypointDialog f = new LocalWaypointDialog();
			Bundle args = new Bundle();
			args.putInt(BUNDLE_KEY_POSITION, position);
			f.setArguments(args);
			return f;

		}

		private void conditionallyEnableSaveButton() {
			View v = getDialog().findViewById(android.R.id.button1);

			if (v == null)
				return;

			if ((this.description.getText().toString().length() > 0)
					&& (this.latitude.getText().toString().length() > 0)
					&& (this.longitude.getText().toString().length() > 0))
				v.setEnabled(true);
			else
				v.setEnabled(false);
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {

			Bundle b;
			if (savedInstanceState != null)
				b = savedInstanceState;
			else
				b = getArguments();

			if (b != null)
				this.w = (Waypoint)((ActivityWaypoints) getActivity()).getListAdapter().getItem(b.getInt(BUNDLE_KEY_POSITION));

            handler = new StaticHandler(this);


            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
					.setTitle(getResources().getString( this.w == null ? R.string.waypointAdd : R.string.waypointEdit))
					.setView(getContentView())
					.setNegativeButton(getResources().getString(R.string.cancel),new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {dismiss();	}
							})
					.setPositiveButton(getResources().getString(R.string.save),new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int which) {
                            boolean update;
                            if (LocalWaypointDialog.this.w == null) {
                                LocalWaypointDialog.this.w = new Waypoint();
                                update = false;
                            } else {
                                update = true;
                            }
                            LocalWaypointDialog.this.w.setDescription(LocalWaypointDialog.this.description.getText().toString());
                            try {
                                LocalWaypointDialog.this.w.setLatitude(Double.parseDouble(LocalWaypointDialog.this.latitude.getText().toString()));
                                LocalWaypointDialog.this.w.setLongitude(Double.parseDouble(LocalWaypointDialog.this.longitude.getText().toString()));
                            } catch (NumberFormatException e) {
                            }

                            try {
                                LocalWaypointDialog.this.w.setRadius(Float.parseFloat(LocalWaypointDialog.this.radius.getText().toString()));
                            } catch (NumberFormatException e) {
                                LocalWaypointDialog.this.w.setRadius(null);
                            }

                            LocalWaypointDialog.this.w.setShared(LocalWaypointDialog.this.share.isChecked());

                            switch (LocalWaypointDialog.this.transitionType.getSelectedItemPosition()) {
                                case 0:
                                    LocalWaypointDialog.this.w.setTransitionType(Geofence.GEOFENCE_TRANSITION_ENTER);
                                    break;
                                case 1:
                                    LocalWaypointDialog.this.w.setTransitionType(Geofence.GEOFENCE_TRANSITION_EXIT);
                                    break;
                                default:
                                    LocalWaypointDialog.this.w.setTransitionType(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT);
                                    break;
                            }


                            if (update)
                                ((ActivityWaypoints) getActivity()).update(LocalWaypointDialog.this.w);
                            else {
                                LocalWaypointDialog.this.w.setDate(new Date());
                                ((ActivityWaypoints) getActivity()).add(LocalWaypointDialog.this.w);
                            }

                            dismiss();
                        }
                    });

			Dialog dialog = builder.create();
			dialog.setOnShowListener(new OnShowListener() {

				@Override
				public void onShow(DialogInterface dialog) {
					conditionallyEnableSaveButton();

				}
			});
			return dialog;
		}

	}
}
