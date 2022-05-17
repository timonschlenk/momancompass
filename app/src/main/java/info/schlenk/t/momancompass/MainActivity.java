package info.schlenk.t.momancompass;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    public static final int DEFAULT_LOCATION_REQUEST_INTERVAL = 5;
    public static final int MAX_LOCATION_REQUEST_INTERVAL = 2;
    private static final int PERMISSIONS_FINE_LOCATION = 99;


    TextView tv_lat, tv_lon, tv_bearing;
    ImageView iv_compass, iv_momancompass;

    FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;
    LocationCallback locationCallback;

    SensorManager sensorManager;
    Sensor rotationVector, accelerometer, magnetometer;

    int azimuth, momanAzimuth, bearingAngle;
    float[] rotationMatrix, orientation, lastAccelerometer, lastMagnetometer;
    double[] locationMatrix, positionBase;
    boolean haveSensor1, haveSensor2, lastAccelerometerSet, lastMagnetometerSet;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        locationMatrix = new double[]{0, 0};
        bearingAngle = 0;
        positionBase = new double[]{46.1161178, -64.7886417};

        iv_momancompass = findViewById(R.id.iv_momancompass);

        rotationMatrix = new float[9];
        orientation = new float[9];
        lastAccelerometer = new float[3];
        lastMagnetometer = new float[3];
        haveSensor1 = false;
        haveSensor2 = false;
        lastAccelerometerSet = false;
        lastMagnetomeeterSet = false;

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000 * DEFAULT_LOCATION_REQUEST_INTERVAL);
        locationRequest.setFastestInterval(1000 * MAX_LOCATION_REQUEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                updateUIValues(locationResult.getLastLocation());
            }
        };

        start();
        updateGPS();
        startLocationUpdates();

    }

    @Override
    protected void onResume() {
        super.onResume();
        start();
        updateGPS();
        startLocationUpdates();
    }

    private void start() {
        if(sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null){
            if(sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null || sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null){
                noSensorAlert();
            } else {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

                haveSensor1 = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
                haveSensor2 = sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
            }
        } else {
            rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            haveSensor1 = sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void noSensorAlert() {
        Toast.makeText(this, "Doesn't support Compass", Toast.LENGTH_SHORT).show();
    }

    private void stop(){
        if(haveSensor1 && haveSensor2){
            sensorManager.unregisterListener(this, accelerometer);
            sensorManager.unregisterListener(this, magnetometer);
        } else if (haveSensor1){
            sensorManager.unregisterListener(this, rotationVector);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        stop();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_FINE_LOCATION){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                updateGPS();
            } else {
                Toast.makeText(this, "Please give Location Permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void updateGPS(){
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    updateUIValues(location);
                }
            });
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_FINE_LOCATION);
            }
        }
    }

    private void updateUIValues(Location location) {
        locationMatrix[0]=location.getLatitude();
        locationMatrix[1]=location.getLongitude();

        calculateBearingAngle(locationMatrix, positionBase);
    }

    public void calculateBearingAngle(double[] pos1, double[] pos2){
        double deltaY = Math.toRadians(pos2[1] - pos1[1]);
        double X1 = Math.toRadians(pos1[0]);
        double X2 = Math.toRadians(pos2[0]);

        double bearingAngleRad = Math.atan2( Math.sin(deltaY) * Math.cos(X2) , Math.cos(X1) * Math.sin(X2) - Math.sin(X1) * Math.cos(X2) * Math.cos(deltaY) );
        bearingAngle = (int) (bearingAngleRad*180/Math.PI + 360) % 360;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){
            SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values);
            azimuth = (int) ((Math.toDegrees(SensorManager.getOrientation(rotationMatrix, orientation)[0])+360)%360);
            momanAzimuth = (int) ((Math.toDegrees(SensorManager.getOrientation(rotationMatrix, orientation)[0])+360)%360-bearingAngle);
        }

        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            System.arraycopy(sensorEvent.values, 0, lastAccelerometer, 0, sensorEvent.values.length);
            lastAccelerometerSet = true;
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            System.arraycopy(sensorEvent.values, 0, lastMagnetometer, 0, sensorEvent.values.length);
            lastMagnetometerSet = true;
        }

        if (lastAccelerometerSet && lastMagnetometerSet){
            SensorManager.getRotationMatrix(rotationMatrix,null, lastAccelerometer, lastMagnetometer);
            SensorManager.getOrientation(rotationMatrix, orientation);
            momanAzimuth = (int) ((Math.toDegrees(SensorManager.getOrientation(rotationMatrix, orientation)[0])+360)%360-bearingAngle);
        }

        iv_momancompass.setRotation(-momanAzimuth);


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}