package com.marty.track;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;

import static com.marty.track.MainActivity.JOB_STATE_CHANGED;
import static com.marty.track.MainActivity.LOCATION_ACQUIRED;

/**
 * Created by Marty on 11/25/2017.
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class LocationJobService extends JobService {

    Handler handler;
    ConnectionDetector cd;
    LocationThread th;
    FusedLocationProviderClient mFusedLocationProviderClient;
    public static final int LOCATION_SERVICE_JOB_ID = 111;
    LocationRequest mLocationRequest;
    LocationCallback mLocationCallback;
    JobParameters jobParameters;
    ArrayList<Location> updatesList = new ArrayList<>();

    public static final String ACTION_STOP_JOB = "actionStopJob";

    private BroadcastReceiver stopJobReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction()!=null && intent.getAction().equals(ACTION_STOP_JOB)) {
                Log.d("unregister"," job stop receiver");
                try {
                    unregisterReceiver(this); //Unregister receiver to avoid receiver leaks exception
                }catch (Exception e){
                    e.printStackTrace();
                }
                onJobFinished();
            }
        }
    };

    private void onJobFinished() {
        Log.d("job finish"," called");
        stopLocationUpdates();
        jobFinished(jobParameters,false);
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        handler = new Handler();
        this.jobParameters = jobParameters;
        th = new LocationThread();
        handler.post(th);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d("job","stopped");
        if(th!=null){
            handler.removeCallbacks(th);
        }
        stopLocationUpdates();
        /*try {
            LocalBroadcastManager.getInstance(getBaseContext()).unregisterReceiver(stopJobReceiver);
        }catch (Exception e){
            e.printStackTrace();
        }*/
        return true;
    }

    private class LocationThread implements Runnable{
        @Override
        public void run() {
            mLocationRequest = new LocationRequest();
            mLocationRequest.setInterval(10000);
            mLocationRequest.setFastestInterval(5000);
            mLocationRequest.setSmallestDisplacement(10);
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            cd = new ConnectionDetector(getApplicationContext());
            startLocationUpdates();
            LocalBroadcastManager.getInstance(LocationJobService.this).registerReceiver(stopJobReceiver , new IntentFilter(ACTION_STOP_JOB));
        }

        private void startLocationUpdates() {
            mLocationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    for (Location location : locationResult.getLocations()) {
                        // Update UI with location data
                        // ...
//                        Toast.makeText(getBaseContext(),"new point",Toast.LENGTH_SHORT).show();
                        Intent i = new Intent(LOCATION_ACQUIRED);
                        i.putExtra("location",location);

                        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(i);

                        if(cd.isConnectingToInternet()) { // check whether internet is available or not
                            updatesList.add(location); //if available add latest location point and send list to server
                            Intent i1 = new Intent(LocationJobService.this, UploadLocationService.class);
                            i1.putParcelableArrayListExtra("points", updatesList);
//                            startService(i1); //i have disabled the call as the server URL in intent service is dummy URL. Change the URL to your server URL and call this intent service
                            updatesList.clear();
                        }else{ // if there is no internet connection
                            updatesList.add(location); // add location points to the list
                        }
                    }
                }

                ;
            };
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Toast.makeText(getApplicationContext(),"permission required !!",Toast.LENGTH_SHORT).show();
                return;
            }
            mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(LocationJobService.this);
            mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback,
                    null /* Looper */);
            getSharedPreferences("track",MODE_PRIVATE).edit().putBoolean("isServiceStarted",true).apply();
            Intent jobStartedMessage = new Intent(JOB_STATE_CHANGED);
            jobStartedMessage.putExtra("isStarted",true);
            Log.d("send broadcast"," as job started");
            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(jobStartedMessage);
            createNotification();
            Toast.makeText(getApplicationContext(),"Location job service started",Toast.LENGTH_SHORT).show();
        }
    }

    private void createNotification() {
        Notification.Builder mBuilder = new Notification.Builder(
                getBaseContext());
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = mBuilder.setSmallIcon((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)? R.drawable.ic_trans_notifi:R.mipmap.ic_launcher).setTicker("Tracking").setWhen(0)
                    .setAutoCancel(false)
                    .setCategory(Notification.EXTRA_BIG_TEXT)
                    .setContentTitle("Tracking")
                    .setContentText("Your trip in progress")
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setColor(ContextCompat.getColor(getBaseContext(),R.color.colorPrimaryDark))
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("Your trip in progress"))
                    .setChannelId("track_marty")
                    .setShowWhen(true)
                    .setOngoing(true)
                    .build();
        }else{
            notification = mBuilder.setSmallIcon((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)? R.drawable.ic_trans_notifi:R.mipmap.ic_launcher).setTicker("Tracking").setWhen(0)
                    .setAutoCancel(false)
                    .setCategory(Notification.EXTRA_BIG_TEXT)
                    .setContentTitle("Tracking")
                    .setContentText("Your trip in progress")
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setColor(ContextCompat.getColor(getBaseContext(),R.color.colorPrimaryDark))
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("Your trip in progress"))
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setShowWhen(true)
                    .setOngoing(true)
                    .build();
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel("track_marty", "Track", NotificationManager.IMPORTANCE_HIGH);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(mChannel);
        }
        assert notificationManager != null;
        notificationManager.notify(0, notification);
    }

    private void removeNotification(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.cancel(0);
    }

    private void stopLocationUpdates() {

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        Log.d("stop location "," updates called");
        if(mLocationCallback!=null && mFusedLocationProviderClient!=null) {
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
            Toast.makeText(getApplicationContext(), "Location job service stopped.", Toast.LENGTH_SHORT).show();
        }
        getSharedPreferences("track",MODE_PRIVATE).edit().putBoolean("isServiceStarted",false).apply();
        Intent jobStoppedMessage = new Intent(JOB_STATE_CHANGED);
        jobStoppedMessage.putExtra("isStarted",false);
        Log.d("broadcasted","job state change");
        removeNotification();
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(jobStoppedMessage);
    }
}
