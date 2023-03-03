package com.example.steptracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;

import uk.me.berndporr.iirj.Butterworth;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView stepsTv;
    private Button startBtn, stopBtn;

    private SensorManager sensorManager;
    private Sensor sensor;

    //for storing accelerometer data
    private double[] acceleration = new double[3];
    private double[] linear_acceleration = new double[3];
    private double[] gravity = new double[3];

    private int stepsCount = 0;

    private double threshold = 0.4;

    Butterworth butterworth = new Butterworth();
    double[] filtered_linear_acceleration = new double[linear_acceleration.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stepsTv = findViewById(R.id.stepsTv);
        startBtn = findViewById(R.id.startBtn);
        stopBtn = findViewById(R.id.stopBtn);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();

            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });

        // usually effective frequency of walking is 0-2Hz
        //get all values between that range
        bandPassFilter(linear_acceleration,0.1f,2.0f,50,5);

    }

    public void start() {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // accelerometer values
        acceleration[0] = sensorEvent.values[0];
        acceleration[1] = sensorEvent.values[1];
        acceleration[2] = sensorEvent.values[2];

        final float alpha = 0.8f;

        //acc due to gravity
        gravity[0] = alpha * gravity[0] + (1 - alpha) * sensorEvent.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * sensorEvent.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * sensorEvent.values[2];

        // linear acc = acc - gravity
        linear_acceleration[0] = acceleration[0] - gravity[0];
        linear_acceleration[1] = acceleration[1] - gravity[1];
        linear_acceleration[2] = acceleration[2] - gravity[2];

        // remove noises
        for(int i =0; i<linear_acceleration.length; i++){
            filtered_linear_acceleration[i] = butterworth.filter(linear_acceleration[i]);
        }
        // normalize data from 0 to 1
        normalize(filtered_linear_acceleration);

        peakDetection(linear_acceleration, threshold );

        stepsTv.setText(String.valueOf(stepsCount));
    }

    private double normalize(@NonNull double[] filtered) {
        double maximum = Math.max(Math.abs(filtered[0]), Math.max(Math.abs(filtered[1]), Math.abs(filtered[2])));
        double minimum = Math.min(Math.abs(filtered[0]), Math.max(Math.abs(filtered[1]), Math.abs(filtered[2])));

        double ans = 0d;
        for (int i = 0; i < filtered.length; i++) {
            ans = (filtered[i] - minimum) / (maximum - minimum);
        }
        return ans;
    }

    private int peakDetection(@NonNull double [] linear_acceleration, double threshold) {

        ArrayList<Double> peaksDetected = new ArrayList<>();

        // check i-1, i, i+1
        //if i is greater than both then it is a peak
        //check if  value at that index is > 0.4
        //add index i into a list and value at i in another list

        for(int i =1; i< linear_acceleration.length -1; i++){
            if (linear_acceleration[i] > linear_acceleration[i-1] && linear_acceleration[i] > linear_acceleration[i+1] && linear_acceleration[i] > threshold) {
                peaksDetected.add(linear_acceleration[i]);
                // peak detected - increase the number of steps by 1
                stepsCount ++ ;
            }

        }
        // get values at peak
        double[] peaksValue = new double[peaksDetected.size()];
        for(int i =0; i< peaksDetected.size(); i++){
            peaksValue[i] = peaksDetected.get(i);
        }

        return stepsCount;
    }

    double[] bandPassFilter(@NonNull double[] linear_acceleration, float low_cut, float high_cut, int fs, int order){
        // sampling freq = 50Hz
        fs = 50;
        order = 5;
        for (int i = 0; i < linear_acceleration.length; i++) {
            double centreFreq = (high_cut + low_cut) / 2.0;
            double width = Math.abs(high_cut - low_cut);

            Butterworth butterworth = new Butterworth();
            butterworth.bandPass(order, fs, centreFreq, width);
            linear_acceleration[i] = (float) butterworth.filter(linear_acceleration[i]);
        }
        return linear_acceleration;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}