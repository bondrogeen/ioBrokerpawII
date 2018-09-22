package ru.codedevice.iobrokerpawii;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    String TAG = "MainActivity";
    Intent intentService;
    SharedPreferences settings;
    String connection_list;

    boolean isRun;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initSettings();

        if(connection_list.equals("0")){
            isRun = isMyServiceRunning(WebServerService.class);
            intentService = new Intent(this,WebServerService.class);
        }else{
            isRun = isMyServiceRunning(MQTTService.class);
            intentService = new Intent(this,MQTTService.class);
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final FloatingActionButton fab = findViewById(R.id.fab);
        if (!isRun) {
            fab.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
            fab.setImageResource(R.drawable.ic_play_arrow_black_24dp);
        }else{
            fab.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
            fab.setImageResource(R.drawable.ic_pause_black_24dp);
        }

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"Click button");

                connection_list = settings.getString("connection_list", "");

                if(connection_list.equals("0")){
                    isRun = isMyServiceRunning(WebServerService.class);
                }else{
                    isRun = isMyServiceRunning(MQTTService.class);
                }

                if((hasConnection().equals("wifi") && connection_list.equals("0"))
                        || (!hasConnection().equals("false") && connection_list.equals("1")) || isRun){
                    if (!isRun) {
                        intentService.putExtra("init","start");
                        startService(intentService);
                        fab.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
                        fab.setImageResource(R.drawable.ic_pause_black_24dp);
                    }else{
                        stopService(intentService);
                        fab.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
                        fab.setImageResource(R.drawable.ic_play_arrow_black_24dp);
                    }
                }else{
                    Snackbar mSnackbar = Snackbar.make(view, "No connection to WIFI", Snackbar.LENGTH_LONG)
                            .setAction("Action", null);
                    mSnackbar.show();
                }


            }
        });

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        Intent intent = new Intent(this, SensorActivity.class);
        intent.putExtra("sensorType", 5);
//        startActivity(intent);

        noSleep();
        if(getIntent().getStringExtra("turnOnScreen") != null) setHome();

    }

    private void noSleep(){
        int flags = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        getWindow().addFlags(flags);
    }

    private boolean setHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        return true;
    }

    public void initSettings(){
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        connection_list = settings.getString("connection_list", "");
        Log.i(TAG,connection_list);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_camera) {

        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    public String hasConnection()    {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()){
            return "wifi";
        }
        wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (wifiInfo != null && wifiInfo.isConnected()){
            return "mobile";
        }
        return "false";
    }

}
