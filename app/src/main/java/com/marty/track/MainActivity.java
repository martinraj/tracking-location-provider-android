package com.marty.track;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.app.Activity;
import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Property;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;


public class MainActivity extends AppCompatActivity {
    FusedLocationProviderClient mFusedLocationProviderClient;
    protected static final int REQUEST_CHECK_SETTINGS = 0x1;
    LocationRequest mLocationRequest;
    LocationCallback mLocationCallback;
    Button button,bService;
    SupportMapFragment mapFragment;
    GoogleMap mMap;
    boolean mapLoaded = false;
    Marker carMarker;
    static MoveThread moveThread;
    static Handler handler;
    Location oldLocation;
    float bearing;
    boolean registered = false, isServiceStarted=false;
    public static final String JOB_STATE_CHANGED = "jobStateChanged";
    public static final String LOCATION_ACQUIRED = "locAcquired";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        button = (Button) findViewById(R.id.b_action);
        bService = (Button) findViewById(R.id.b_service);
        mapFragment = SupportMapFragment.newInstance();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(R.id.map_fragment, mapFragment).commitAllowingStateLoss();
        handler = new Handler();
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
                CameraUpdate point = CameraUpdateFactory.newLatLngZoom(new LatLng(13.0827,80.2707),8);
                mMap.moveCamera(point);
                mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                    @Override
                    public void onMapLoaded() {
                        mapLoaded = true;
                        mMap.getUiSettings().setAllGesturesEnabled(true);
                        mMap.getUiSettings().setZoomControlsEnabled(true);
                    }
                });
            }
        });
        isServiceStarted = getSharedPreferences("track",MODE_PRIVATE).getBoolean("isServiceStarted",false);
        changeServiceButton(isServiceStarted);
        if(!registered&&isServiceStarted) {
            IntentFilter i = new IntentFilter(JOB_STATE_CHANGED);
            i.addAction(LOCATION_ACQUIRED);
            LocalBroadcastManager.getInstance(this).registerReceiver(jobStateChanged, i);
        }

    }

    public void OnButtonClick(View view) {
        switch (view.getId()) {
            case R.id.b_action:
                if (view.getTag().equals("s")) {
                    createLocationRequest();
                } else {
                    Log.d("clicked", "button");
                    stopLocationUpdates();
                }
                break;
            case R.id.b_service:
                if(view.getTag().equals("s")){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Log.d("registered"," on start service");
                        startBackgroundService();
                    }else{
                        Toast.makeText(getBaseContext(),"service for pre lollipop will be available in next update",Toast.LENGTH_LONG).show();
                    }
                }else{
                    stopBackgroundService();
                }
                break;
        }
    }

    private BroadcastReceiver jobStateChanged = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction()==null){
                return;
            }
            if(intent.getAction().equals(JOB_STATE_CHANGED)) {
                changeServiceButton(intent.getExtras().getBoolean("isStarted"));
            }else if (intent.getAction().equals(LOCATION_ACQUIRED)){
                if(intent.getExtras()!=null){
                    Bundle b = intent.getExtras();
                    Location l = b.getParcelable("location");
                    updateMarker(l);
                }else{
                    Log.d("intent","null");
                }
            }
        }
    };

    private void changeServiceButton(boolean isStarted) {
        if(isStarted){
            bService.setTag("f");
            bService.setText("STOP BACKGROUND TRACKING");
            button.setVisibility(View.GONE);
        }else{
            bService.setTag("s");
            bService.setText("START BACKGROUND TRACKING");
            button.setVisibility(View.VISIBLE);
        }
    }

    private void stopBackgroundService() {
        if(getSharedPreferences("track",MODE_PRIVATE).getBoolean("isServiceStarted",false)){
            Log.d("registered"," on stop service");
            Intent stopJobService = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                stopJobService = new Intent(LocationJobService.ACTION_STOP_JOB);
                LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(stopJobService);
            }else{
                Toast.makeText(getApplicationContext(),"yet to be coded - stop service",Toast.LENGTH_SHORT).show();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startBackgroundService() {
        if(!registered) {
            IntentFilter i = new IntentFilter(JOB_STATE_CHANGED);
            i.addAction(LOCATION_ACQUIRED);
            LocalBroadcastManager.getInstance(this).registerReceiver(jobStateChanged, i);
        }
        JobScheduler jobScheduler =
                (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assert jobScheduler != null;
        jobScheduler.schedule(new JobInfo.Builder(LocationJobService.LOCATION_SERVICE_JOB_ID,
                new ComponentName(this, LocationJobService.class))
                .setOverrideDeadline(500)
                .setPersisted(true)
                .setRequiresDeviceIdle(false)
                .build());
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(30000);
        mLocationRequest.setFastestInterval(15000);
        mLocationRequest.setSmallestDisplacement(50);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                // All location settings are satisfied. The client can initialize
                // location requests here.
                // ...
                bService.setVisibility(View.GONE);
                startLocationUpdates();
            }
        });
        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                int statusCode = ((ApiException) e).getStatusCode();
                switch (statusCode) {
                    case CommonStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            resolvable.startResolutionForResult(MainActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException sendEx) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });
    }

    private void startLocationUpdates() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // Update UI with location data
                    // ...
                    updateMarker(location);
                }
            }

            ;
        };
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Toast.makeText(getApplicationContext(),"location permission required !!",Toast.LENGTH_SHORT).show();
            return;
        }
        mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                null /* Looper */);
        button.setTag("f");
        button.setText("STOP FOREGROUND TRACKING");
//        Toast.makeText(getApplicationContext(),"Location update started",Toast.LENGTH_SHORT).show();
    }
    private void stopLocationUpdates() {
        if (button.getTag().equals("s")) {
            Log.d("TRACK", "stopLocationUpdates: updates never requested, no-op.");
            return;
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
        button.setTag("s");
        button.setText("START FOREGROUND TRACKING");
        bService.setVisibility(View.VISIBLE);
//        Toast.makeText(getApplicationContext(),"Location update stopped.",Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        try {
            if (registered) {
                unregisterReceiver(jobStateChanged);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if( requestCode == REQUEST_CHECK_SETTINGS) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    Log.i("Dash", "User agreed to make required location settings changes.");
                    createLocationRequest();
                    break;
                case Activity.RESULT_CANCELED:
//                    showTimeoutDialog("Without location access, GreenPool Enterprise can't be used !!", true);
                    Log.i("Dash", "User choose not to make required location settings changes.");
                    break;
            }
        }else{
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    static void animateMarkerToICS(Marker marker, LatLng finalPosition) {
        TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
            @Override
            public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
                return interpolate(fraction, startValue, endValue);
            }
        };
        Property<Marker, LatLng> property = Property.of(Marker.class, LatLng.class, "position");
        ObjectAnimator animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, finalPosition);
        animator.setDuration(3000);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                handler.post(moveThread);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        animator.start();
    }

    public static LatLng interpolate(float fraction, LatLng a, LatLng b) {
        // function to calculate the in between values of old latlng and new latlng.
        // To get more accurate tracking(Car will always be in the road even when the latlng falls away from road), use roads api from Google apis.
        // As it has quota limits I didn't have used that method.
        double lat = (b.latitude - a.latitude) * fraction + a.latitude;
        double lngDelta = b.longitude - a.longitude;

        // Take the shortest path across the 180th meridian.
        if (Math.abs(lngDelta) > 180) {
            lngDelta -= Math.signum(lngDelta) * 360;
        }
        double lng = lngDelta * fraction + a.longitude;
        return new LatLng(lat, lng);
    }

    private class MoveThread implements Runnable {
        LatLng newPoint;
        float zoom = 16;

        void setNewPoint(LatLng latLng, float zoom){
            this.newPoint = latLng;
            this.zoom = zoom;
        }

        @Override
        public void run() {
            final CameraUpdate point = CameraUpdateFactory.newLatLngZoom(newPoint,zoom);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMap.animateCamera(point);
                }
            });
        }
    }

    private void updateMarker(Location location) {
        if(location == null){
            return;
        }
        if(mMap!=null && mapLoaded){
            if(carMarker==null) {
                oldLocation = location;
                MarkerOptions markerOptions = new MarkerOptions();
                BitmapDescriptor car = BitmapDescriptorFactory.fromResource(R.mipmap.ic_car);
                markerOptions.icon(car);
                markerOptions.anchor(0.5f, 0.5f); // set the car image to center of the point instead of anchoring to above or below the location
                markerOptions.flat(true); // set as true, so that when user rotates the map car icon will remain in the same direction
                markerOptions.position(new LatLng(location.getLatitude(),location.getLongitude()));
                carMarker = mMap.addMarker(markerOptions);
                if(location.hasBearing()){ // if location has bearing set the same bearing to marker(if location is acquired using GPS bearing will be available)
                    bearing = location.getBearing();
                }else{
                    bearing = 0; // no need to calculate bearing as it will be the first point
                }
                carMarker.setRotation(bearing);
                moveThread  = new MoveThread();
                moveThread.setNewPoint(new LatLng(location.getLatitude(),location.getLongitude()),16);
                handler.post(moveThread);

            }else{
                if(location.hasBearing()){// if location has bearing set the same bearing to marker(if location is acquired using GPS bearing will be available)
                    bearing = location.getBearing();
                }else { // if not, calculate bearing between old location and new location point
                    bearing = oldLocation.bearingTo(location);
                }
                carMarker.setRotation(bearing);
                moveThread.setNewPoint(new LatLng(location.getLatitude(),location.getLongitude()),mMap.getCameraPosition().zoom); // set the map zoom to current map's zoom level as user may zoom the map while tracking.
                animateMarkerToICS(carMarker,new LatLng(location.getLatitude(),location.getLongitude())); // animate the marker smoothly
            }
        }else{
            Log.e("map null or not loaded","");
        }
    }
}
