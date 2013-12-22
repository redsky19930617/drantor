package st.alr.mqttitude.services;

import java.util.HashMap;

import st.alr.mqttitude.support.Defaults;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import de.greenrobot.event.EventBus;

public class ServiceProxy extends ServiceBindable {
    public static final String SERVICE_APP = "1:App";
    public static final String SERVICE_LOCATOR = "2:Loc";
    public static final String SERVICE_BROKER = "3:Brk";
    private static ServiceProxy instance;
    private static HashMap<String, ProxyableService> services = new HashMap<String, ProxyableService>();
    private static int intentCounter = 0;
    
    @Override
    public void onCreate(){
        super.onCreate();
    }
    
    @Override
    protected void onStartOnce() {        
        Log.v(this.toString(), "ServiceApplication starting");
        instantiateService(SERVICE_APP);
        instantiateService(SERVICE_BROKER);
        instantiateService(SERVICE_LOCATOR);
        instance = this;
    }
    
    
    
    public static ServiceProxy getInstance() {
        return instance;
    }

    @Override
    public void onDestroy(){
        for (ProxyableService p : services.values()) {
            EventBus.getDefault().unregister(p);
            p.onDestroy();
        }
        
        super.onDestroy();

    }
    



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int r = super.onStartCommand(intent, flags, startId);         // Invokes onStartOnce(...) the fist time to initialize the service         

        ProxyableService s = getServiceForIntent(intent);
        if(s != null)
            s.onStartCommand(intent, flags, startId);
    
        return r;        
    }
        
    public static ProxyableService getService(String id){
        return services.get(id);
    }
    
    private ProxyableService instantiateService(String id){
        ProxyableService p = services.get(id);
        if(p != null)
            return p;
        
                
        if(id.equals(SERVICE_APP))
            p = new ServiceApplication();
        else if(id.equals(SERVICE_BROKER))
            p = new ServiceBroker();
        else if(id.equals(SERVICE_LOCATOR))
            p = new ServiceLocatorFused();
             

        services.put(id, p);
        p.onCreate(this);
        EventBus.getDefault().registerSticky(p);

        return p;
    }

    public static ServiceApplication getServiceApplication() {
        return (ServiceApplication) getService(SERVICE_APP);
    }
    public static ServiceLocatorFused getServiceLocator() {
        return (ServiceLocatorFused) getService(SERVICE_LOCATOR);
    }
    public static ServiceBroker getServiceBroker() {
        return (ServiceBroker) getService(SERVICE_BROKER);
    }
    
    public static ProxyableService getServiceForIntent(Intent i) {
        if (i != null && i.getStringExtra("srvID") != null) {
            return getService(i.getStringExtra("srvID"));
        } else {
            return null;
        }
        
    }
    public static PendingIntent getPendingIntentForService(Context c, String targetServiceId, String action, Bundle extras) {
        Intent i = new Intent().setClass(c, ServiceProxy.class);
        i.setAction(action);

        if(extras != null)
            i.putExtras(extras); 
        i.putExtra("srvID", targetServiceId);
        
        return PendingIntent.getService(c, intentCounter++, i, PendingIntent.FLAG_UPDATE_CURRENT);                
        
    }
}
