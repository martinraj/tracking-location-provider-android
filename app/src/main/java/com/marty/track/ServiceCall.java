package com.marty.track;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

/**
 * Created by Marty on 3/20/2017.
 */

public class ServiceCall {
    public static final String TIME_OUT="timeout";
    public static String doServerCall(String method, URL url, String jsonData, String token){
        HttpURLConnection connection = null;
        Boolean postPut;
        postPut = method.equals("POST") || method.equals("PUT") || method.equals("DELETE");
        try {
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("token",  token);
            connection.setDoOutput(postPut);
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);
            if(postPut) {
                if(jsonData!=null) {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
                    writer.write(jsonData);
                    writer.close();
                }else{
                    Log.d("json data "," null");
                }
            }
            BufferedReader reader;
            InputStream inputStream = null;
            int status = connection.getResponseCode();

            if(status == 200)
                inputStream = connection.getInputStream();
            else
                inputStream = connection.getErrorStream();

            StringBuilder buffer = new StringBuilder();
            if (inputStream == null) {
                // Nothing to do.
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String inputLine;
            while ((inputLine = reader.readLine()) != null)
                buffer.append(inputLine).append("\n");
            if (buffer.length() == 0) {
                // Stream was empty. No point in parsing.
                return null;
            }
            return buffer.toString();
        } catch (SocketTimeoutException e){
            return TIME_OUT;
        }
        catch (IOException e){
            e.printStackTrace();
        }finally{
            if(connection != null)
                connection.disconnect();
        }
        return null;
    }

}
