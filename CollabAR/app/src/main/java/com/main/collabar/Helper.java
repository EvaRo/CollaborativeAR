package com.main.collabar;

/**
 * This class is a simple helper class that simplifies communication with the ThingWorx server
 */

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;


public class Helper {

    //You have to add the ThingWorx server and the server's generated AppKey here for the program to function
    private static final String APPKEY = "";
    private static final String SERVER = ""; // in form of: https://server.com:443

    public static String sendPOSTRequest(String payload, String logtag, String urlpart) {
        try {
            String url = SERVER + urlpart + "?appKey=" + APPKEY;
            URL obj = new URL(url);

            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("appKey", APPKEY);

            //for POST/PUT, if request body exists
            con.setDoOutput(true);

            Log.d(logtag, "Payload: " + payload);

            OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
            out.write(payload);
            out.flush();
            out.close();

            int responseCode = con.getResponseCode();
            Log.d(logtag, "Sending 'POST' request to URL : " + url);
            Log.d(logtag, "Response Code : " + responseCode);

            String responseMessage = con.getResponseMessage();
            Log.d(logtag, "Response Message : " + responseMessage);

            //In case of Response Body
            BufferedReader br;
            StringBuilder sb = new StringBuilder();
            String line;

            if (200 <= con.getResponseCode() && con.getResponseCode() <= 299) {
                br = new BufferedReader(new InputStreamReader((con.getInputStream())));
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
            } else {
                br = new BufferedReader(new InputStreamReader((con.getErrorStream())));
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
            }
            return sb.toString();

        } catch (Exception e) {
            Log.d(logtag, "Error in Request : " + e.getMessage());
        }
        return "";
    }

    public static void sendDELETERequest(String logtag, String urlpart) {
        try {
            String url = SERVER + urlpart + "?appKey=" + APPKEY;
            URL obj = new URL(url);

            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("DELETE");
            con.setRequestProperty("appKey", APPKEY);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Content-Type", "application/json");

            int responseCode = con.getResponseCode();
            Log.d(logtag, "Sending 'DELETE' request to URL : " + url);
            Log.d(logtag, "Response Code : " + responseCode);

            String responseMessage = con.getResponseMessage();
            Log.d(logtag, "Response Message : " + responseMessage);

        } catch (Exception e) {
            Log.d(logtag, "Error in Request : " + e.getMessage());
        }
}
}
