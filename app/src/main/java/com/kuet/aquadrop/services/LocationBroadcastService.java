package com.kuet.aquadrop.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.activities.LiveTrackingActivity;
import com.kuet.aquadrop.models.LiveTelemetry;
import com.kuet.aquadrop.utils.EtaCalculator;
import com.kuet.aquadrop.utils.FirebaseManager;

import java.util.Locale;

/**
 * Android Foreground Service capturing driver GPS telemetry every 4 seconds
 * and synchronizing live position, distance, and ETA to Firebase Realtime Database.
 */
public class LocationBroadcastService extends Service {

    public static final String CHANNEL_ID = "AquaDrop_Location_Channel";
    public static final int NOTIFICATION_ID = 2001;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private String activeOrderId;
    private double buyerLat = 22.8995;
    private double buyerLng = 89.5020;
    private int floorNumber = 1;
    private String locationName = "KUET Campus";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            activeOrderId = intent.getStringExtra("orderId");
            buyerLat = intent.getDoubleExtra("buyerLat", 22.8995);
            buyerLng = intent.getDoubleExtra("buyerLng", 89.5020);
            floorNumber = intent.getIntExtra("floorNumber", 1);
            if (intent.hasExtra("locationName")) {
                locationName = intent.getStringExtra("locationName");
            }
        }

        Notification notification = buildForegroundNotification("Broadcasting Live Delivery Location to " + locationName);
        startForeground(NOTIFICATION_ID, notification);

        startLocationUpdates();
        return START_STICKY;
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Capture device GPS coordinates every 4 seconds (4000ms) with high accuracy
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(3000)
                .setMinUpdateDistanceMeters(2.0f)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || locationResult.getLastLocation() == null) {
                    return;
                }

                Location location = locationResult.getLastLocation();
                double driverLat = location.getLatitude();
                double driverLng = location.getLongitude();

                if (activeOrderId != null) {
                    double distMeters = EtaCalculator.distanceMeters(driverLat, driverLng, buyerLat, buyerLng);
                    double distKm = distMeters / 1000.0;
                    int etaMins = EtaCalculator.calculateEtaMinutes(distKm, floorNumber, 0);

                    LiveTelemetry telemetry = new LiveTelemetry(
                            activeOrderId,
                            driverLat,
                            driverLng,
                            etaMins,
                            distMeters,
                            System.currentTimeMillis()
                    );

                    // Write to Firebase Realtime Database at active_deliveries/{orderId}
                    FirebaseManager.getInstance().getTelemetryRef(activeOrderId).setValue(telemetry);

                    // Update notification with live distance and ETA
                    String statusText = String.format(Locale.getDefault(), "En route to %s • ETA: %s (%s)",
                            locationName, EtaCalculator.formatEta(etaMins), EtaCalculator.formatDistance(distMeters));
                    updateNotification(statusText);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private Notification buildForegroundNotification(String text) {
        Intent notificationIntent = new Intent(this, LiveTrackingActivity.class);
        notificationIntent.putExtra("orderId", activeOrderId);
        notificationIntent.putExtra("buyerLat", buyerLat);
        notificationIntent.putExtra("buyerLng", buyerLng);
        notificationIntent.putExtra("locationName", locationName);
        notificationIntent.putExtra("isSeller", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AquaDrop: Broadcasting Live Delivery Location")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_delivery_van)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        Notification notification = buildForegroundNotification(text);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AquaDrop Driver Geolocation",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Broadcasting driver live delivery location to KUET consumers");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

