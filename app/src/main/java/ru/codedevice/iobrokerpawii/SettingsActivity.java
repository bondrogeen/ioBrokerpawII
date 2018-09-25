package ru.codedevice.iobrokerpawii;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;

import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof RingtonePreference) {

                if (TextUtils.isEmpty(stringValue)) {
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        preference.setSummary(null);
                    } else {

                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
//        getListView().setBackground(getResources().getDrawable(R.drawable.bg2));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {

            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || EventPreferenceFragment.class.getName().equals(fragmentName)
                || ConnectionPreferenceFragment.class.getName().equals(fragmentName)
                || SensorsPreferenceFragment.class.getName().equals(fragmentName);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("example_text"));
            bindPreferenceSummaryToValue(findPreference("example_list"));
            bindPreferenceSummaryToValue(findPreference("sync_frequency"));
            bindPreferenceSummaryToValue(findPreference("general_notifications_ringtone"));
            bindPreferenceSummaryToValue(findPreference("general_notifications_time_on"));
            bindPreferenceSummaryToValue(findPreference("general_notifications_time_off"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class ConnectionPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener{
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
            newMenu();

        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        private void newMenu(){
            PreferenceScreen rootScreen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(rootScreen);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String storedPreference = pref.getString("connection_list","0");

            ListPreference lp = new ListPreference(getActivity());
            lp.setEntries(new CharSequence[]{"Web server", "MQTT"});
            lp.setEntryValues(new CharSequence[]{"0","1"});
            lp.setDefaultValue("0");
            lp.setTitle("Type connection");
            lp.setSummary(storedPreference.equals("0") ? "Web server" : "MQTT");
            lp.setDialogTitle("Select connection type");
            lp.setKey("connection_list");
            rootScreen.addPreference(lp);

            findPreference("connection_list").setOnPreferenceChangeListener(this);

//            bindPreferenceSummaryToValue(findPreference("connection_list"));

            if(storedPreference.equals("0")){
                rootScreen.addPreference(newEditText(getActivity(),"connection_web_server_ip", "Server", pref.getString("connection_web_server_ip","192.168.1.10"),false));
                findPreference("connection_web_server_ip").setOnPreferenceChangeListener(this);
                rootScreen.addPreference(newEditText(getActivity(),"connection_web_server_port", "Port", pref.getString("connection_web_server_port","8080"),false));
                findPreference("connection_web_server_port").setOnPreferenceChangeListener(this);
            }else{
                rootScreen.addPreference(newEditText(getActivity(),"connection_mqtt_ip", "Server", pref.getString("connection_mqtt_ip","mqtt.com"),false));
                findPreference("connection_mqtt_ip").setOnPreferenceChangeListener(this);
                rootScreen.addPreference(newEditText(getActivity(),"connection_mqtt_port", "Port", pref.getString("connection_mqtt_port","1883"),false));
                findPreference("connection_mqtt_port").setOnPreferenceChangeListener(this);
                rootScreen.addPreference(newEditText(getActivity(),"connection_mqtt_login", "Login", pref.getString("connection_mqtt_login","admin"),false));
                findPreference("connection_mqtt_login").setOnPreferenceChangeListener(this);
                rootScreen.addPreference(newEditText(getActivity(),"connection_mqtt_pass", "Password", pref.getString("connection_mqtt_pass","pass"),true));
                findPreference("connection_mqtt_pass").setOnPreferenceChangeListener(this);
                findPreference("connection_mqtt_pass").setOnPreferenceClickListener(this);
                rootScreen.addPreference(newEditText(getActivity(),"connection_mqtt_device", "Device", pref.getString("connection_mqtt_device",Build.MODEL.replaceAll("\\s+","")),false));
                findPreference("connection_mqtt_device").setOnPreferenceChangeListener(this);
            }
        }

        private SwitchPreference newSwitch(Context cont, String key, String title, String summary){
            SwitchPreference newElem = new SwitchPreference(cont);
            newElem.setKey(key);
            newElem.setTitle(title);
            newElem.setSummary(summary);
            return  newElem;
        }

        private EditTextPreference newEditText(Context cont, String key, String title, String summary, boolean pass){
            EditTextPreference newElem = new EditTextPreference(cont);
            newElem.setKey(key);
            newElem.setTitle(title);
            newElem.setDefaultValue(summary);
            newElem.setDialogTitle("Input");
            newElem.setSummary(pass ? summary.replaceAll(".","*") : summary);
            return  newElem;
        }

        private ListPreference newList(Context cont,String key,String title,String dialogTitle,String devValue,CharSequence[] entries,CharSequence[] entryValues){
            ListPreference lp = new ListPreference(cont);
            lp.setEntries(entries);
            lp.setEntryValues(entryValues);
            lp.setDefaultValue(devValue);
            lp.setTitle(title);
            lp.setSummary(devValue.equals("0") ? "Web server" : "MQTT");
            lp.setDialogTitle(dialogTitle);
            lp.setKey(key);
            return  lp;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            Log.i("TAG","onClick");

            if(preference instanceof EditTextPreference && preference.getKey().equals("connection_mqtt_pass")){
                EditTextPreference pref=(EditTextPreference)preference;
                EditText field=pref.getEditText();
                field.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                field.setTransformationMethod(new PasswordTransformationMethod());
//                field.requestFocus();
            }

            return true;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            String stringValue = o.toString();
            Log.i("TAG","onChange");
            Log.i("TAG",preference.getKey());
            Log.i("TAG", String.valueOf(o.toString()));

            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                listPreference.setValue(stringValue);
                Log.i("TAG",listPreference.getValue());
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
            }else if(preference instanceof EditTextPreference){
                preference.setSummary(stringValue);
                ((EditTextPreference) preference).setText(stringValue);

            }

            newMenu();
            return false;
        }

    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class EventPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_event);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SensorsPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);

            PreferenceScreen rootScreen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(rootScreen);

            SensorManager sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
            assert sensorManager != null;
            List<Sensor> listSensor = sensorManager.getSensorList(Sensor.TYPE_ALL);

            for (int i = 0; i < listSensor.size(); i++) {
                Log.i("TAG", String.valueOf(listSensor.get(i).getName()));
                Log.i("TAG", String.valueOf(listSensor.get(i).getType()));
                Log.i("TAG", String.valueOf(listSensor.get(i).getVersion()));

                SwitchPreference newElem = new SwitchPreference(getActivity());
                PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
                Intent intent = new Intent(getActivity(),SensorActivity.class);

                switch (listSensor.get(i).getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        newElem.setKey("sensors_accelerometer");
                        newElem.setTitle("Accelerometer");
                        newElem.setSummary(listSensor.get(i).getName());
                        rootScreen.addPreference(newElem);

                        screen.setTitle("Accelerometer setup");
                        screen.setDependency("sensors_accelerometer");
                        intent.putExtra("sensorType", Sensor.TYPE_ACCELEROMETER);
                        screen.setIntent(intent);
                        rootScreen.addPreference(screen);
                        break;
                    case 3:
                        newElem.setKey("sensors_orientation");
                        newElem.setTitle("Orientation");
                        newElem.setSummary(listSensor.get(i).getName());
                        rootScreen.addPreference(newElem);

                        screen.setTitle("Orientation setup");
                        screen.setDependency("sensors_orientation");
                        intent.putExtra("sensorType", 3);
                        screen.setIntent(intent);
                        rootScreen.addPreference(screen);
                        break;
                    case Sensor.TYPE_LIGHT:
                        newElem.setKey("sensors_light");
                        newElem.setTitle("Light");
                        newElem.setSummary(listSensor.get(i).getName());
                        rootScreen.addPreference(newElem);

                        screen.setTitle("Light setup");
                        screen.setDependency("sensors_light");
                        intent.putExtra("sensorType", Sensor.TYPE_LIGHT);
                        screen.setIntent(intent);
                        rootScreen.addPreference(screen);
                        break;
                    case Sensor.TYPE_PROXIMITY:
                        newElem.setKey("sensors_proximity");
                        newElem.setTitle("Proximity");
                        newElem.setSummary(listSensor.get(i).getName());
                        rootScreen.addPreference(newElem);

                        screen.setTitle("Proximity setup");
                        screen.setDependency("sensors_proximity");
                        intent.putExtra("sensorType", Sensor.TYPE_PROXIMITY);
                        screen.setIntent(intent);
                        rootScreen.addPreference(screen);
                        break;
                    case Sensor.TYPE_PRESSURE:
                        newElem.setKey("sensors_pressure");
                        newElem.setTitle("Pressure");
                        newElem.setSummary(listSensor.get(i).getName());
                        rootScreen.addPreference(newElem);

                        screen.setTitle("Pressure setup");
                        screen.setDependency("sensors_pressure");
                        intent.putExtra("sensorType", Sensor.TYPE_PRESSURE);
                        screen.setIntent(intent);
                        rootScreen.addPreference(screen);
                        break;
                    case Sensor.TYPE_GYROSCOPE:
                        newElem.setKey("sensors_gyroscope");
                        newElem.setTitle("Gyroscope");
                        break;

                    case Sensor.TYPE_AMBIENT_TEMPERATURE:
                        newElem.setKey("sensors_temperature");
                        newElem.setTitle("Temperature");
                        newElem.setSummary(listSensor.get(i).getName());
                        rootScreen.addPreference(newElem);

                        screen.setTitle("Temperature setup");
                        screen.setDependency("sensors_temperature");
                        intent.putExtra("sensorType", Sensor.TYPE_AMBIENT_TEMPERATURE);
                        screen.setIntent(intent);
                        rootScreen.addPreference(screen);
                        break;
                    case Sensor.TYPE_RELATIVE_HUMIDITY:
                        newElem.setKey("sensors_humidity");
                        newElem.setTitle("Humidity");
                        newElem.setSummary(listSensor.get(i).getName());
                        rootScreen.addPreference(newElem);

                        screen.setTitle("Humidity setup");
                        screen.setDependency("sensors_humidity");
                        intent.putExtra("sensorType", Sensor.TYPE_RELATIVE_HUMIDITY);
                        screen.setIntent(intent);
                        rootScreen.addPreference(screen);
                        break;
                }
            }

//            findPreference("sensors_wifi").setOnPreferenceClickListener(this);


        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {

            Log.i("TAG","rrrrrrrr");
            return true;
        }
    }

}
