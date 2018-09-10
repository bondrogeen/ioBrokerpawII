package ru.codedevice.iobrokerpawii;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import fi.iki.elonen.NanoHTTPD;

public class MainService extends Service {

    private WebServer androidWebServer;
    private static boolean isStarted = false;
    private NotificationManager notificationManager;
    public static final int DEFAULT_NOTIFICATION_ID = 101;
    private static final String INSTALL_DIR = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/ioBroker/paw_2";

    private final String OK = "{status:OK}";
    private final String ERROR = "{status:ERROR}";

    int port = 8080;
    String TAG = "MainService";
    JSONObject all = new JSONObject();
    JSONObject wifi = new JSONObject();
    JSONObject volume = new JSONObject();
    JSONObject memory = new JSONObject();
    JSONObject info = new JSONObject();




    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (intent != null && intent.getExtras() != null) {
            String status = intent.getStringExtra("init");
            Log.d(TAG, "init :" + status);
            switch (status) {
                case "start":
                    if (isConnectedInWifi()) {
                        if (!isStarted && startAndroidWebServer()) {
                            isStarted = true;
                            sendNotification("Ticker","Title","Text");
                        } else if (stopAndroidWebServer()) {
                            isStarted = false;
                        }
                    }
                    break;

            }
        }

        //return super.onStartCommand(intent, flags, startId);
        //return Service.START_STICKY;
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        checkInstallation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        stopAndroidWebServer();
        isStarted = false;

        notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
        stopSelf();
    }

    private void checkInstallation() {
        if (!new File(INSTALL_DIR).exists()) {
            new File(INSTALL_DIR).mkdirs();
            HashMap<String, Integer> keepFiles = new HashMap<>();
            try {
                extractZip(getAssets().open("content.zip"),INSTALL_DIR, keepFiles);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void extractZip(InputStream zipIs, String dest, HashMap<String, Integer> keepFiles) {
        try {
            if (!dest.endsWith("/")) {
                dest = dest + "/";
            }

            byte[] buf = new byte[8192];
            ZipInputStream zipinputstream;
            zipinputstream = new ZipInputStream(zipIs);
            ZipEntry zipentry = zipinputstream.getNextEntry();

            while(true) {
                while(zipentry != null) {
                    String entryName = dest + zipentry.getName();
                    entryName = entryName.replace('/', File.separatorChar);
                    entryName = entryName.replace('\\', File.separatorChar);
                    File newFile = new File(entryName);
                    if (keepFiles.get(zipentry.getName()) != null && newFile.exists()) {
                        Log.d(TAG, "File not overwritten: " + zipentry.getName());
                        zipentry = zipinputstream.getNextEntry();
                    } else if (zipentry.isDirectory()) {
                        newFile.mkdirs();
                        zipentry = zipinputstream.getNextEntry();
                    } else {
                        if (!(new File(newFile.getParent())).exists()) {
                            (new File(newFile.getParent())).mkdirs();
                        }

                        FileOutputStream fileoutputstream = new FileOutputStream(entryName);

                        int n;
                        while((n = zipinputstream.read(buf, 0, 8192)) > -1) {
                            fileoutputstream.write(buf, 0, n);
                        }

                        fileoutputstream.close();
                        zipinputstream.closeEntry();
                        zipentry = zipinputstream.getNextEntry();
                    }
                }

                zipinputstream.close();
                break;
            }
        } catch (Exception var11) {
            Log.e(TAG, var11.getMessage());
        }

    }

    private boolean startAndroidWebServer() {
        if (!isStarted) {
            try {
                androidWebServer = new WebServer(port);
                androidWebServer.start(5000);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private String getIpAccess() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        final String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        return "http://" + formatedIpAddress + ":";
    }

    public boolean isConnectedInWifi() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        NetworkInfo networkInfo = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        assert wifiManager != null;
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()
                && wifiManager.isWifiEnabled() && networkInfo.getTypeName().equals("WIFI");
    }

    public void sendNotification(String Ticker,String Title,String Text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker("Start")
                .setContentTitle("ioBroker.paw 2")
                .setContentText(getIpAccess())
                .setWhen(System.currentTimeMillis());

        Notification notification;
        if (android.os.Build.VERSION.SDK_INT<=15) {
            notification = builder.getNotification(); // API-15 and lower
        }else{
            notification = builder.build();
        }
        startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }

    private boolean stopAndroidWebServer() {
        if (isStarted && androidWebServer != null) {
            androidWebServer.stop();
            return true;
        }
        return false;
    }

    private int getBrightness(){
        int brightness = 0;
        try {
            brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            brightness = (int) Math.round(brightness/2.55);
        } catch (Exception ignored) {

        }
        return brightness;
    }


    private String getDisplay(){
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        String display;
        assert pm != null;
        if(pm.isScreenOn()){
            display="true";
        }else{
            display="false";
        }
        return display;
    }

    private String getBrightnessMode(){
        String mode = "";
        try {
            mode = Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE) == 1 ? "auto" : "manual";
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return mode;
    }

    private boolean setBrightness(int value){
        if(value<4){value=4;}
        if(value>100){value=100;}
        if (value <=100 && value >=4){
            int num = (int) Math.round(value*2.55);
            Log.i("Brightness", String.valueOf(num));
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,num );
            return true;
        }
        return false;
    }

    public boolean isNumber(String str) {
        if (str == null || str.isEmpty()) return false;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) return false;
        }
        return true;
    }


    private boolean setBrightnessMode(String str){
        if (str.equals("auto")){
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            return true;
        }
        if (str.equals("manual")){
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            return true;
        }
        return false;
    }


    private JSONObject getInfoWiFi(){

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if(wifiInfo != null && wifiInfo.getIpAddress() != 0) {
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
            String mac = wifiInfo.getMacAddress();
            String SSID = wifiInfo.getSSID();
            int  Rssi = wifiInfo.getRssi();
            if(SSID == null) SSID = "unknown";
            String linkspeed = wifiInfo.getLinkSpeed() + " " + WifiInfo.LINK_SPEED_UNITS;
            if(wifiInfo.getLinkSpeed() < 0) { linkspeed = "unknown"; }
            String wifiStrengthPercent = (100 + wifiInfo.getRssi() + 20)+"%";
            try {
                wifi.put("ssid", SSID);
                wifi.put("strengthpercent", wifiStrengthPercent);
                wifi.put("linkspeed", linkspeed);
                wifi.put("ip", formatedIpAddress);
                wifi.put("rssi", Rssi);
                wifi.put("mac", mac);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return wifi;
    }
    private String getInfo(){
        try {
            info.put("brand", Build.BRAND);
            info.put("model", Build.MODEL);
            info.put("time", Build.TIME);
            info.put("sdk", Build.VERSION.SDK);
            info.put("brightness",getBrightness());
            info.put("lcd",getDisplay());
            info.put("brightnessMode",getBrightnessMode());


            AudioManager audio = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            assert audio != null;
            volume.put("ring", audio.getStreamVolume(AudioManager.STREAM_RING));
            volume.put("music", audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            volume.put("alarm", audio.getStreamVolume(AudioManager.STREAM_ALARM));
            volume.put("notification", audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION));
            volume.put("system", audio.getStreamVolume(AudioManager.STREAM_SYSTEM));
            volume.put("voice_call", audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL));

            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
            assert activityManager != null;
            activityManager.getMemoryInfo(memoryInfo);
            memory.put("free_RAM",memoryInfo.availMem/ 1024 /1024);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                memory.put("total_RAM",memoryInfo.totalMem/ 1024 /1024);
            }


            all.put("info",info);
            all.put("memory",memory);
            all.put("wifi",getInfoWiFi());
            all.put("audio_volume",volume);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return String.valueOf(all);
    }

    public class WebServer extends NanoHTTPD {
        String TAG = "WebServer";
        WebServer(int port) {
            super(port);
        }

        @Override
        public NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session) {
            Map<String, String> header = session.getHeaders();
            Map<String, String> parms = session.getParms();
            Response res;
            String uri = session.getUri();
            String status = ERROR;


            Log.i(TAG, uri);
            Log.i(TAG, String.valueOf(parms));
            Log.i(TAG, String.valueOf(header));

            if(uri.equals("/get.json")){
                Log.i(TAG, "GET");
                return newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", getInfo());
            }else if(uri.equals("/set.json")){
                Log.i(TAG, "SET");
                if((parms.get("send") != null)){
                    if(parms.get("send").equals("brightness") && parms.get("value") != null && isNumber(parms.get("value"))){
                        status = setBrightness(Integer.parseInt(parms.get("value"))) ? OK : ERROR;
                    }
                    if(parms.get("send").equals("brightnessMode") && parms.get("value") != null ){
                        status = setBrightnessMode(parms.get("value")) ? OK : ERROR;
                    }
                }
                return newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", status);
            }else{
                Log.i(TAG, "ELSE");
                if (uri.equals("/")) {
                    uri = "/index.html";
                }
                File f = new File(INSTALL_DIR + uri);
                if (f.exists() && !f.isDirectory()) {
                    String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f.length()).hashCode());
                    long fileLen = f.length();
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(f);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    res = newFixedLengthResponse(Response.Status.OK, getMimeTypeForFile(uri), fis, fileLen);
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", "" + fileLen);
                    res.addHeader("ETag", etag);
                    return res;
                }
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "<h1>Page not found</h1>");
            }

        }
    }
}