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
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.WindowManager;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MQTTService extends Service implements SensorEventListener, MqttCallback {

    MqttAndroidClient MQTTclient;
    MqttConnectOptions options;
    private TextToSpeech tts;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wl = null;
    private BroadcastReceiver broadcastReceiver;
    private SharedPreferences settings;
    private SensorManager sensorManager;
    private Timer timeSensor;

    private final int DEFAULT_NOTIFICATION_ID = 101;
    private boolean isStarted = false;
    private final String INSTALL_DIR = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/ioBroker/paw_2";
    private boolean TTS_OK = false;
    private final String NOTIFICATION_CHANNEL_ID_SERVICE = "ru.codedevice.iobrokerpawii.MQTTService";
    private final String NOTIFICATION_CHANNEL_ID_INFO = "com.package.download_info";

    private String TAG = "MQTTService";
    private JSONObject all = new JSONObject();
    private JSONObject wifi = new JSONObject();
    private JSONObject volume = new JSONObject();
    private JSONObject memory = new JSONObject();
    private JSONObject info = new JSONObject();
    private JSONObject tempJSON;

    private String mqttIP, mqttPort, mqttLogin, mqttPass, mqttDevice, event_battery_full;

    private String accelerometerTempValue, orientationTempValue, lightTempValue, proximityTempValue, pressureTempValue, gyroscopeTempValue, temperatureTempValue, humidityTempValue;


    private String clientId = "MAC";
    private String serverUri;
    private String number,text,type;

    private boolean batteryOneOk,batteryOneLow = true;
    private int notificationID = 1;

    private int timeScreenOff;
    private String BATTERY_HEALTH[] = {"unknown","unknown","good","overhead","dead","over_voltage","unspecified_failure","cold"};

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
                    if (hasConnection()) {
                        startMQTT();
                    }
                    break;
                case "sms":
                    publish("info/sms/number",  intent.getStringExtra("number"));
                    publish("info/sms/text",  intent.getStringExtra("text"));
                    break;
                case "call":
                    if(type.equals("ringing")){
                        publish("info/call/number",  intent.getStringExtra("number"));
                        publish("info/call/status",  intent.getStringExtra("type"));
                    }else{
                        publish("info/call/status",  intent.getStringExtra("type"));
                    }

                    break;
                case "batteryInfo":
                    Log.i(TAG,"batteryInfo_____________________");
                    int level = intent.getIntExtra("level", -1);
                    event_battery_full = settings.getString("event_battery_full", "100");
                    int setting_full_battery = Integer.parseInt(event_battery_full);

                    if (level >= setting_full_battery && batteryOneOk) {
                        publish("info/battery/status",  "ok");
                        batteryOneOk = false;
                    }
                    if(level < setting_full_battery) batteryOneOk = true;

                    if (level <= 15 && batteryOneLow) {
                        publish("info/battery/status",  "low");
                        batteryOneLow = false;
                    }
                    if(level > 15)batteryOneLow = true;

                    publish("info/battery/voltage",  String.valueOf(level));
                    int voltage = intent.getIntExtra("voltage", -1);
                    publish("info/battery/voltage",  String.valueOf((float) voltage / 1000));
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
                    publish("info/battery/typeConnection",  type);
                    int health = intent.getIntExtra("health", -1);
                    publish("info/battery/health",  String.valueOf(BATTERY_HEALTH[health]));
                    int temperature = intent.getIntExtra("temperature", -1);
                    publish("info/battery/temperature",  String.valueOf((float) temperature / 10));
                    break;
                case "wifi":
                    if(hasConnection()){
//                        startAndroidWebServer();
                    }
                    break;
                case "power":
                    publish("info/battery/charging", intent.getStringExtra("power"));
                    break;
                case "screen":
                    publish("info/display/status", intent.getStringExtra("state"));
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
        initSettings();
        initMQTT();
        checkInstallation();
        initBroadReceiver();
        initTTS();
        initSensors();

    }

    private void initSensors() {

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

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

        if(settings.getBoolean("sensors_accelerometer",false)
                || settings.getBoolean("sensors_orientation",false)
                || settings.getBoolean("sensors_light",false)
                || settings.getBoolean("sensors_pressure",false)
                || settings.getBoolean("sensors_gyroscope",false)
                || settings.getBoolean("sensors_temperature",false)
                || settings.getBoolean("sensors_humidity",false)){
            timerSensors();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
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
//                orientationTempValue
                break;
            case Sensor.TYPE_LIGHT:
                lightTempValue = String.valueOf(sensorEvent.values[0]);
                break;
            case Sensor.TYPE_PROXIMITY:
                proximityTempValue = String.valueOf(sensorEvent.values[0]);
                publish("info/sensors/proximity", sensorEvent.values[0] == 0 ? "true" : "false");
                break;
            case Sensor.TYPE_PRESSURE:
                pressureTempValue = String.valueOf(sensorEvent.values[0]);
                break;
            case Sensor.TYPE_GYROSCOPE:
//                gyroscopeTempValue
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                temperatureTempValue = String.valueOf(sensorEvent.values[0]);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                humidityTempValue = String.valueOf(sensorEvent.values[0]);
                break;
        }
    }

    private void timerSensors(){
        timeSensor = new Timer();
        timeSensor.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if(humidityTempValue != null) publish("info/sensors/humidity", new DecimalFormat("##0.00").format(Float.parseFloat(humidityTempValue)));
                if(temperatureTempValue != null) publish("info/sensors/temperature", new DecimalFormat("##0.00").format(Float.parseFloat(temperatureTempValue)));
                if(pressureTempValue != null) publish("info/sensors/pressure", new DecimalFormat("##0.00").format(Float.parseFloat(pressureTempValue)  * 0.75));
                if(lightTempValue != null) publish("info/sensors/light", lightTempValue);
            }
        }, 1000, 60000);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void initSettings() {
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        mqttIP = settings.getString("connection_mqtt_ip", "");
        mqttPort = settings.getString("connection_mqtt_port", "");
        mqttLogin = settings.getString("connection_mqtt_login", "");
        mqttPass = settings.getString("connection_mqtt_pass", "");
        mqttDevice = settings.getString("connection_mqtt_device", "");
        serverUri = "tcp://" + mqttIP + ":" + mqttPort;
        event_battery_full = settings.getString("event_battery_full", "");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMQTT();
        if(broadcastReceiver != null){
            unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }
        if(sensorManager != null){
            sensorManager.unregisterListener(this);
            sensorManager = null;
        }
        if(timeSensor != null){
            timeSensor.cancel();
            timeSensor = null;
        }
        stopSelf();
    }

    public void initBroadReceiver() {

        IntentFilter filter = new IntentFilter();

        if(settings.getBoolean("event_wifi", false))filter.addAction("android.net.wifi.STATE_CHANGE");
        if(settings.getBoolean("event_call", false)) filter.addAction("android.intent.action.PHONE_STATE");
        if(settings.getBoolean("event_call", false)) filter.addAction("android.intent.action.PHONE_STATE");
        if(settings.getBoolean("event_sms", false)) filter.addAction("android.provider.Telephony.SMS_RECEIVED");

        if(settings.getBoolean("event_battery", false)){
            filter.addAction("android.intent.action.BATTERY_LOW");
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        }
        if(settings.getBoolean("event_display", false)){
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
        }

        broadcastReceiver = new MainReceiver();
        registerReceiver(broadcastReceiver, filter);
    }

    public void sendNotification(String Ticker, String Title, String Text) {
        String Id;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE, "MQTTService", NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID_INFO, "Download Info", NotificationManager.IMPORTANCE_DEFAULT));
            Id = NOTIFICATION_CHANNEL_ID_SERVICE;
        } else {
            Id = "";
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

//        Intent intentCancel = new Intent(this, MQTTService.class);
//        intentCancel.setAction("CANCEL");
//        intentCancel.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//
//        PendingIntent pendingIntentCancel = PendingIntent.getBroadcast(this, 1, intentCancel, PendingIntent.FLAG_CANCEL_CURRENT);
//

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,Id);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(Ticker)
//                .addAction(R.drawable.ic_add_black_24dp, "Stop", pendingIntentCancel)
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

    private boolean startMQTT() {
        if (!isStarted) {
            try {
                IMqttToken token = MQTTclient.connect(options);
                token.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "Connection");
                        pubOne();
                        setSubscribe();

                        sendNotification("Start", "MQTT", "is run");
                        Log.d(TAG, "Connection");
                        isStarted = true;
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.d(TAG, "Connection Failure");
//                        toast = Toast.makeText(getApplicationContext(),
//                                "Connection Failure", Toast.LENGTH_SHORT);
//                        toast.show();
//                        sendBrodecast("ConnectionFailure");
                        stopSelf();
                    }
                });


                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void initMQTT() {
        Log.i(TAG, "Start initMQTT");

        MQTTclient = new MqttAndroidClient(getApplication(), serverUri, mqttDevice);
        options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);

        if (mqttLogin!=null && !mqttLogin.equals("")){
            options.setUserName(mqttLogin);
        }
        if (mqttPass!=null && !mqttPass.equals("")){
            options.setPassword(mqttPass.toCharArray());
        }

        MQTTclient.setCallback(this);
    }

    private String getIpAccess() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        final String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        return "http://" + formatedIpAddress + ":8080";
    }

    public boolean hasConnection()    {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()){
            Log.e(TAG, "Network Connection is TYPE_WIFI");
            return true;
        }
        wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (wifiInfo != null && wifiInfo.isConnected()){
            Log.e(TAG, "Network Connection is TYPE_MOBILE");
            return true;
        }
        return false;
    }

    private boolean stopMQTT() {
        if (isStarted && MQTTclient != null) {
            try {
                MQTTclient.disconnect();
                MQTTclient=null;
            } catch (MqttException e) {
                e.printStackTrace();
            }
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

        if (val < 0 || val > audioMgr.getStreamMaxVolume(volumeType)) return false;

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
                publish("info/tts/status", "start");
            }
            @Override
            public void onDone(String utteranceId) {
                Log.i(TAG, "TTS onDone");
                publish("info/tts/status", "done");
            }
            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS onError");
                publish("info/tts/status", "error");
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



    public void publish(String topic, String payload) {
        if(MQTTclient.isConnected()) {
            try {
                byte[] encodedPayload = new byte[0];
                encodedPayload = payload.getBytes("UTF-8");
                MqttMessage message = new MqttMessage(encodedPayload);
                MQTTclient.publish(clientId + "/" + mqttDevice + "/" + topic, message);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (MqttPersistenceException e) {
                e.printStackTrace();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    private void pubOne() {
        publish("info/general/BRAND", Build.BRAND);
        publish("info/general/MODEL", Build.MODEL);
        publish("info/general/sdc", Build.VERSION.SDK);

        publish("info/display/brightness", String.valueOf(getBrightness()));
        publish("info/display/mode", getBrightnessMode());
        publish("info/display/status", getDisplay());
        publish("info/display/timeOff", String.valueOf(getTimeScreenOff()));
//        publish("info/display/sleep", "true");
        publish("info/tts/status", "done");
        publish("info/battery/charging", "");

        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        assert audio != null;

        publish("info/audio/ring", String.valueOf(audio.getStreamVolume(AudioManager.STREAM_RING)));
        publish("info/audio/music", String.valueOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)));
        publish("info/audio/alarm", String.valueOf(audio.getStreamVolume(AudioManager.STREAM_ALARM)));
        publish("info/audio/notification", String.valueOf(audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)));
        publish("info/audio/system", String.valueOf(audio.getStreamVolume(AudioManager.STREAM_SYSTEM)));
        publish("info/audio/voice", String.valueOf(audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)));


        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        assert activityManager != null;
        activityManager.getMemoryInfo(memoryInfo);
        publish("info/memory/RAM_free", String.valueOf(memoryInfo.availMem / 1024 / 1024));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            publish("info/memory/RAM_total", String.valueOf(memoryInfo.totalMem / 1024 / 1024));
        }


        publish("comm/call/number", "");
        publish("comm/call/end", "");
        publish("comm/sms/number", "");
        publish("comm/sms/text", "");
        publish("comm/tts/request", "");
        publish("comm/tts/stop", "");
        publish("comm/display/brightness", "");
        publish("comm/display/mode", "");
        publish("comm/display/toWake", "");
        publish("comm/display/timeOff", "");
        publish("comm/notification/create", "");
        publish("comm/notification/delete", "");
        publish("comm/notification/alert", "");
        publish("comm/other/home", "");
        publish("comm/other/openURL", "");
        publish("comm/other/vibrate", "");
        publish("comm/audio/ring", "");
        publish("comm/audio/music","");
        publish("comm/audio/alarm","");
        publish("comm/audio/notification","");
        publish("comm/audio/system", "");
        publish("comm/audio/voice","");

        publish("comm/display/turnOnOff","");

    }

    public void turnOnScreen(){
        Intent i = new Intent(this,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.putExtra("turnOnScreen","true");
        startActivity(i);
    }


    private void turnOffScreen(){
        Log.e(TAG, "timeScreenOff");
        timeScreenOff  = getTimeScreenOff();
        setTimeScreenOff(1);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                Log.e(TAG, "timeScreenOff + 5s");
                setTimeScreenOff(timeScreenOff);
            }
        }, 5000);
    }

    private void setSubscribe() {
        int qos = 1;
        try {
            IMqttToken subToken = MQTTclient.subscribe(clientId + "/" + mqttDevice +"/comm/*", qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // The message was published
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void connectionLost(Throwable cause) {

    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {

        Log.d(TAG, "topic: "+topic + " value: "+message);
        Log.d(TAG, message.toString());
        if (message.toString().equals("")){return;}

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/tts/request")){
            if(speakOut(message.toString())) publish("comm/tts/request", "");
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/tts/stop")){
            if(isTrue(message.toString()) == 1){
                if(speakStop())publish("comm/tts/stop", "");
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/display/brightness")){
            if(isNumber(message.toString())){
                if(setBrightness(Integer.parseInt(message.toString()))){
                    publish("comm/display/brightness", "");
                    publish("info/display/brightness", message.toString());
                    publish("info/display/mode", "manual");
                }
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/display/mode")){
            int num = isTrue(message.toString());
            if(num==1 || num==2){
                if(setBrightnessMode(num==1 ? "auto" : "manual" )){
                    publish("comm/display/mode", "");
                    publish("info/display/mode", num==1 ? "auto" : "manual");
                }
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/display/timeOff")){
            if(isNumber(message.toString())){
                if(setTimeScreenOff(Integer.parseInt(message.toString()))) publish("comm/display/timeOff", "");
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/display/toWake")){
            int num = isTrue(message.toString());
            if(num==1 || num==2){
//                set(num);
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/call/number")){
            if(isNumber(message.toString())){
                if (setCall(message.toString())) publish("comm/call/number", "");
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/call/end")){
            if(isTrue(message.toString())==1){
                if(disconnectCall()) publish("comm/call/end", "");
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/other/home")){
            if(isTrue(message.toString())==1){
                if(setHome()) publish("comm/other/home", "");
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/other/openURL")){
            if(openURL(message.toString())) publish("comm/other/openURL", "");
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/other/openURL")){
            if(openURL(message.toString())) publish("comm/other/openURL", "");
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/display/turnOnOff")){
            if(isTrue(message.toString())==1){
                turnOnScreen();
                publish("comm/display/turnOnOff", "");
            }else if(isTrue(message.toString())==2){
                turnOffScreen();
                publish("comm/display/turnOnOff", "");
            }
        }

        if (topic.equals(clientId + "/" + mqttDevice +"/comm/other/vibrate")){
            if(isNumber(message.toString())){
                if(vibrate(Integer.parseInt(message.toString()))) publish("comm/other/vibrate", "");
            }
        }

        if (topic.contains(clientId + "/" + mqttDevice + "/comm/audio/")){
//            Log.i(TAG,"TOPIC : "+topic);
            String key = topic.replaceAll(clientId + "/" + mqttDevice + "/comm/audio/", "");
//            Log.i(TAG,key);
            if(isNumber(message.toString())){
                if(setVolume(Integer.parseInt(message.toString()),key)) {
                    publish("comm/audio/"+key, "");
                    publish("info/audio/"+key, message.toString());
                }
            }
        }


//        if (topic.equals(clientId + "/" + mqttDevice +"/comm/notification/create")){
//            setNoti(message.toString());
//        }
//        if (topic.equals(clientId + "/" + mqttDevice +"/comm/notification/delete")){
//            notificationDel(message.toString());
//        }
//        if (topic.equals(clientId + "/" + mqttDevice +"/comm/notification/alert")){
//            alert(message.toString());
//        }

    }

    private int isTrue(String message){
        String mes = message.toLowerCase();
        int res = 0;
        if(mes.equals("true")
                ||mes.equals("1")
                ||mes.equals("auto")
                ||mes.equals("on")
                ){
            res = 1;
        }else if (mes.equals("false")
                ||mes.equals("0")
                ||mes.equals("manual")
                ||mes.equals("off")
                ){
            res = 2;
        }
        return res;
    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }
}
