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

import java.util.ArrayList;
import java.util.Collections;

import uk.me.berndporr.iirj.Butterworth;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView stepsTv;
    private Button startBtn, stopBtn, resetBtn;

    private SensorManager sensorManager;
    private Sensor sensor;

    //for storing accelerometer data
    private double[] acceleration = new double[3];
    private double[] linear_acceleration = new double[3];
    private double[] gravity = new double[3];
    private ArrayList<Float> data = new ArrayList<>();

    private int stepsCount = 0;

    private double threshold = 0.35;
    float magnitude = 0f;

    boolean isActive = false;
    long time_start;
    long time_end;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stepsTv = findViewById(R.id.stepsTv);
        startBtn = findViewById(R.id.startBtn);
        stopBtn = findViewById(R.id.stopBtn);
        resetBtn = findViewById(R.id.resetBtn);

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

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });

    }
    private void start() {
        isActive = true;
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        time_start = System.currentTimeMillis();
    }

    private void stop() {
        isActive = false;
        time_end = System.currentTimeMillis();
    }

    private void reset(){
        stepsCount = 0;
        stepsTv.setText(String.valueOf(stepsCount));
        data.clear();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(isActive){
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

            //magnitude array: invariant to phone orientation
            magnitude = (float) Math.sqrt(Math.pow(acceleration[0], 2) + Math.pow(acceleration[1], 2) + Math.pow(acceleration[2], 2));
            data.add(magnitude);
        }

        else {
            System.out.println("data to send");
            System.out.println(data);

            long time_diff = (time_end-time_start)/1000;
            stepCounter(data, time_diff);

            data.clear();
            sensorManager.unregisterListener(this);
        }


    }

    public void stepCounter(ArrayList<Float> data, long time_diff){
        //usually effective frequency of walking is 0-2Hz
        //get all values between that range
        // remove noises
        double[] data_arr = data.stream().mapToDouble(Float::doubleValue).toArray(); //via method reference
        int fs = (int) (data.size() / time_diff);
        System.out.println("sampling rate");
        System.out.println(fs);

        double[] filtered = bandPassFilter(data_arr,0.1f,2.0f,fs,5);

        ArrayList<Double> filtered_linear_acceleration = new ArrayList<>();
        for (int i = 1; i < filtered.length-1; i++) {
            filtered_linear_acceleration.add(filtered[i]);
        }
        System.out.println("filtered data");
        System.out.println(filtered_linear_acceleration);

        // normalize data from 0 to 1
        ArrayList<Double> normalized_arr = normalize(filtered_linear_acceleration);

        System.out.println("norm data");
        System.out.println(normalized_arr);

        int stepsCount = peakDetection(normalized_arr, threshold,fs);
        stepsTv.setText(String.valueOf(stepsCount));
        System.out.println("steps: " + stepsCount);
    }

    private ArrayList<Double> normalize(@NonNull ArrayList<Double> filtered) {
        double maximum = Collections.max(filtered);
        double minimum = Collections.min(filtered);

        ArrayList<Double> ans = new ArrayList<>();
        for (int i = 1; i < filtered.size()-1; i++) {
            ans.add((filtered.get(i) - minimum) / (maximum - minimum));
        }
        return ans;
    }

    private int peakDetection(@NonNull ArrayList<Double> linear_acceleration, double threshold, int fs) {

        ArrayList<Integer> peaksDetected = new ArrayList<>();

        //check i-1, i, i+1
        //if i is greater than both then it is a peak
        //check if  value at that index is > threshold
        //add index i into a list and value at i in another list

        for(int i =1; i< linear_acceleration.size() -1; i++){
            if (linear_acceleration.get(i) > linear_acceleration.get(i - 1) && linear_acceleration.get(i) > linear_acceleration.get(i + 1)
                    && linear_acceleration.get(i) > threshold) {
                peaksDetected.add(i);
            }

        }

        //distance threshold
        for(int i = 0; i<peaksDetected.size()-1; i++){
            if((peaksDetected.get(i+1) - peaksDetected.get(i)) > (int)(fs/4)-1){
                stepsCount++;   //peak detected -> increase the number of steps by 1
            }
        }
        System.out.println(stepsCount);

        // get values at peak
        double[] peaksValue = new double[peaksDetected.size()];
        for(int i =0; i< peaksDetected.size(); i++){
            peaksValue[i] = (i);
        }
        return stepsCount;
    }

    double[] bandPassFilter(@NonNull double[] linear_acceleration, float low_cut, float high_cut, int fs, int order){
        // sampling freq = no. of samples/signal duration
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