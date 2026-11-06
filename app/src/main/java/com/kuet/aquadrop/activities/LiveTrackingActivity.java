package com.kuet.aquadrop.activities;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.LiveTelemetry;
import com.kuet.aquadrop.utils.EtaCalculator;
import com.kuet.aquadrop.utils.FirebaseManager;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live Delivery GPS Telemetry Tracker powered by OpenStreetMap (osmdroid).
 * Renders 100% free interactive street and building tiles centered on KUET Campus
 * without requiring any paid Google Maps API key or billing account.
 */
public class LiveTrackingActivity extends AppCompatActivity {

    // KUET Campus Geographic Anchors
    private static final double KUET_CENTER_LAT = 22.8998;
    private static final double KUET_CENTER_LNG = 89.5015;
    private static final double FULBARIGATE_DEPOT_LAT = 22.8970; // Water Depot coordinates
    private static final double FULBARIGATE_DEPOT_LNG = 89.5060;

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

    // OpenStreetMap (osmdroid) Map State
    private MapView osmMapView;
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
    private GeoPoint buyerGeoPoint;

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

        // Configure osmdroid tile caching & user agent before layout inflation
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_live_tracking);

        extractIntentExtras();
        initViews();
        setupOsmMapView();
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
        buyerGeoPoint = new GeoPoint(buyerLat, buyerLng);
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
        osmMapView = findViewById(R.id.osmMapView);

        tvTrackingHeaderSubtitle.setText(String.format("Order #%s • Realtime Telemetry", orderId));
        tvTrackingDestination.setText(String.format(Locale.getDefault(),
                "Destination: %s • Floor %d", locationName, floorNumber));
    }

    private void setupOsmMapView() {
        if (osmMapView == null) return;

        // Use high-contrast Mapnik OpenStreetMap raster tiles
        osmMapView.setTileSource(TileSourceFactory.MAPNIK);
        osmMapView.setMultiTouchControls(true);
        osmMapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);

        // Center directly on KUET Campus with zoom 16.5 as specified
        IMapController controller = osmMapView.getController();
        controller.setZoom(16.5);
        GeoPoint kuetCenter = new GeoPoint(KUET_CENTER_LAT, KUET_CENTER_LNG);
        controller.setCenter(kuetCenter);

        // 1. Add Destination Marker (Buyer's Hall Pin)
        destinationMarker = new Marker(osmMapView);
        destinationMarker.setPosition(buyerGeoPoint);
        destinationMarker.setTitle("Delivery Point: " + locationName);
        destinationMarker.setSnippet(String.format(Locale.getDefault(), "Floor %d", floorNumber));
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        Drawable destDrawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin);
        if (destDrawable != null) {
            Drawable tinted = destDrawable.mutate();
            DrawableCompat.setTint(tinted, ContextCompat.getColor(this, R.color.urgent_red));
            destinationMarker.setIcon(tinted);
        }
        osmMapView.getOverlays().add(destinationMarker);

        // 2. Add Water Van Marker at Fulbarigate Depot
        GeoPoint depotPoint = new GeoPoint(FULBARIGATE_DEPOT_LAT, FULBARIGATE_DEPOT_LNG);
        driverMarker = new Marker(osmMapView);
        driverMarker.setPosition(depotPoint);
        driverMarker.setTitle("AquaDrop Water Van");
        driverMarker.setSnippet("Depot Center");
        driverMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

        Drawable vanDrawable = ContextCompat.getDrawable(this, R.drawable.ic_delivery_van);
        if (vanDrawable != null) {
            Drawable tintedVan = vanDrawable.mutate();
            DrawableCompat.setTint(tintedVan, Color.parseColor("#0288D1"));
            driverMarker.setIcon(tintedVan);
        }
        osmMapView.getOverlays().add(driverMarker);

        // 3. Add Route Polyline between Depot and Destination Pin
        routePolyline = new Polyline(osmMapView);
        routePolyline.getOutlinePaint().setColor(Color.parseColor("#0288D1"));
        routePolyline.getOutlinePaint().setStrokeWidth(12f);

        List<GeoPoint> initialRoute = new ArrayList<>();
        initialRoute.add(depotPoint);
        initialRoute.add(buyerGeoPoint);
        routePolyline.setPoints(initialRoute);
        osmMapView.getOverlays().add(routePolyline);

        osmMapView.invalidate();
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

    private void subscribeToFirebaseTelemetry() {
        telemetryRef = FirebaseManager.getInstance().getTelemetryRef(orderId);

        telemetryValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Initialize with depot default position
                    double dist = EtaCalculator.distanceMeters(FULBARIGATE_DEPOT_LAT, FULBARIGATE_DEPOT_LNG, buyerLat, buyerLng);
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
        final GeoPoint driverPos = new GeoPoint(telemetry.getDriverLat(), telemetry.getDriverLng());

        // 1. Move Water Van Marker
        if (osmMapView != null) {
            if (driverMarker != null) {
                animateMarkerTo(driverMarker, driverPos);
            }

            // 2. Update Route Polyline between Van and Buyer's Hall Pin
            if (routePolyline != null) {
                List<GeoPoint> updatedRoute = new ArrayList<>();
                updatedRoute.add(driverPos);
                updatedRoute.add(buyerGeoPoint);
                routePolyline.setPoints(updatedRoute);
                osmMapView.invalidate();
            }

            // Center view on van position on initial sync
            if (!isMapInitialCentered) {
                osmMapView.getController().animateTo(driverPos);
                isMapInitialCentered = true;
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

                    // Linear interpolation from Depot to Buyer coordinates
                    double currentLat = FULBARIGATE_DEPOT_LAT + fraction * (buyerLat - FULBARIGATE_DEPOT_LAT);
                    double currentLng = FULBARIGATE_DEPOT_LNG + fraction * (buyerLng - FULBARIGATE_DEPOT_LNG);

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

    // --- Helper Utilities: Marker Animation ---

    private void animateMarkerTo(final Marker marker, final GeoPoint targetPosition) {
        if (marker == null) return;
        final GeoPoint startPosition = marker.getPosition();

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1200);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = animation.getAnimatedFraction();
                double lat = v * targetPosition.getLatitude() + (1 - v) * startPosition.getLatitude();
                double lng = v * targetPosition.getLongitude() + (1 - v) * startPosition.getLongitude();
                marker.setPosition(new GeoPoint(lat, lng));
                if (osmMapView != null) osmMapView.invalidate();
            }
        });
        animator.start();
    }

    // --- Activity Lifecycle Callbacks (Week 8 Syllabus) & osmdroid management ---

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("AquaDropLifecycle", "LiveTrackingActivity: onStart() called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("AquaDropLifecycle", "LiveTrackingActivity: onResume() called");
        if (osmMapView != null) {
            osmMapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("AquaDropLifecycle", "LiveTrackingActivity: onPause() called");
        if (osmMapView != null) {
            osmMapView.onPause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("AquaDropLifecycle", "LiveTrackingActivity: onStop() called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("AquaDropLifecycle", "LiveTrackingActivity: onDestroy() called");
        stopVanMovementSimulation();
        if (osmMapView != null) {
            osmMapView.onDetach();
        }
        if (telemetryRef != null && telemetryValueEventListener != null) {
            telemetryRef.removeEventListener(telemetryValueEventListener);
        }
    }
}
