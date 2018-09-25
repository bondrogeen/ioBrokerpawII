package ru.codedevice.iobrokerpawii;

import android.app.ActionBar;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    String TAG = "MainActivity";
    Intent intentService;
    SharedPreferences settings;
    String connection_list;
    Menu menu;
    boolean isRun;

    private RecyclerView mRecyclerView;
    private RecycleListAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManadger;
    ArrayList<RecycleItem> mExampleList;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createList();
        buildRecyclerView();

        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        final FloatingActionButton fab = findViewById(R.id.fab);

        initSettings();
        noSleep();
        if (getIntent().getStringExtra("turnOnScreen") != null) setHome();

        if(connection_list.equals("0")){
            Log.i(TAG,"connection_list == 0");
            isRun = isMyServiceRunning(WebServerService.class);
            intentService = new Intent(this,WebServerService.class);
        }else{
            Log.i(TAG,"connection_list != 0");
            isRun = isMyServiceRunning(MQTTService.class);
            intentService = new Intent(this,MQTTService.class);
        }

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"Click button");

                mExampleList.add(new RecycleItem(R.drawable.ic_menu_camera,"Line ", "Line "));
                mAdapter.notifyItemInserted(2);


            }
        });
        DrawerLayout drawer = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

    }

    private void buildRecyclerView() {
        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManadger = new LinearLayoutManager(this);
        mAdapter = new RecycleListAdapter(mExampleList);
        mRecyclerView.setLayoutManager(mLayoutManadger);
        mRecyclerView.setAdapter(mAdapter);

        mAdapter.setOnItemClickListener(new RecycleListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                mExampleList.get(position).changeText1(",m,m,");
                mAdapter.notifyItemChanged(position);
            }
        });
    }


    public void createList(){
        mExampleList = new ArrayList<>();
        mExampleList.add(new RecycleItem(R.drawable.ic_fan,"Line 1", "Line 2"));
        mExampleList.add(new RecycleItem(R.drawable.ic_light,"Line 3", "Line 4"));
        mExampleList.add(new RecycleItem(R.drawable.ic_menu_camera,"Line 5", "Line 6"));

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
        if (id == R.id.nav_connection) {

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
                    item.setTitle("Disconnection");
                }else{
                    stopService(intentService);
                    item.setTitle("Connection");
                }
            }else{
//                final Snackbar snackbar = Snackbar.make(view, "Coordinator NTB", Snackbar.LENGTH_SHORT);
//                snackbar.getView().setBackgroundColor(getResources().getColor(R.color.main_bg));
//                ((TextView) snackbar.getView().findViewById(R.id.snackbar_text))
//                        .setTextColor(getResources().getColor(R.color.item_text));
//                snackbar.show();
            }
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this,SettingsActivity.class));
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

    public String hasConnection() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()) {
            return "wifi";
        }
        wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (wifiInfo != null && wifiInfo.isConnected()) {
            return "mobile";
        }
        return "false";
    }

    private void noSleep() {
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

    public void initSettings() {
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        connection_list = settings.getString("connection_list", "");
        Log.i(TAG, connection_list);
    }
}
