package com.kuet.aquadrop.activities;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.LiveTelemetry;
import com.kuet.aquadrop.utils.EtaCalculator;
import com.kuet.aquadrop.utils.FirebaseManager;

import java.util.Locale;

public class LiveTrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    // KUET Campus Geographic Anchors
    private static final LatLng KUET_CENTER = new LatLng(22.8998, 89.5015);
    private static final LatLng FULBARIGATE_DEPOT = new LatLng(22.8970, 89.5060); // Water Depot coordinates

    // Views
    private ImageView ivBackTracking;
    private TextView tvTrackingHeaderTitle;
    private TextView tvTrackingHeaderSubtitle;
    private TextView tvTrackingStatusBadge;
    private TextView tvLiveTelemetryTime;
    private TextView tvTrackingDestination;
    private TextView tvTrackingDistanceRemaining;
    private TextView tvTrackingEtaCountdown;
    private MaterialButton btnSimulateVanMovement;

    // Google Maps State
    private GoogleMap googleMap;
    private Marker driverMarker;
    private Marker destinationMarker;
    private Polyline routePolyline;
    private boolean isMapInitialCentered = false;

    // Order & Location Parameters
    private String orderId = "AQ-DEMO";
    private String locationName = "Fazlul Huq Hall";
    private double buyerLat = 22.8992;
    private double buyerLng = 89.5012;
    private int floorNumber = 3;
    private LatLng buyerLatLng;

    // Firebase Telemetry Realtime Database Listener
    private DatabaseReference telemetryRef;
    private ValueEventListener telemetryValueEventListener;

    // Van Movement Simulation Handler
    private final Handler simulationHandler = new Handler(Looper.getMainLooper());
    private Runnable simulationRunnable;
    private boolean isSimulating = false;
    private int simulationStep = 0;
    private static final int TOTAL_SIMULATION_STEPS = 25;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_tracking);

        extractIntentExtras();
        initViews();
        setupMapFragment();
        setupListeners();
        subscribeToFirebaseTelemetry();
    }

    private void extractIntentExtras() {
        if (getIntent() != null) {
            if (getIntent().hasExtra("orderId")) {
                orderId = getIntent().getStringExtra("orderId");
            }
            if (getIntent().hasExtra("locationName")) {
                locationName = getIntent().getStringExtra("locationName");
            }
            buyerLat = getIntent().getDoubleExtra("buyerLat", 22.8992);
            buyerLng = getIntent().getDoubleExtra("buyerLng", 89.5012);
            floorNumber = getIntent().getIntExtra("floorNumber", 3);
        }
        buyerLatLng = new LatLng(buyerLat, buyerLng);
    }

    private void initViews() {
        ivBackTracking = findViewById(R.id.ivBackTracking);
        tvTrackingHeaderTitle = findViewById(R.id.tvTrackingHeaderTitle);
        tvTrackingHeaderSubtitle = findViewById(R.id.tvTrackingHeaderSubtitle);
        tvTrackingStatusBadge = findViewById(R.id.tvTrackingStatusBadge);
        tvLiveTelemetryTime = findViewById(R.id.tvLiveTelemetryTime);
        tvTrackingDestination = findViewById(R.id.tvTrackingDestination);
        tvTrackingDistanceRemaining = findViewById(R.id.tvTrackingDistanceRemaining);
        tvTrackingEtaCountdown = findViewById(R.id.tvTrackingEtaCountdown);
        btnSimulateVanMovement = findViewById(R.id.btnSimulateVanMovement);

        tvTrackingHeaderSubtitle.setText(String.format("Order #%s • Realtime Telemetry", orderId));
        tvTrackingDestination.setText(String.format(Locale.getDefault(),
                "Destination: %s • Floor %d", locationName, floorNumber));
    }

    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupListeners() {
        ivBackTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnSimulateVanMovement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleVanMovementSimulation();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        // Add Buyer Hall Destination Pin (Red Marker)
        destinationMarker = googleMap.addMarker(new MarkerOptions()
                .position(buyerLatLng)
                .title("Delivery Point: " + locationName)
                .snippet(String.format(Locale.getDefault(), "Floor %d", floorNumber))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Initial camera centering on KUET campus
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(KUET_CENTER, 16.0f));

        // Add initial Water Van marker at Depot
        driverMarker = googleMap.addMarker(new MarkerOptions()
                .position(FULBARIGATE_DEPOT)
                .title("AquaDrop Water Van")
                .snippet("Depot Center")
                .icon(getBitmapDescriptorFromVector(R.drawable.ic_delivery_van, Color.parseColor("#0288D1"))));
    }

    private void subscribeToFirebaseTelemetry() {
        telemetryRef = FirebaseManager.getInstance().getTelemetryRef(orderId);

        telemetryValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // If no telemetry published yet, initialize with depot position
                    double dist = EtaCalculator.distanceMeters(FULBARIGATE_DEPOT.latitude, FULBARIGATE_DEPOT.longitude, buyerLat, buyerLng);
                    int eta = EtaCalculator.calculateEtaMinutes(dist / 1000.0, floorNumber, 0);
                    updateTelemetryUI(dist, eta);
                    return;
                }

                LiveTelemetry telemetry = snapshot.getValue(LiveTelemetry.class);
                if (telemetry != null) {
                    onLiveTelemetryReceived(telemetry);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LiveTrackingActivity.this, "Telemetry sync failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        telemetryRef.addValueEventListener(telemetryValueEventListener);
    }

    private void onLiveTelemetryReceived(LiveTelemetry telemetry) {
        final LatLng driverPos = new LatLng(telemetry.getDriverLat(), telemetry.getDriverLng());

        // 1. Smoothly Move Water Van Marker
        if (googleMap != null) {
            if (driverMarker == null) {
                driverMarker = googleMap.addMarker(new MarkerOptions()
                        .position(driverPos)
                        .title("AquaDrop Water Van")
                        .icon(getBitmapDescriptorFromVector(R.drawable.ic_delivery_van, Color.parseColor("#0288D1"))));
            } else {
                animateMarkerTo(driverMarker, driverPos);
            }

            // 2. Draw / Update Polyline between Van and Buyer's Hall Pin
            if (routePolyline != null) {
                routePolyline.remove();
            }
            routePolyline = googleMap.addPolyline(new PolylineOptions()
                    .add(driverPos, buyerLatLng)
                    .width(10f)
                    .color(Color.parseColor("#0288D1"))
                    .geodesic(true));

            // Camera bounds framing both van and hall on first sync
            if (!isMapInitialCentered) {
                try {
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    builder.include(driverPos);
                    builder.include(buyerLatLng);
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200));
                    isMapInitialCentered = true;
                } catch (Exception e) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverPos, 16.0f));
                }
            }
        }

        // 3. Update Text Views in Real Time
        updateTelemetryUI(telemetry.getDistanceMeters(), telemetry.getEtaMinutes());
    }

    private void updateTelemetryUI(double distanceMeters, int etaMinutes) {
        tvTrackingDistanceRemaining.setText(EtaCalculator.formatDistance(distanceMeters));
        tvTrackingEtaCountdown.setText(EtaCalculator.formatEta(etaMinutes));
        tvLiveTelemetryTime.setText("● Live GPS Synced");
    }

    // --- Van Movement Simulation to KUET Hall ---

    private void toggleVanMovementSimulation() {
        if (isSimulating) {
            stopVanMovementSimulation();
        } else {
            startVanMovementSimulation();
        }
    }

    private void startVanMovementSimulation() {
        isSimulating = true;
        simulationStep = 0;
        btnSimulateVanMovement.setText("Stop Campus Simulation");
        btnSimulateVanMovement.setStrokeColor(ContextCompat.getColorStateList(this, R.color.urgent_red));
        btnSimulateVanMovement.setTextColor(ContextCompat.getColor(this, R.color.urgent_red));
        Toast.makeText(this, "Simulating Van moving from Fulbarigate Depot to " + locationName, Toast.LENGTH_SHORT).show();

        simulationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSimulating) {
                    return;
                }

                if (simulationStep <= TOTAL_SIMULATION_STEPS) {
                    double fraction = (double) simulationStep / TOTAL_SIMULATION_STEPS;

                    // Linear interpolation from Depot (22.8970, 89.5060) to Buyer's Hall coordinates
                    double currentLat = FULBARIGATE_DEPOT.latitude + fraction * (buyerLat - FULBARIGATE_DEPOT.latitude);
                    double currentLng = FULBARIGATE_DEPOT.longitude + fraction * (buyerLng - FULBARIGATE_DEPOT.longitude);

                    double distMeters = EtaCalculator.distanceMeters(currentLat, currentLng, buyerLat, buyerLng);
                    double distKm = distMeters / 1000.0;
                    int etaMins = EtaCalculator.calculateEtaMinutes(distKm, floorNumber, 0);

                    LiveTelemetry telemetry = new LiveTelemetry(
                            orderId,
                            currentLat,
                            currentLng,
                            etaMins,
                            distMeters,
                            System.currentTimeMillis()
                    );

                    // Push to Firebase Realtime Database: updates both Buyer and Seller live screens
                    FirebaseManager.getInstance().getTelemetryRef(orderId).setValue(telemetry);

                    simulationStep++;
                    simulationHandler.postDelayed(this, 2000); // 2-second interpolation interval
                } else {
                    stopVanMovementSimulation();
                    Toast.makeText(LiveTrackingActivity.this, "Van reached " + locationName + "!", Toast.LENGTH_LONG).show();
                }
            }
        };

        simulationHandler.post(simulationRunnable);
    }

    private void stopVanMovementSimulation() {
        isSimulating = false;
        simulationHandler.removeCallbacks(simulationRunnable);
        btnSimulateVanMovement.setText("Simulate Van Movement to KUET Hall");
        btnSimulateVanMovement.setStrokeColor(ContextCompat.getColorStateList(this, R.color.primary));
        btnSimulateVanMovement.setTextColor(ContextCompat.getColor(this, R.color.primary));
    }

    // --- Helper Utilities: Custom Markers & Marker Animation ---

    private void animateMarkerTo(final Marker marker, final LatLng targetPosition) {
        if (marker == null) return;
        final LatLng startPosition = marker.getPosition();

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1200);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = animation.getAnimatedFraction();
                double lat = v * targetPosition.latitude + (1 - v) * startPosition.latitude;
                double lng = v * targetPosition.longitude + (1 - v) * startPosition.longitude;
                marker.setPosition(new LatLng(lat, lng));
            }
        });
        animator.start();
    }

    private BitmapDescriptor getBitmapDescriptorFromVector(int vectorResId, int tintColor) {
        Drawable vectorDrawable = ContextCompat.getDrawable(this, vectorResId);
        if (vectorDrawable == null) {
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        }
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        DrawableCompat.setTint(vectorDrawable, tintColor);
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVanMovementSimulation();
        if (telemetryRef != null && telemetryValueEventListener != null) {
            telemetryRef.removeEventListener(telemetryValueEventListener);
        }
    }
}
