package ru.codedevice.iobrokerpawii;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.NumberFormat;


public class SensorActivity extends AppCompatActivity implements SensorEventListener{

    private TextView sensorValue0, sensorValue1, sensorValue2;
    SensorManager SensorManager;
    Sensor sensor;

    LineGraphSeries<DataPoint> series0,series1,series2,series3;

    GraphView graph;
    private int valueX = 5;
    private int sensorType;

    private String TAG = "SensorActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        sensorType = getIntent().getIntExtra("sensorType",0);


        Log.i(TAG, String.valueOf(sensorType));
        Toolbar toolbar = findViewById(R.id.toolbar_sett);
        toolbar.setTitle(getNameTypeSensors(sensorType));
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        sensorValue0 = findViewById(R.id.sensorValue0);
        sensorValue1 = findViewById(R.id.sensorValue1);
        sensorValue2 = findViewById(R.id.sensorValue2);


        final FloatingActionButton fab = findViewById(R.id.fab);

        fab.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        fab.setImageResource(R.drawable.ic_add_black_24dp);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"Click button");
                fab.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
                fab.setImageResource(R.drawable.ic_pause_black_24dp);

            }
        });
        getScreenOrientation();

        SensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        assert SensorManager != null;
        sensor = SensorManager.getDefaultSensor(sensorType);

        createGraph(sensorType,getCountsOfDigits(sensor.getMaximumRange()));

        Log.i(TAG, "MAX: " + String.valueOf(sensor.getMaximumRange()));
        Log.i(TAG, "MAX: " + String.valueOf(getCountsOfDigits(sensor.getMaximumRange())));

        if(sensor != null){
            SensorManager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private int getCountsOfDigits(float number) {
        int num = Math.round(number);
        return String.valueOf(Math.abs(num)).length()-1;
    }

    private void createGraph(int type,int num){
        graph = (GraphView) findViewById(R.id.graph);
        series0 = new LineGraphSeries<>();
        series0.setColor(Color.RED);
        graph.addSeries(series0);

        if(getQtySensors(type) == 3) {
            series1 = new LineGraphSeries<>();
            series2 = new LineGraphSeries<>();
//            series3 = new LineGraphSeries<>();

            series1.setColor(Color.GREEN);
            series2.setColor(Color.BLUE);
//            series3.setColor(Color.YELLOW);

            graph.addSeries(series1);
            graph.addSeries(series2);
//            graph.addSeries(series3);
        }

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(40);
//        graph.getViewport().setMaxYAxisSize(1);

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumFractionDigits(1);
        nf.setMinimumIntegerDigits(num);

        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(nf, nf));
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);

    }

    private boolean getScreenOrientation(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            return true;
        }
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return true;
        }else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            return true;
        }else{
            return false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == sensorType){

            if(getQtySensors(sensorType)==3) {
                series0.appendData(new DataPoint(valueX, event.values[0]), true, 40);
                sensorValue0.setText("X: " + event.values[0]);
                series1.appendData(new DataPoint(valueX, event.values[1]), true, 40);
                sensorValue1.setText("Y: " + event.values[1]);
                series2.appendData(new DataPoint(valueX, event.values[2]), true, 40);
                sensorValue2.setText("Z: " + event.values[2]);
            }else{
                series0.appendData(new DataPoint(valueX, event.values[0]), true, 40);
                sensorValue0.setText("Value: " + event.values[0]);
            }
            valueX ++;

        }
    }

    protected void onResume() {
        super.onResume();
        SensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        SensorManager.unregisterListener(this);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private String getNameTypeSensors(int i){
        String name = "";
        switch (i) {
            case Sensor.TYPE_ACCELEROMETER:
                name = "Accelerometer";
                break;
            case 3:
                name = "Orientation";
                break;
            case Sensor.TYPE_LIGHT:
                name = "Light";
                break;
            case Sensor.TYPE_PROXIMITY:
                name = "Proximity";
                break;
            case Sensor.TYPE_PRESSURE:
                name = "Pressure";
                break;
            case Sensor.TYPE_GYROSCOPE:
                name = "Gyroscope";
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                name = "Temperature";
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                name = "Humidity";
                break;
        }
        return name;
    }

private int getQtySensors(int i){
        int qty = 0;
        switch (i) {
            case Sensor.TYPE_ACCELEROMETER:
                qty = 3;
                break;
            case 3:
                qty = 3;
                break;
            case Sensor.TYPE_LIGHT:
                qty = 1;
                break;
            case Sensor.TYPE_PROXIMITY:
                qty = 1;
                break;
            case Sensor.TYPE_PRESSURE:
                qty = 1;
                break;
            case Sensor.TYPE_GYROSCOPE:
                qty = 3;
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                qty = 1;
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                qty = 1;
                break;
        }
        return qty;
    }
}
