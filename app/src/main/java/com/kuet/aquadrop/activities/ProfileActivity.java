package com.kuet.aquadrop.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.utils.FirebaseManager;
import com.kuet.aquadrop.utils.KuetLocations;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Profile Activity allowing both BUYERS and SELLERS to view and edit their profile,
 * campus location, and GPS coordinates with real-time Firestore synchronization.
 */
public class ProfileActivity extends AppCompatActivity {

    private ImageView ivProfileBack;
    private ProgressBar progressBarProfile;

    // Header views
    private TextView tvProfileNameBanner;
    private TextView tvProfilePhoneBanner;
    private TextView tvProfileRoleBadge;

    // Form fields
    private TextInputEditText etProfileName;
    private TextInputEditText etProfilePhone;
    private Spinner spinnerProfileLocation;
    private TextInputEditText etProfileRoom;
    private TextInputEditText etProfileFloor;

    // GPS views
    private TextView tvProfileGpsCoords;
    private MaterialButton btnProfileGpsFromPreset;
    private MaterialButton btnProfileDetectLiveGps;

    // Buyer ledger section
    private MaterialCardView cardProfileBuyerLedger;
    private TextView tvProfileEmptyJars;
    private TextView tvProfileDepositBalance;

    private MaterialButton btnProfileSave;

    // State
    private User currentUser;
    private double currentLatitude = 22.8998;
    private double currentLongitude = 89.5015;
    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean fineGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarseGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fineGranted) || Boolean.TRUE.equals(coarseGranted)) {
                    detectDeviceGpsLocation();
                } else {
                    Toast.makeText(this, "Location permission denied. You can select location from the campus hall list.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupLocationSpinner();
        setupListeners();
        loadUserProfile();
    }

    private void initViews() {
        ivProfileBack = findViewById(R.id.ivProfileBack);
        progressBarProfile = findViewById(R.id.progressBarProfile);

        tvProfileNameBanner = findViewById(R.id.tvProfileNameBanner);
        tvProfilePhoneBanner = findViewById(R.id.tvProfilePhoneBanner);
        tvProfileRoleBadge = findViewById(R.id.tvProfileRoleBadge);

        etProfileName = findViewById(R.id.etProfileName);
        etProfilePhone = findViewById(R.id.etProfilePhone);
        spinnerProfileLocation = findViewById(R.id.spinnerProfileLocation);
        etProfileRoom = findViewById(R.id.etProfileRoom);
        etProfileFloor = findViewById(R.id.etProfileFloor);

        tvProfileGpsCoords = findViewById(R.id.tvProfileGpsCoords);
        btnProfileGpsFromPreset = findViewById(R.id.btnProfileGpsFromPreset);
        btnProfileDetectLiveGps = findViewById(R.id.btnProfileDetectLiveGps);

        cardProfileBuyerLedger = findViewById(R.id.cardProfileBuyerLedger);
        tvProfileEmptyJars = findViewById(R.id.tvProfileEmptyJars);
        tvProfileDepositBalance = findViewById(R.id.tvProfileDepositBalance);

        btnProfileSave = findViewById(R.id.btnProfileSave);
    }

    private void setupLocationSpinner() {
        List<String> locations = KuetLocations.getLocationNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                locations
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProfileLocation.setAdapter(adapter);

        spinnerProfileLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // When selecting a campus location, offer preset GPS coordinates
                String selected = (String) parent.getItemAtPosition(position);
                LatLng latLng = KuetLocations.getLatLngByName(selected);
                if (latLng != null) {
                    currentLatitude = latLng.latitude;
                    currentLongitude = latLng.longitude;
                    updateGpsDisplay();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupListeners() {
        ivProfileBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnProfileGpsFromPreset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedLocation = (String) spinnerProfileLocation.getSelectedItem();
                LatLng latLng = KuetLocations.getLatLngByName(selectedLocation);
                if (latLng != null) {
                    currentLatitude = latLng.latitude;
                    currentLongitude = latLng.longitude;
                    updateGpsDisplay();
                    Toast.makeText(ProfileActivity.this,
                            "GPS coordinates set to " + selectedLocation, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnProfileDetectLiveGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkLocationPermissionAndDetect();
            }
        });

        btnProfileSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveProfileChanges();
            }
        });
    }

    private void updateGpsDisplay() {
        tvProfileGpsCoords.setText(String.format(Locale.getDefault(),
                "%.6f° N, %.6f° E", currentLatitude, currentLongitude));
    }

    private void checkLocationPermissionAndDetect() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            detectDeviceGpsLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void detectDeviceGpsLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Toast.makeText(this, "Detecting device GPS coordinates...", Toast.LENGTH_SHORT).show();

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            currentLatitude = location.getLatitude();
                            currentLongitude = location.getLongitude();
                            updateGpsDisplay();
                            Toast.makeText(ProfileActivity.this, "Live GPS coordinates detected!", Toast.LENGTH_SHORT).show();
                        } else {
                            // Fallback to last known location
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    currentLatitude = lastLoc.getLatitude();
                                    currentLongitude = lastLoc.getLongitude();
                                    updateGpsDisplay();
                                    Toast.makeText(ProfileActivity.this, "Last known GPS coordinates detected!", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(ProfileActivity.this, "Could not acquire GPS fix. Please ensure GPS is turned on.", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileActivity.this, "GPS error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUserProfile() {
        FirebaseUser authUser = FirebaseManager.getInstance().getCurrentUser();
        if (authUser == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setLoading(true);
        final String uid = authUser.getUid();
        final java.util.concurrent.atomic.AtomicBoolean loaded = new java.util.concurrent.atomic.AtomicBoolean(false);

        // 1. Fetch from Realtime Database
        FirebaseManager.getInstance().getRtdbUsersRef().child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            User user = User.fromDataSnapshot(snapshot);
                            if (user != null && loaded.compareAndSet(false, true)) {
                                setLoading(false);
                                currentUser = user;
                                populateUi(user);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // 2. Fetch from Firestore
        try {
            FirebaseManager.getInstance().getUsersRef().document(uid).get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot doc) {
                            if (doc != null && doc.exists()) {
                                User user = doc.toObject(User.class);
                                if (user != null && loaded.compareAndSet(false, true)) {
                                    setLoading(false);
                                    currentUser = user;
                                    populateUi(user);
                                }
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            if (!loaded.get()) {
                                setLoading(false);
                            }
                        }
                    });
        } catch (Exception ignored) {}
    }

    private void populateUi(User user) {
        tvProfileNameBanner.setText(user.getName());
        tvProfilePhoneBanner.setText(user.getPhone());

        boolean isSeller = user.isSeller();
        tvProfileRoleBadge.setText(isSeller ? "CENTRAL DEPOT SUPPLIER (SELLER)" : "CAMPUS CONSUMER (BUYER)");
        tvProfileRoleBadge.setBackgroundColor(isSeller ? 0xFFEDE7F6 : 0xFFE0F7FA);
        tvProfileRoleBadge.setTextColor(isSeller ? 0xFF512DA8 : 0xFF00838F);

        etProfileName.setText(user.getName());
        etProfilePhone.setText(user.getPhone());
        etProfileRoom.setText(user.getRoomOrFlatNo());
        etProfileFloor.setText(String.valueOf(user.getFloorNumber()));

        // Set spinner selection
        List<String> locations = KuetLocations.getLocationNames();
        int index = locations.indexOf(user.getLocationName());
        if (index >= 0) {
            spinnerProfileLocation.setSelection(index);
        }

        // Set GPS coordinates
        if (user.getLatitude() != 0.0 && user.getLongitude() != 0.0) {
            currentLatitude = user.getLatitude();
            currentLongitude = user.getLongitude();
        } else {
            LatLng defLatLng = KuetLocations.getLatLngByName(user.getLocationName());
            if (defLatLng != null) {
                currentLatitude = defLatLng.latitude;
                currentLongitude = defLatLng.longitude;
            }
        }
        updateGpsDisplay();

        // Bottle Ledger display (only relevant for buyers)
        if (isSeller) {
            cardProfileBuyerLedger.setVisibility(View.GONE);
        } else {
            cardProfileBuyerLedger.setVisibility(View.VISIBLE);
            tvProfileEmptyJars.setText(String.format(Locale.getDefault(), "%d Jars", user.getEmptyJarsHeld()));
            tvProfileDepositBalance.setText(String.format(Locale.getDefault(), "%.0f BDT", user.getDepositBalance()));
        }
    }

    private void saveProfileChanges() {
        if (currentUser == null) {
            return;
        }

        String name = etProfileName.getText() != null ? etProfileName.getText().toString().trim() : "";
        String phone = etProfilePhone.getText() != null ? etProfilePhone.getText().toString().trim() : "";
        String room = etProfileRoom.getText() != null ? etProfileRoom.getText().toString().trim() : "";
        String floorStr = etProfileFloor.getText() != null ? etProfileFloor.getText().toString().trim() : "1";
        String locationName = (String) spinnerProfileLocation.getSelectedItem();

        if (TextUtils.isEmpty(name)) {
            etProfileName.setError("Name is required");
            etProfileName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone) || phone.length() < 10) {
            etProfilePhone.setError("Valid phone number required");
            etProfilePhone.requestFocus();
            return;
        }

        int floor = 1;
        try {
            floor = Integer.parseInt(floorStr);
            if (floor < 0 || floor > 6) floor = 1;
        } catch (NumberFormatException e) {
            floor = 1;
        }
        final int finalFloor = floor;

        setLoading(true);

        final Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("locationName", locationName);
        updates.put("roomOrFlatNo", room);
        updates.put("floorNumber", finalFloor);
        updates.put("latitude", currentLatitude);
        updates.put("longitude", currentLongitude);

        // Update Realtime Database
        FirebaseManager.getInstance().getRtdbUsersRef().child(currentUser.getUid()).updateChildren(updates);

        // Update Firestore
        try {
            FirebaseManager.getInstance().getUsersRef().document(currentUser.getUid()).update(updates);
        } catch (Exception ignored) {}

        setLoading(false);
        Toast.makeText(ProfileActivity.this,
                "Profile updated! Changes are live across AquaDrop.", Toast.LENGTH_SHORT).show();

        // Update local models
        currentUser.setName(name);
        currentUser.setPhone(phone);
        currentUser.setLocationName(locationName);
        currentUser.setRoomOrFlatNo(room);
        currentUser.setFloorNumber(finalFloor);
        currentUser.setLatitude(currentLatitude);
        currentUser.setLongitude(currentLongitude);

        tvProfileNameBanner.setText(name);
        tvProfilePhoneBanner.setText(phone);
    }

    private void setLoading(boolean isLoading) {
        progressBarProfile.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnProfileSave.setEnabled(!isLoading);
        etProfileName.setEnabled(!isLoading);
        etProfilePhone.setEnabled(!isLoading);
        etProfileRoom.setEnabled(!isLoading);
        etProfileFloor.setEnabled(!isLoading);
        spinnerProfileLocation.setEnabled(!isLoading);
        btnProfileGpsFromPreset.setEnabled(!isLoading);
        btnProfileDetectLiveGps.setEnabled(!isLoading);
    }
}

