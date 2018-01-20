package com.marty.track;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;


/**
 * Created by Marty on 12/20/2017.
 */

public class UploadLocationService extends IntentService{

    ArrayList<Location> points;

    public UploadLocationService(){
        super("UploadLocationService");
    }
    public UploadLocationService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if(intent !=null) {
            Bundle b = intent.getExtras();
            if (b != null) {
                points = intent.getParcelableArrayListExtra("points");
                URL url;
                try {
                        url = new URL("http://localhost:8000/trackdrivertrip/update"); // set your server url
                        sendLocation(url,"");
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendLocation(URL url,String token){
        try {
            String jsonResp;
            int code = 0;
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id","userid");
            JSONArray pointsArray = new JSONArray();
            for (int i = 0; i < points.size(); i++) {
                pointsArray.put(new JSONArray().put(points.get(i).getLongitude()).put(points.get(i).getLatitude()));
            }
            jsonObject.put("coordinates",pointsArray);
            Log.d("data sent", jsonObject.toString());
            if (pointsArray.length() != 0) {
                jsonResp = ServiceCall.doServerCall("POST", url, pointsArray.toString(), token);
                if (jsonResp != null && jsonResp.equals(ServiceCall.TIME_OUT)) {
                    code = 500;
                    return;
                } else if (jsonResp == null) {
                    code = 500;
                    return;
                }
//                Log.d("resp", jsonResp);
//                JSONObject json = new JSONObject(jsonResp);
//                code = Integer.parseInt(json.getString("code"));
            } else {
                code = 100;
            }

        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
