
package st.alr.mqttitude;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import st.alr.mqttitude.preferences.ActivityPreferences;
import st.alr.mqttitude.services.ServiceLocator;
import st.alr.mqttitude.services.ServiceMqtt;
import st.alr.mqttitude.services.ServiceLocator.LocatorBinder;
import st.alr.mqttitude.support.Events;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import de.greenrobot.event.EventBus;

public class ActivityMain extends android.support.v4.app.FragmentActivity {
    private static final int GEOCODER_RESULT = 1;
    MenuItem publish;
    TextView location;
    TextView statusLocator;
    TextView statusLastupdate;
    TextView statusServer;
    private GoogleMap mMap;

    private TextView locationPrimary;
    private TextView locationMeta;
    private LinearLayout locationAvailable;
    private LinearLayout locationUnavailable;

    private Marker mMarker;
    private Circle mCircle;
    private ServiceLocator serviceLocator;
    private ServiceConnection locatorConnection;
    private static Handler handler;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        Intent i = null;
        
        if (itemId == R.id.menu_settings) {
            i = new Intent(this, ActivityPreferences.class);
            startActivity(i);
            return true;
        }  else if (itemId == R.id.menu_status) {
                i = new Intent(this, ActivityStatus.class);
                startActivity(i);
                return true;
        } else if (itemId == R.id.menu_publish) {           
            if(serviceLocator != null)
                serviceLocator.publishLastKnownLocation();
            return true;
        } else if (itemId == R.id.menu_share) {
            this.share(null);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((com.google.android.gms.maps.SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map)).getMap();
            if (mMap != null)
                setUpMap();
        }
    }

    private void setUpMap() {
        // Hide the zoom controls as the button panel will cover it.
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.setMyLocationEnabled(false);
        mMap.setTrafficEnabled(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        
        Log.v(this.toString(), "binding");

        
        locatorConnection = new ServiceConnection() {
            
            @Override
            public void onServiceDisconnected(ComponentName name) {
                serviceLocator = null;                
            }
            
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.v(this.toString(), "bound");

                serviceLocator = ((ServiceLocator.LocatorBinder)service).getService();
                
            }
        };
        
        bindService(new Intent(this, App.getServiceLocatorClass()), locatorConnection, Context.BIND_AUTO_CREATE);
        EventBus.getDefault().register(this);
    }
    
    @Override
    public void onStop() {
        unbindService(locatorConnection);
        EventBus.getDefault().unregister(this);
        super.onStop();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        if(serviceLocator != null)
            serviceLocator.enableForegroundMode();
    }

    @Override
    protected void onPause() {
        if(serviceLocator != null)
            serviceLocator.enableBackgroundMode();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
    
        if (App.getInstance().isDebugBuild())
                menu.findItem(R.id.menu_status).setVisible(true);
        
        return true;
    }

    /**
     * @category START
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setUpMapIfNeeded();

        serviceLocator = null;
        locationAvailable = (LinearLayout) findViewById(R.id.locationAvailable);
        locationUnavailable = (LinearLayout) findViewById(R.id.locationUnavailable);
        locationPrimary = (TextView) findViewById(R.id.locationPrimary);
        locationMeta = (TextView) findViewById(R.id.locationMeta);
        // Handler for updating text fields on the UI like the lat/long and address.
        handler = new Handler() {
            public void handleMessage(Message msg) {
                onHandlerMessage(msg);
            }
        };

        showLocationUnavailable();        
    }
    
    private void onHandlerMessage(Message msg) {
        switch (msg.what) {
            case GEOCODER_RESULT:
                locationPrimary.setText((String) msg.obj);
                break;
        }
    }   

    public void onEvent(Events.LocationUpdated e) {
        setLocation(e.getLocation());
    }

    public void setLocation(Location l) {
       if(l == null) {
           showLocationUnavailable();
           return;
       } 
       
        LatLng latlong = new LatLng(l.getLatitude(), l.getLongitude());
        CameraUpdate center = CameraUpdateFactory.newLatLng(latlong);
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(15);

        if (mMarker != null)
            mMarker.remove();

        if (mCircle != null)
            mCircle.remove();

        
        mMarker = mMap.addMarker(new MarkerOptions().position(latlong).icon(BitmapDescriptorFactory.fromResource(R.drawable.marker)));

         if(l.getAccuracy() >= 50) {
                 mCircle = mMap.addCircle(new
                 CircleOptions().center(latlong).radius(l.getAccuracy()).strokeColor(0xff1082ac).fillColor(0x1c15bffe).strokeWidth(3));
         }

        mMap.moveCamera(center);
        mMap.animateCamera(zoom);

        locationPrimary.setText(l.getLatitude() + " / " + l.getLongitude());
        locationMeta.setText(App.getInstance().formatDate(new Date()));
        showLocationAvailable();
        
        if (Geocoder.isPresent())
            (new ReverseGeocodingTask(this)).execute(new Location[] {l});
        
    }
    // AsyncTask encapsulating the reverse-geocoding API.  Since the geocoder API is blocked,
    // we do not want to invoke it from the UI thread.
    private class ReverseGeocodingTask extends AsyncTask<Location, Void, Void> {
        Context mContext;

        public ReverseGeocodingTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Location... params) {
            Log.v(this.toString(), "Doing reverse Geocoder lookup of location");
            Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
            Location l = params[0];

            try {
                List<Address> addresses = geocoder.getFromLocation(l.getLatitude(), l.getLongitude(), 1);
                if (addresses != null && addresses.size() > 0) {            
                    Address a = addresses.get(0);
                    Message.obtain(handler, GEOCODER_RESULT, a.getAddressLine(0)).sendToTarget();
                }
            } catch (IOException e) {
                // Geocoder information not available. LatLong is already shown and just not overwritten. Nothing to do here
            }

//            
//            Location loc = params[0];
//            List<Address> addresses = null;
//            try {
//                addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
//            } catch (IOException e) {
//                e.printStackTrace();
//                // Update address field with the exception.
//                Message.obtain(mHandler, UPDATE_ADDRESS, e.toString()).sendToTarget();
//            }
//            if (addresses != null && addresses.size() > 0) {
//                Address address = addresses.get(0);
//                // Format the first line of address (if available), city, and country name.
//                String addressText = String.format("%s, %s, %s",
//                        address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "",
//                        address.getLocality(),
//                        address.getCountryName());
//                // Update address field on UI.
//                Message.obtain(mHandler, UPDATE_ADDRESS, addressText).sendToTarget();
//            }
            return null;
        }
    }



    private void showLocationAvailable() {
        locationUnavailable.setVisibility(View.GONE);
        if(!locationAvailable.isShown())
            locationAvailable.setVisibility(View.VISIBLE);
    }

    private void showLocationUnavailable(){
        locationAvailable.setVisibility(View.GONE);
        if(!locationUnavailable.isShown())          
            locationUnavailable.setVisibility(View.VISIBLE);        
    }
    
    public void share(View view) {
        if(serviceLocator != null)
            return;
        
        Location l = serviceLocator.getLastKnownLocation();
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(
                Intent.EXTRA_TEXT,
                "http://maps.google.com/?q=" + Double.toString(l.getLatitude()) + ","
                        + Double.toString(l.getLongitude()));
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent,
                getResources().getText(R.string.shareLocation)));

    }

    public void upload(View view) {
        if(serviceLocator != null)
            serviceLocator.publishLastKnownLocation();
    }
}
