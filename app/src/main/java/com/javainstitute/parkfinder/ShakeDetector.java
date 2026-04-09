package com.javainstitute.parkfinder;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Detects shake gestures via the accelerometer.
 * Usage:
 *   shakeDetector = new ShakeDetector(() -> { /* on shake *\/ });
 *   shakeDetector.start(sensorManager);  // in onResume
 *   shakeDetector.stop(sensorManager);   // in onPause
 */
public class ShakeDetector implements SensorEventListener {

    // Tune these to adjust sensitivity
    private static final float SHAKE_THRESHOLD_GRAVITY = 2.5f; // G-force threshold
    private static final int   SHAKE_SLOP_TIME_MS      = 500;  // Min ms between two shakes
    private static final int   SHAKE_COUNT_RESET_MS    = 3000; // Window to count shakes
    private static final int   SHAKES_REQUIRED         = 2;    // Shakes needed to trigger

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

        // Compute net G-force (gravity is ~9.8 m/s², SensorManager.GRAVITY_EARTH)
        float gForce = (float) Math.sqrt(x * x + y * y + z * z)
                / SensorManager.GRAVITY_EARTH;

        if (gForce < SHAKE_THRESHOLD_GRAVITY) return; // Not a shake

        long now = System.currentTimeMillis();

        // Ignore if within slop window (prevents a single shake counting multiple times)
        if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) return;

        // Reset counter if outside the count window
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
        // Not needed
    }
}