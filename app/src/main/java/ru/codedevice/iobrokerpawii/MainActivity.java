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
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Random;

import devlight.io.library.ntb.NavigationTabBar;

public class MainActivity extends AppCompatActivity implements NavigationTabBar.OnTabBarSelectedIndexListener, ViewPager.OnPageChangeListener {

    String TAG = "MainActivity";
    Intent intentService;
    SharedPreferences settings;
    String connection_list;
    Menu menu;

    boolean isRun;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initSettings();
        setContentView(R.layout.activity_horizontal_coordinator_ntb);
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

        initUI();

        ImageView settings = findViewById(R.id.toolbar_settings);
        settings.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplication(), SettingsActivity.class));
            }
        });

    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
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
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initUI() {
        final ViewPager viewPager = findViewById(R.id.vp_horizontal_ntb);
        viewPager.setAdapter(new PagerAdapter() {

            @Override
            public int getCount() {
                return 3;
            }

            @Override
            public boolean isViewFromObject(final View view, final Object object) {
                return view.equals(object);
            }

            @Override
            public void destroyItem(final View container, final int position, final Object object) {
                ((ViewPager) container).removeView((View) object);
            }

            @Override
            public Object instantiateItem(final ViewGroup container, final int position) {
                final View view = LayoutInflater.from(
                        getBaseContext()).inflate(R.layout.item_vp_list, null, false);

                final RecyclerView recyclerView = view.findViewById(R.id.rv);
                recyclerView.setHasFixedSize(true);
                recyclerView.setLayoutManager(new LinearLayoutManager(
                                getBaseContext(), LinearLayoutManager.VERTICAL, false
                        )
                );
                recyclerView.setAdapter(new RecycleAdapter());

                container.addView(view);
                return view;
            }
        });

        final String[] colors = getResources().getStringArray(R.array.default_preview);

        final NavigationTabBar navigationTabBar = findViewById(R.id.ntb_horizontal);
        final ArrayList<NavigationTabBar.Model> models = new ArrayList<>();
        models.add(
                new NavigationTabBar.Model.Builder(
                        getResources().getDrawable(R.drawable.ic_light),
                        Color.parseColor(colors[0]))
                        .title("Heart")
                        .build()
        );
        models.add(
                new NavigationTabBar.Model.Builder(
                        getResources().getDrawable(R.drawable.ic_fan),
                        Color.parseColor(colors[1]))
                        .title("Cup")
                        .build()
        );
        models.add(
                new NavigationTabBar.Model.Builder(
                        getResources().getDrawable(R.drawable.ic_menu_camera),
                        Color.parseColor(colors[2]))
                        .title("Diploma")
                        .build()
        );

        navigationTabBar.setModels(models);
        navigationTabBar.setViewPager(viewPager, 2);
        navigationTabBar.setBehaviorEnabled(true);
        navigationTabBar.setOnTabBarSelectedIndexListener(this);
        navigationTabBar.setOnPageChangeListener(this);

        final FloatingActionButton fab = findViewById(R.id.fab);

        Log.i(TAG,"isRun : " + isRun);

        if (!isRun) {
            fab.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
            fab.setImageResource(R.drawable.ic_play_arrow_black_24dp);
        }else{
            fab.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
            fab.setImageResource(R.drawable.ic_pause_black_24dp);
        }

        fab.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View v) {

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
                    final Snackbar snackbar = Snackbar.make(navigationTabBar, "Coordinator NTB", Snackbar.LENGTH_SHORT);
                    snackbar.getView().setBackgroundColor(getResources().getColor(R.color.main_bg));
                    ((TextView) snackbar.getView().findViewById(R.id.snackbar_text))
                            .setTextColor(getResources().getColor(R.color.item_text));
                    snackbar.show();
                }
            }
        });

    }


    public class RecycleAdapter extends RecyclerView.Adapter<RecycleAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            final View view = LayoutInflater.from(getBaseContext()).inflate(R.layout.item_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {
            holder.txt.setText(String.format("Navigation Item #%d", position));
        }

        @Override
        public int getItemCount() {
            return 10;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            public TextView txt;

            public ViewHolder(final View itemView) {
                super(itemView);
                txt = itemView.findViewById(R.id.txt_vp_item_list);
            }
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public void onStartTabSelected(final NavigationTabBar.Model model, final int index) {
    }

    @Override
    public void onEndTabSelected(final NavigationTabBar.Model model, final int index) {
        model.hideBadge();
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

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
