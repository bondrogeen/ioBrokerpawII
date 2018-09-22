package ru.codedevice.iobrokerpawii;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.WindowManager;

import com.jjoe64.graphview.series.DataPoint;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebServerService extends Service implements SensorEventListener {

    private WebServer webServer;
    private TextToSpeech tts;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wl = null;
    private BroadcastReceiver broadcastReceiver;
    private SharedPreferences settings;
    private SensorManager sensorManager;
    private List<Sensor> sensors;

    private final int DEFAULT_NOTIFICATION_ID = 101;
    private boolean isStarted = false;
    private final String INSTALL_DIR = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/ioBroker/paw_2";
    private boolean TTS_OK = false;
    private final String NOTIFICATION_CHANNEL_ID_SERVICE = "ru.codedevice.iobrokerpawii.WebServerService";
    private final String NOTIFICATION_CHANNEL_ID_INFO = "com.package.download_info";

    private String TAG = "WebServerService";
    private JSONObject all = new JSONObject();
    private JSONObject wifi = new JSONObject();
    private JSONObject volume = new JSONObject();
    private JSONObject memory = new JSONObject();
    private JSONObject info = new JSONObject();
    private JSONObject tempJSON;

    private String serverIP, serverPort, event_battery_full;

    private String accelerometerTempValue, orientationTempValue, lightTempValue, proximityTempValue, pressureTempValue, gyroscopeTempValue, temperatureTempValue, humidityTempValue;


    private String number,text,type;

    private boolean batteryOneOk,batteryOneLow = true;
    private int notificationID = 1;

    private String BATTERY_HEALTH[] = {"unknown","unknown","good","overhead","dead","over_voltage","unspecified_failure","cold"};

    Map<String, String> map;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        map = new HashMap<String, String>();
        if (intent != null && intent.getExtras() != null) {
            String status = intent.getStringExtra("init");
            Log.d(TAG, "init :" + status);
            switch (status) {
                case "start":
                    if (isConnectedInWifi()) {
                        startAndroidWebServer();
                    }
                    break;
                case "sms":
                    map.put("text",intent.getStringExtra("text"));
                    map.put("number",intent.getStringExtra("number"));
                    post("/",map);
                    break;
                case "call":
                    type = intent.getStringExtra("type");
                    number = intent.getStringExtra("number");
                    try {
                        if(type.equals("ringing")){
                            post("/",new JSONObject().put("number",number).put("type",type));
                        }else{
                            post("/",new JSONObject().put("type",type));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
                case "batteryInfo":
                    Log.i(TAG,"batteryInfo_____________________");
                    int level = intent.getIntExtra("level", -1);
                    event_battery_full = settings.getString("event_battery_full", "100");
                    int setting_full_battery = Integer.parseInt(event_battery_full);

                    if (level >= setting_full_battery && batteryOneOk) {
                        map.put("status", "ok");
                        batteryOneOk = false;
                    }
                    if(level < setting_full_battery) batteryOneOk = true;

                    if (level <= 15 && batteryOneLow) {
                        map.put("status", "low");
                        batteryOneLow = false;
                    }
                    if(level > 15)batteryOneLow = true;


                    map.put("level", String.valueOf(level));
                    int voltage = intent.getIntExtra("voltage", -1);
                    map.put("voltage", String.valueOf((float) voltage / 1000));
                    int plugtype = intent.getIntExtra("plugged", -1);
                    String type = "";
                    if (plugtype == 0) {
                        type = "none";
                    } else if (plugtype == 1) {
                        type = "ac";
                    } else if (plugtype == 2) {
                        type = "usb";
                    } else {
                        type = String.valueOf(plugtype);
                    }
                    map.put("type", type);
                    int health = intent.getIntExtra("health", -1);
                    map.put("health", String.valueOf(BATTERY_HEALTH[health]));
                    int temperature = intent.getIntExtra("temperature", -1);
                    map.put("temperature", String.valueOf((float) temperature / 10));
                    post("/",map);
                    break;
                case "wifi":
                    if(isConnectedInWifi()){
//                        startAndroidWebServer();
                    }
                    break;
                case "power":
                    map.put("power",intent.getStringExtra("power"));
                    post("/",map);
                    break;
                case "screen":
                    map.put("screen",intent.getStringExtra("state"));
                    post("/",map);
                    break;
                case "alert":
                    setAlert(intent);
                    break;
            }
        }

        //return Service.START_STICKY;
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        map = new HashMap<String, String>();
        initSettings();
        checkInstallation();
        initBroadReceiver();
        initTTS();
        initSensors();

    }

    private void initSensors() {

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        if(settings.getBoolean("sensors_accelerometer",false)){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(settings.getBoolean("sensors_orientation",false)){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(3), SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(settings.getBoolean("sensors_light",false)){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(settings.getBoolean("sensors_proximity",false)){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(settings.getBoolean("sensors_pressure",false)){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(settings.getBoolean("sensors_gyroscope",false)){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(settings.getBoolean("sensors_temperature",false)){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE), SensorManager.SENSOR_DELAY_NORMAL);
        }
        if(settings.getBoolean("sensors_humidity",false)){
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
//        tvText.setText(String.valueOf(sensorEvent.values[0]));

//        Log.i(TAG, String.valueOf(sensorEvent.sensor.getType()));

        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
//                name = "Accelerometer";
//                accelerometerTempValue
//                sensorValue0.setText("X: " + event.values[0]);
//                sensorValue1.setText("Y: " + event.values[1]);
//                sensorValue2.setText("Z: " + event.values[2]);
                break;
            case 3:
//                name = "Orientation";
//                orientationTempValue
                break;
            case Sensor.TYPE_LIGHT:
//                name = "Light";
                lightTempValue = String.valueOf(sensorEvent.values[0]);
                break;
            case Sensor.TYPE_PROXIMITY:
//                name = "Proximity";
                proximityTempValue = String.valueOf(sensorEvent.values[0]);
                post("/","proximity",proximityTempValue);
                break;
            case Sensor.TYPE_PRESSURE:
//                name = "Pressure";
                pressureTempValue = String.valueOf(sensorEvent.values[0]);
                break;
            case Sensor.TYPE_GYROSCOPE:
//                name = "Gyroscope";
//                gyroscopeTempValue
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
//                name = "Temperature";
                temperatureTempValue = String.valueOf(sensorEvent.values[0]);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
//                name = "Humidity";
                humidityTempValue = String.valueOf(sensorEvent.values[0]);
                break;
        }


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


    private void initSettings() {
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        serverIP = settings.getString("connection_web_server_ip", "");
        serverPort = settings.getString("connection_web_server_port", "");
        event_battery_full = settings.getString("event_battery_full", "");
        Log.i(TAG,serverIP);
        Log.i(TAG,serverPort);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAndroidWebServer();
        if(broadcastReceiver != null){
            unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }
        if(sensorManager != null){
            sensorManager.unregisterListener(this);
            sensorManager = null;
        }
        stopSelf();
    }

    public void initBroadReceiver() {

        IntentFilter filter = new IntentFilter();

        if(settings.getBoolean("event_wifi", false)){
            filter.addAction("android.net.wifi.STATE_CHANGE");
//        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        }

        if(settings.getBoolean("event_call", false)) filter.addAction("android.intent.action.PHONE_STATE");

        if(settings.getBoolean("event_display", false)){
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
        }

        if(settings.getBoolean("event_call", false)) filter.addAction("android.intent.action.PHONE_STATE");

        if(settings.getBoolean("event_sms", false)) filter.addAction("android.provider.Telephony.SMS_RECEIVED");

        if(settings.getBoolean("event_battery", false)){
            filter.addAction("android.intent.action.BATTERY_LOW");
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        }

        broadcastReceiver = new MainReceiver();
        registerReceiver(broadcastReceiver, filter);
    }

    public void sendNotification(String Ticker, String Title, String Text) {
        String chanelId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE, "WebServerService", NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID_INFO, "Download Info", NotificationManager.IMPORTANCE_DEFAULT));
            chanelId = NOTIFICATION_CHANNEL_ID_SERVICE;
        } else {
            chanelId = "";
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,chanelId);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(Ticker)
                .setContentTitle(Title)
                .setContentText(Text)
                .setWhen(System.currentTimeMillis());

        Notification notification;
        if (android.os.Build.VERSION.SDK_INT <= 15) {
            notification = builder.getNotification(); // API-15 and lower
        } else {
            notification = builder.build();
        }
        startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }

    private void checkInstallation() {
        if (!new File(INSTALL_DIR).exists()) {
            new File(INSTALL_DIR).mkdirs();
            HashMap<String, Integer> keepFiles = new HashMap<>();
            try {
                extractZip(getAssets().open("content.zip"), INSTALL_DIR, keepFiles);
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

            while (true) {
                while (zipentry != null) {
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
                        while ((n = zipinputstream.read(buf, 0, 8192)) > -1) {
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

    private void initTTS() {
        TTS_OK = false;
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Locale def_local = Locale.getDefault();
                if (def_local!=null && status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(def_local);
                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    } else {
                        TTS_OK = true;
                        Log.i("TTS", "This Language is supported");
                    }
                } else {
                    Log.e("TTS", "Initilization Failed!");
                }
            }
        });
    }

    private boolean startAndroidWebServer() {
        if (!isStarted) {
            try {
                int port = 8080;
                webServer = new WebServer(port);
                webServer.start(5000);
                sendNotification("Start", "Web Server", getIpAccess());
                isStarted = true;
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
        return "http://" + formatedIpAddress + ":8080";
    }

    public boolean isConnectedInWifi() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        NetworkInfo networkInfo = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        assert wifiManager != null;
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()
                && wifiManager.isWifiEnabled() && networkInfo.getTypeName().equals("WIFI");
    }

    private boolean stopAndroidWebServer() {
        if (isStarted && webServer != null) {
            webServer.stop();
            notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
            isStarted = false;
            return true;
        }
        return false;
    }

    private int getBrightness() {
        int brightness = 0;
        try {
            brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            brightness = (int) Math.round(brightness / 2.55);
        } catch (Exception ignored) {

        }
        return brightness;
    }

    private String getDisplay() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        String display;
        assert pm != null;
        if (pm.isScreenOn()) {
            display = "true";
        } else {
            display = "false";
        }
        return display;
    }

    private boolean setNotSleep(String s){
        Log.i("setNotSleep : ", String.valueOf(s));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (s.equals("true") && wl == null && pm != null) {
            wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    TAG);
            wl.acquire();
            return true;
        }else if (s.equals("false") && wl != null){
            wl.release();
            wl=null;
            return true;
        }
        return false;
    }

    private String getBrightnessMode() {
        String mode = "";
        try {
            mode = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == 1 ? "auto" : "manual";
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return mode;
    }

    private boolean setBrightness(int value) {
        if (value < 4) {
            value = 4;
        }
        if (value > 100) {
            value = 100;
        }
        if (value <= 100) {
            int num = (int) Math.round(value * 2.55);
            Log.i("Brightness", String.valueOf(num));
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, num);
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

    private boolean setBrightnessMode(String str) {
        if (str.equals("auto")) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            return true;
        }
        if (str.equals("manual")) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            return true;
        }
        return false;
    }

    private boolean openURL(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
    }

    private boolean vibrate(int val) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(val, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(val);
        }
        return true;
    }

    private JSONObject getInfoWiFi() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null && wifiInfo.getIpAddress() != 0) {
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
            String mac = wifiInfo.getMacAddress();
            String SSID = wifiInfo.getSSID();
            int Rssi = wifiInfo.getRssi();
            if (SSID == null) SSID = "unknown";
            String linkspeed = wifiInfo.getLinkSpeed() + " " + WifiInfo.LINK_SPEED_UNITS;
            if (wifiInfo.getLinkSpeed() < 0) {
                linkspeed = "unknown";
            }
            String wifiStrengthPercent = (100 + wifiInfo.getRssi() + 20) + "%";
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

    private String getInfo() {
        try {
            info.put("brand", Build.BRAND);
            info.put("model", Build.MODEL);
            info.put("time", Build.TIME);
            info.put("sdk", Build.VERSION.SDK);
            info.put("brightness", getBrightness());
            info.put("lcd", getDisplay());
            info.put("brightnessMode", getBrightnessMode());
            info.put("timeScreen", getTimeScreenOff());

            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            assert audio != null;
            volume.put("ring", audio.getStreamVolume(AudioManager.STREAM_RING));
            volume.put("music", audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            volume.put("alarm", audio.getStreamVolume(AudioManager.STREAM_ALARM));
            volume.put("notification", audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION));
            volume.put("system", audio.getStreamVolume(AudioManager.STREAM_SYSTEM));
            volume.put("voice_call", audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL));

            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            assert activityManager != null;
            activityManager.getMemoryInfo(memoryInfo);
            memory.put("free_RAM", memoryInfo.availMem / 1024 / 1024);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                memory.put("total_RAM", memoryInfo.totalMem / 1024 / 1024);
            }

            all.put("info", info);
            all.put("memory", memory);
            all.put("wifi", getInfoWiFi());
            all.put("audio_volume", volume);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return String.valueOf(all);
    }

    private int getTimeScreenOff() {
        int timeOff = 0;
        try {
            timeOff = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
            timeOff = timeOff / 1000;
        } catch (Exception ignored) {

        }
        return timeOff;
    }

    private boolean setTimeScreenOff(int time) {
        time = (time != 0) ? time * 1000 : -1;
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, time);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean setHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        return true;
    }

    private boolean setAlert(Intent i) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(i.getStringExtra("title") != null ? i.getStringExtra("title") : "Title");
        builder.setMessage(i.getStringExtra("alert"));

        if (i.getStringExtra("cancel") != null && i.getStringExtra("cancel").equals("true") && (i.getStringExtra("positiveButton") != null
                || i.getStringExtra("neutralButton") != null
                || i.getStringExtra("negativeButton") != null)) {
            builder.setCancelable(false);
        } else {
            builder.setCancelable(true);
        }

        if (i.getStringExtra("positiveButton") != null) {
            builder.setPositiveButton(i.getStringExtra("positiveButton"),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                            int id) {
                            dialog.cancel();
                        }
                    });
        }

        if (i.getStringExtra("neutralButton") != null) {
            builder.setNeutralButton(i.getStringExtra("neutralButton"),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                            int id) {
                            dialog.cancel();
                        }
                    });
        }

        if (i.getStringExtra("negativeButton") != null) {
            builder.setNegativeButton(i.getStringExtra("negativeButton"),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                            int id) {
                            dialog.cancel();
                        }
                    });
        }

        AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        alertDialog.show();

        return true;
    }

    private boolean setNoti(Map<String, String> str) {
        notificationID = (str.get("id") != null && isNumber(str.get("id"))) ? Integer.parseInt(str.get("id")) : notificationID++;
        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(str.get("title") != null ? str.get("title") : "Title")
                        .setContentText(str.get("newNoti"))
                        .setShowWhen(true)
                        .setContentIntent(resultPendingIntent);


        if (str.get("contentInfo") != null) {
            builder.setContentInfo(str.get("contentInfo"));
        }
        if (str.get("vibrate") != null && str.get("vibrate").equals("true")) {
            builder.setVibrate(new long[]{1000, 1000});
        }
        if (str.get("sound") != null && str.get("sound").equals("true")) {
            builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
        }
        if (str.get("light") != null && str.get("light").equals("true")) {
            builder.setLights(Color.RED, 3000, 3000);
        }
        if (str.get("progress") != null && isNumber(str.get("progress"))) {
            builder.setProgress(100, Integer.parseInt(str.get("progress")), false);
        }

        Notification notification = builder.build();
        notificationManager.notify(notificationID, notification);
        return true;
    }

    private boolean delNoti() {
        notificationManager.cancelAll();
        return true;
    }

    private boolean delNoti(int id) {
        notificationManager.cancel(id);
        return true;
    }

    private boolean setVolume(int val, String type) {
        AudioManager audioMgr = (AudioManager) getSystemService(AUDIO_SERVICE);
        int volumeType;

        switch (type) {
            case "ring":
                volumeType = AudioManager.STREAM_RING;
                break;
            case "music":
                volumeType = AudioManager.STREAM_MUSIC;
                break;
            case "notification":
                volumeType = AudioManager.STREAM_NOTIFICATION;
                break;
            case "alarm":
                volumeType = AudioManager.STREAM_ALARM;
                break;
            case "system":
                volumeType = AudioManager.STREAM_SYSTEM;
                break;
            case "voice":
                volumeType = AudioManager.STREAM_VOICE_CALL;
                break;
            default:
                return false;
        }

        if (val < 0) {
            val = 0;
        }
        int max = audioMgr.getStreamMaxVolume(volumeType);
        if (val > max) {
            val = max;
        }
        audioMgr.setStreamVolume(volumeType, val, 0);
        return true;
    }

    private boolean setCall(String num) {
        Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + num));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }else{
            startActivity(i);
        }
        return true;
    }

    private boolean speakOut(String text) {
        if (TTS_OK){
            setTtsUtteranceProgressListener();
            HashMap<String, String> map = new HashMap<>();
            map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,"messageID");
            tts.speak(text, TextToSpeech.QUEUE_ADD, map);
            return true;
        }
        return false;
    }

    private boolean speakStop() {
        if (TTS_OK) {
            tts.stop();
            return true;
        }
        return false;
    }

    private void setTtsUtteranceProgressListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.i(TAG, "TTS onStart");
                post("/","tts","start");
            }
            @Override
            public void onDone(String utteranceId) {
                Log.i(TAG, "TTS onDone");
                post("/","tts","done");
            }
            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS onError");
                post("/","tts","error");
            }
        });
    }

    private boolean setMessage(String num,String text){
        SmsManager.getDefault().sendTextMessage(num, null, text, null, null);
        return true;
    }

    public boolean disconnectCall(){
        try {
            String serviceManagerName = "android.os.ServiceManager";
            String serviceManagerNativeName = "android.os.ServiceManagerNative";
            String telephonyName = "com.android.internal.telephony.ITelephony";
            Class<?> telephonyClass;
            Class<?> telephonyStubClass;
            Class<?> serviceManagerClass;
            Class<?> serviceManagerNativeClass;
            Method telephonyEndCall;
            Object telephonyObject;
            Object serviceManagerObject;
            telephonyClass = Class.forName(telephonyName);
            telephonyStubClass = telephonyClass.getClasses()[0];
            serviceManagerClass = Class.forName(serviceManagerName);
            serviceManagerNativeClass = Class.forName(serviceManagerNativeName);
            Method getService = serviceManagerClass.getMethod("getService", String.class);
            Method tempInterfaceMethod = serviceManagerNativeClass.getMethod("asInterface", IBinder.class);
            Binder tmpBinder = new Binder();
            tmpBinder.attachInterface(null, "fake");
            serviceManagerObject = tempInterfaceMethod.invoke(null, tmpBinder);
            IBinder retbinder = (IBinder) getService.invoke(serviceManagerObject, "phone");
            Method serviceMethod = telephonyStubClass.getMethod("asInterface", IBinder.class);
            telephonyObject = serviceMethod.invoke(null, retbinder);
            telephonyEndCall = telephonyClass.getMethod("endCall");
            telephonyEndCall.invoke(telephonyObject);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG,"FATAL ERROR: could not connect to telephony subsystem");
            Log.e(TAG, "Exception object: " + e);
            return false;
        }
    }

    private boolean post(String url, JSONObject str){
        return post(url,str.toString());
    }

    private boolean post(String url, Map<String, String> m){ return post(url,new JSONObject(m)); }

    private boolean post(String url, String key , String value){
        return post(url, "{\"" + key + "\":\"" + value + "\"}");
    }

    private boolean post(String url, String json){
        if(isStarted) {
            String link = "http://" + serverIP + ":" + serverPort + url;
            Log.i(TAG, link);
            Log.i(TAG, json);
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            OkHttpClient client = new OkHttpClient();
            RequestBody body = RequestBody.create(JSON, json);
            Request request = new Request.Builder()
                    .url(link)
                    .post(body)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, final Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    } else {
                        Log.i(TAG, response.body().string());
                    }
                }
            });
            return true;
        }
        return false;
    }


    public class WebServer extends NanoHTTPD {
        String TAG = "WebServer";
        private final String OK = "{status:OK}";
        private final String ERROR = "{status:ERROR}";
        WebServer(int port) {super(port);}

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

            if(uri.equals("/api/get.json")){
                Log.i(TAG, "GET");
                return newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", getInfo());
            }else if(uri.equals("/api/set.json")){
                Log.i(TAG, "SET");
                Log.i(TAG, String.valueOf(parms.size()));
                if(parms.size()>=1){
                    if(parms.get("brightness") != null && isNumber(parms.get("brightness"))){
                        status = setBrightness(Integer.parseInt(parms.get("brightness"))) ? OK : ERROR;
                    }
                    if(parms.get("brightnessMode")!=null){
                        status = setBrightnessMode(parms.get("brightnessMode")) ? OK : ERROR;
                    }
                    if(parms.get("link") != null ){
                        status = openURL(parms.get("link")) ? OK : ERROR;
                    }
                    if(parms.get("vibrate") != null && isNumber(parms.get("vibrate"))){
                        status = vibrate(Integer.parseInt(parms.get("vibrate"))) ? OK : ERROR;
                    }
                    if(parms.get("timeScreenOff") != null && isNumber(parms.get("screenOff"))){
                        status = setTimeScreenOff(Integer.parseInt(parms.get("screenOff"))) ? OK : ERROR;
                    }
                    if(parms.get("home") != null){
                        status = setHome() ? OK : ERROR;
                    }
                    if(parms.get("test") != null){
                        status = post("http://192.168.1.83:8898/","{}") ? OK : ERROR;
//                        status = post("http://192.168.1.56/","{}") ? OK : ERROR;
                    }
                    if(parms.get("wakeUp") != null){
                        status = setNotSleep(parms.get("sleep")) ? OK : ERROR;
                    }
                    if(parms.get("tts") != null){
                        status = speakOut(parms.get("tts")) ? OK : ERROR;
                    }
                    if(parms.get("ttsStop") != null){
                        status = speakStop() ? OK : ERROR;
                    }
                    if(parms.get("volume") != null && isNumber(parms.get("volume"))){
                        status = setVolume(Integer.parseInt(parms.get("volume")), parms.get("type") != null ? parms.get("type") : "music") ? OK : ERROR;
                    }
                    if(parms.get("alert") != null){
                        status = OK;
                        Intent i = new Intent(getApplicationContext(),WebServerService.class);
                        for (String key : parms.keySet()) {
                            i.putExtra(key,parms.get(key));
                        }
                        i.putExtra("init","alert");
                        startService(i);
                    }
                    if(parms.get("newNoti") != null){
                        status = setNoti(parms) ? OK : ERROR;
                    }
                    if(parms.get("call") != null){
                        status = setCall(parms.get("call")) ? OK : ERROR;
                    }
                    if(parms.get("callEnd") != null){
                        status = disconnectCall() ? OK : ERROR;
                    }
                    if(parms.get("sms") != null && parms.get("text") != null){
                        status = setMessage(parms.get("sms"),parms.get("text")) ? OK : ERROR;
                    }
                    if(parms.get("delNoti") != null){
                        if(isNumber(parms.get("delNoti"))) {
                            status = delNoti(Integer.parseInt(parms.get("delNoti"))) ? OK : ERROR;
                        }
                        if(parms.get("delNoti").equals("all")){
                            status = delNoti() ? OK : ERROR;
                        }
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