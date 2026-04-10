package com.javainstitute.parkfinder;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


public class ShakeDetector implements SensorEventListener {

    private static final float SHAKE_THRESHOLD_GRAVITY = 2.5f;
    private static final int   SHAKE_SLOP_TIME_MS      = 500;
    private static final int   SHAKE_COUNT_RESET_MS    = 3000;
    private static final int   SHAKES_REQUIRED         = 2;

    public interface OnShakeListener {
        void onShake();
    }

    private final OnShakeListener listener;
    private long  shakeTimestamp  = 0;
    private int   shakeCount      = 0;

    public ShakeDetector(OnShakeListener listener) {
        this.listener = listener;
    }

    public void start(SensorManager sensorManager) {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(
                    this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void stop(SensorManager sensorManager) {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];


        float gForce = (float) Math.sqrt(x * x + y * y + z * z)
                / SensorManager.GRAVITY_EARTH;

        if (gForce < SHAKE_THRESHOLD_GRAVITY) return;

        long now = System.currentTimeMillis();


        if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) return;


        if (shakeTimestamp + SHAKE_COUNT_RESET_MS < now) {
            shakeCount = 0;
        }

        shakeTimestamp = now;
        shakeCount++;

        if (shakeCount >= SHAKES_REQUIRED) {
            shakeCount = 0;
            listener.onShake();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}