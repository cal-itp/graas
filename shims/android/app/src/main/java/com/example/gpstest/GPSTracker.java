package com.example.gpstest;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

public class GPSTracker extends Service implements LocationListener {
    private final Context mContext;

    // flag for GPS status
    boolean isGPSEnabled = false;

    // flag for network status
    boolean isNetworkEnabled = false;

    // flag for GPS status
    boolean canGetLocation = false;

    Location location; // location

    private double latitude;
    private double longitude;
    private double heading;
    private double accuracy;
    private double speed;
    private long timestamp;

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1;

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1000 * 3;

    // Declaring a Location Manager
    protected LocationManager locationManager;

    public GPSTracker(Context context) {
        this.mContext = context;

        timestamp = System.currentTimeMillis() / 1000;
        getLocation();
    }

    @SuppressLint("MissingPermission")
    public Location getLocation() {
        try {
            locationManager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);

            // getting GPS status
            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            // getting network status
            isNetworkEnabled = locationManager
                    .isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isGPSEnabled && !isNetworkEnabled) {
                // no network provider is enabled
            } else {
                this.canGetLocation = true;
                // First get location from Network Provider
                if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    Log.d("Network", "Network");
                    if (locationManager != null) {
                        location = locationManager
                                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                        }
                    }
                }

                // if GPS Enabled get lat/long using GPS Services
                if (isGPSEnabled) {
                    if (location == null) {
                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                        Log.d("GPS Enabled", "GPS Enabled");
                        if (locationManager != null) {
                            location = locationManager
                                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);

                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return location;
    }

    public void stopUsingGPS(){
        if(locationManager != null){
            locationManager.removeUpdates(GPSTracker.this);
        }
    }

    public synchronized double getLatitude(){
        /*if(location != null){
            latitude = location.getLatitude();
        }*/

        // return latitude
        return latitude;
    }

    public synchronized double getLongitude(){
        /*if(location != null){
            longitude = location.getLongitude();
        }*/

        // return longitude
        return longitude;
    }

    public String getPositionString() {
        Map<String, Object> coords = new HashMap<String, Object>();

        coords.put("latitude", latitude);
        coords.put("longitude", longitude);
        coords.put("speed", speed);
        coords.put("heading", heading);
        coords.put("accuracy", accuracy);

        Map<String, Object> loc = new HashMap<String, Object>();
        loc.put("timestamp", timestamp);
        loc.put("coords", coords);

        return toJSONObject(loc);
    }

    public boolean canGetLocation() {
        return this.canGetLocation;
    }

    public void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);

        // Setting Dialog Title
        alertDialog.setTitle("GPS is settings");

        // Setting Dialog Message
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");

        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                mContext.startActivity(intent);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Showing Alert Message
        alertDialog.show();
    }

    /*
{"data":{"uuid":"4f15fc20-aa0a-01a7-db87-6b9cc124064d","agent":["(Linux; Android 7.1.1; SM-T350)"],"timestamp":1620080155,"lat":40.8067329,"long":-124.1478361,"speed":0.0013935526367276907,"heading":88.36840057373047,"accuracy":4.551000118255615,"version":"0.10 (04/27)","trip-id":"t_858256_b_26560_tn_0","agency-id":"humboldt-transit-authority","vehicle-id":"510","pos-timestamp":1620080148},"sig":"ojCxLYMYwarirmyty0rRTha99daEAaW/HxchLebX9dDpGiKc5EXUjAeNdoxzIACFxbnqWK03f39iZWXf4OJPsA=="}
 */
    private String toJSONObject(Map<String, Object> map) {
        StringBuffer sb = new StringBuffer();
        sb.append('{');

        for (String key : map.keySet()) {
            if (sb.length() > 1) {
                sb.append(',');
            }

            sb.append('"');
            sb.append(key);
            sb.append('"');
            sb.append(':');

            Object v = map.get(key);

            if (v instanceof Map) {
                sb.append(toJSONObject((Map<String, Object>)v));
            } else {
                if (v instanceof String) {
                    sb.append('"');
                }

                sb.append(v);

                if (v instanceof String) {
                    sb.append('"');
                }
            }
        }

        sb.append('}');
        return sb.toString();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createUpdate(Location location) {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("uuid", mContext.getString(R.string.uuid));
        map.put("agency-id", mContext.getString(R.string.agency_id));
        map.put("vehicle-id", "android-shim");
        map.put("trip-id", "tannhauser-gate-" + timestamp);
        map.put("agent", "android");
        map.put("version", "0.001");
        map.put("lat", location.getLatitude());
        map.put("long", location.getLongitude());
        map.put("speed", location.getSpeed());
        map.put("heading", location.getBearing());
        map.put("pos-timestamp", location.getTime());
        map.put("accuracy", location.getAccuracy());
        map.put("timestamp", timestamp);

        String payload = toJSONObject(map);
        Log.d("gpstest","- payload: " + payload);
        //Debug.log("- payload: " + payload);

        String pem = mContext.getString(R.string.pk);
        PrivateKey sk = CryptoUtil.importPrivateKeyFromPEM(pem);

        map.clear();
        map.put("data", new StringBuffer(payload));
        map.put("sig", CryptoUtil.sign(payload, sk, true));

        String msg = toJSONObject(map);
        Log.d("gpstest","- msg: " + msg);
        //Debug.log("- msg: " + msg);

        Map<String, String> headers =  new HashMap<String, String>();
        headers.put("Content-Type", "application/json");

        Util.post(
            mContext.getString(R.string.base_url) + "new-pos-sig",
            msg,
            headers
        );
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("DefaultLocale")
    @Override
    public void onLocationChanged(Location location) {
        /*Debug.log(String.format(
                "%f,%f",
                location.getLatitude(),
                location.getLongitude()
        ));

        createUpdate(location);*/

        Log.d("gpstest", "onLocationChanged()");

        synchronized (this) {
            latitude = location.getLatitude();
            Log.d("gpstest", "- latitude: " + latitude);
            longitude = location.getLongitude();
            Log.d("gpstest", "- longitude: " + longitude);

            heading = location.getBearing();
            accuracy = location.getAccuracy();
            timestamp = location.getTime();
            speed = location.getSpeed();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
}