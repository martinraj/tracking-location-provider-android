# tracking-location-provider-android

In this demo, I have covered both foreground tracking and background tracking.

## Foreground Tracking

Foreground Tracking will work **only if app is in foreground.** To start foreground tracking, tap on the "START FOREGROUND TRACKING" button. Before requesting for location updates, check location settings of the device as below.


```
mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(30000);
        mLocationRequest.setFastestInterval(15000);
        mLocationRequest.setSmallestDisplacement(50); // set this as 100 to 200 if user use app while driving or motor riding
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
```


Note: No need to show notication, as location is taken when the app is in foreground.

## Background Tracking

Background Tracking is need when you want to track user location even after your app is closed or killed from task. For background tracking you must notify users via foreground notification while getting location. For this purpose we are using foreground service named(LocationJobService.java). Android versions > 7.1 restricts background location process even though foreground service is used. You cannot override this, and you will get 3 0r 4 location updates for 1 hour period. For more information, [refer here](https://developer.android.com/about/versions/oreo/background-location-limits)

## Screenshots
![main_page](https://github.com/martinraj/tracking-location-provider-android/blob/master/mainpage.jpeg)
![background_service](https://github.com/martinraj/tracking-location-provider-android/blob/master/bgservice.jpeg)
![notification](https://github.com/martinraj/tracking-location-provider-android/blob/master/notification.jpeg)
