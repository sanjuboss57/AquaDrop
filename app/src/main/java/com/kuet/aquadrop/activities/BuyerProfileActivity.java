package com.kuet.aquadrop.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.utils.FirebaseManager;
import com.kuet.aquadrop.utils.KuetLocations;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated Buyer Profile & Delivery Location Activity.
 * Displays personal information and allows updating KUET Hall/Area,
 * room, floor, optional directions, and exact delivery GPS coordinates.
 */
public class BuyerProfileActivity extends AppCompatActivity {

    private ImageView ivBackProfile;

    // Personal info views
    private TextView tvProfileName;
    private TextView tvProfileRoleBadge;
    private TextView tvProfilePhone;
    private TextView tvProfileEmail;
    private TextView tvProfileEmptyJars;
    private TextView tvProfileDeposit;

    // Saved location display views
    private MaterialButton btnToggleEditLocation;
    private LinearLayout llSavedLocationDisplay;
    private TextView tvSavedHall;
    private TextView tvSavedRoomFloor;
    private TextView tvSavedDirections;
    private TextView tvSavedGpsCoords;

    // Editable location views
    private LinearLayout llEditLocationSection;
    private Spinner spinnerEditHall;
    private TextInputEditText etEditRoom;
    private TextInputEditText etEditFloor;
    private TextInputEditText etEditDirections;
    private TextView tvEditGpsCoordsPreview;
    private MaterialButton btnDetectDeviceGps;
    private MaterialButton btnCancelEditLocation;
    private MaterialButton btnSaveDeliveryLocation;
    private MaterialButton btnBuyerProfileLogout;

    // State
    private User currentUser;
    private double editLatitude = 22.8998;
    private double editLongitude = 89.5015;
    private FusedLocationProviderClient fusedLocationClient;

    private ValueEventListener rtdbUserListener;
    private ListenerRegistration firestoreUserListener;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    detectDeviceGps();
                } else {
                    Toast.makeText(this, "Location permission denied. Using selected building GPS.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_profile);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupHallSpinner();
        setupListeners();
        subscribeToUserProfile();
    }

    private void initViews() {
        ivBackProfile = findViewById(R.id.ivBackProfile);

        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileRoleBadge = findViewById(R.id.tvProfileRoleBadge);
        tvProfilePhone = findViewById(R.id.tvProfilePhone);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileEmptyJars = findViewById(R.id.tvProfileEmptyJars);
        tvProfileDeposit = findViewById(R.id.tvProfileDeposit);

        btnToggleEditLocation = findViewById(R.id.btnToggleEditLocation);
        llSavedLocationDisplay = findViewById(R.id.llSavedLocationDisplay);
        tvSavedHall = findViewById(R.id.tvSavedHall);
        tvSavedRoomFloor = findViewById(R.id.tvSavedRoomFloor);
        tvSavedDirections = findViewById(R.id.tvSavedDirections);
        tvSavedGpsCoords = findViewById(R.id.tvSavedGpsCoords);

        llEditLocationSection = findViewById(R.id.llEditLocationSection);
        spinnerEditHall = findViewById(R.id.spinnerEditHall);
        etEditRoom = findViewById(R.id.etEditRoom);
        etEditFloor = findViewById(R.id.etEditFloor);
        etEditDirections = findViewById(R.id.etEditDirections);
        tvEditGpsCoordsPreview = findViewById(R.id.tvEditGpsCoordsPreview);
        btnDetectDeviceGps = findViewById(R.id.btnDetectDeviceGps);
        btnCancelEditLocation = findViewById(R.id.btnCancelEditLocation);
        btnSaveDeliveryLocation = findViewById(R.id.btnSaveDeliveryLocation);
        btnBuyerProfileLogout = findViewById(R.id.btnBuyerProfileLogout);
    }

    private void setupHallSpinner() {
        List<String> locations = KuetLocations.getLocationNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, locations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEditHall.setAdapter(adapter);

        spinnerEditHall.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String loc = (String) parent.getItemAtPosition(position);
                LatLng p = KuetLocations.getLatLngByName(loc);
                if (p != null) {
                    editLatitude = p.latitude;
                    editLongitude = p.longitude;
                    updateEditGpsPreview();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupListeners() {
        ivBackProfile.setOnClickListener(v -> finish());

        btnToggleEditLocation.setOnClickListener(v -> {
            boolean isVisible = llEditLocationSection.getVisibility() == View.VISIBLE;
            if (isVisible) {
                llEditLocationSection.setVisibility(View.GONE);
                btnToggleEditLocation.setText("✏️ Edit");
            } else {
                populateEditFields();
                llEditLocationSection.setVisibility(View.VISIBLE);
                btnToggleEditLocation.setText("✕ Close");
            }
        });

        btnCancelEditLocation.setOnClickListener(v -> {
            llEditLocationSection.setVisibility(View.GONE);
            btnToggleEditLocation.setText("✏️ Edit");
        });

        btnDetectDeviceGps.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                detectDeviceGps();
            } else {
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            }
        });

        btnSaveDeliveryLocation.setOnClickListener(v -> saveDeliveryLocation());

        if (btnBuyerProfileLogout != null) {
            btnBuyerProfileLogout.setOnClickListener(v -> {
                try {
                    FirebaseManager.getInstance().signOut();
                } catch (Exception ignored) {}
                Toast.makeText(BuyerProfileActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(BuyerProfileActivity.this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void detectDeviceGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener(loc -> {
            if (loc != null) {
                editLatitude = loc.getLatitude();
                editLongitude = loc.getLongitude();
                updateEditGpsPreview();
                Toast.makeText(this, "Acquired live GPS fix!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Could not acquire GPS fix. Ensure location is enabled.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateEditGpsPreview() {
        if (tvEditGpsCoordsPreview != null) {
            tvEditGpsCoordsPreview.setText(String.format(Locale.getDefault(),
                    "GPS: %.6f° N, %.6f° E", editLatitude, editLongitude));
        }
    }

    private void populateEditFields() {
        if (currentUser == null) return;

        List<String> locations = KuetLocations.getLocationNames();
        int idx = locations.indexOf(currentUser.getLocationName());
        if (idx >= 0) {
            spinnerEditHall.setSelection(idx);
        }

        if (etEditRoom != null) {
            etEditRoom.setText(!TextUtils.isEmpty(currentUser.getRoomOrFlatNo()) ? currentUser.getRoomOrFlatNo() : "101");
        }
        if (etEditFloor != null) {
            etEditFloor.setText(String.valueOf(currentUser.getFloorNumber() > 0 ? currentUser.getFloorNumber() : 1));
        }

        String directions = !TextUtils.isEmpty(currentUser.getOptionalDirections())
                ? currentUser.getOptionalDirections()
                : currentUser.getLocationNotes();
        if (etEditDirections != null) {
            etEditDirections.setText(!TextUtils.isEmpty(directions) ? directions : "");
        }

        if (currentUser.getLatitude() != 0.0 && currentUser.getLongitude() != 0.0) {
            editLatitude = currentUser.getLatitude();
            editLongitude = currentUser.getLongitude();
        } else {
            LatLng p = KuetLocations.getLatLngByName(currentUser.getLocationName());
            if (p != null) {
                editLatitude = p.latitude;
                editLongitude = p.longitude;
            }
        }
        updateEditGpsPreview();
    }

    private void subscribeToUserProfile() {
        FirebaseUser user = FirebaseManager.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        final String uid = user.getUid();

        // 1. Realtime Database Profile Listener (Primary)
        rtdbUserListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User u = User.fromDataSnapshot(snapshot);
                    if (u != null) {
                        currentUser = u;
                        applyProfileData(u);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w("BuyerProfile", "RTDB profile error: " + error.getMessage());
            }
        };
        FirebaseManager.getInstance().getRtdbUsersRef().child(uid).addValueEventListener(rtdbUserListener);

        // 2. Cloud Firestore Listener (Background)
        try {
            firestoreUserListener = FirebaseManager.getInstance().getUsersRef().document(uid)
                    .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                        @Override
                        public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException error) {
                            if (error != null || snapshot == null || !snapshot.exists()) return;
                            User u = snapshot.toObject(User.class);
                            if (u != null) {
                                currentUser = u;
                                applyProfileData(u);
                            }
                        }
                    });
        } catch (Exception ignored) {}
    }

    private void applyProfileData(User user) {
        if (user == null) return;

        tvProfileName.setText(!TextUtils.isEmpty(user.getName()) ? user.getName() : "AquaDrop Buyer");
        tvProfilePhone.setText(!TextUtils.isEmpty(user.getPhone()) ? user.getPhone() : "N/A");
        tvProfileEmail.setText(!TextUtils.isEmpty(user.getEmail()) ? user.getEmail() : "N/A");
        tvProfileRoleBadge.setText(user.isBuyer() ? "BUYER / STUDENT" : user.getRole());

        tvProfileEmptyJars.setText(String.format(Locale.getDefault(), "%d Jars", user.getEmptyJarsHeld()));
        tvProfileDeposit.setText(String.format(Locale.getDefault(), "%.0f BDT", user.getDepositBalance()));

        // Delivery Location Details
        String hall = !TextUtils.isEmpty(user.getLocationName()) ? user.getLocationName() : "Amar Ekushey Hall";
        String room = !TextUtils.isEmpty(user.getRoomOrFlatNo()) ? user.getRoomOrFlatNo() : "101";
        int floor = user.getFloorNumber() > 0 ? user.getFloorNumber() : 1;
        String directions = !TextUtils.isEmpty(user.getOptionalDirections())
                ? user.getOptionalDirections()
                : (!TextUtils.isEmpty(user.getLocationNotes()) ? user.getLocationNotes() : "Standard Hall Gate drop");

        tvSavedHall.setText(hall);
        tvSavedRoomFloor.setText(String.format(Locale.getDefault(), "Room %s • Floor %d", room, floor));
        tvSavedDirections.setText(directions);

        double lat = user.getLatitude();
        double lng = user.getLongitude();
        if (lat == 0.0 || lng == 0.0) {
            LatLng p = KuetLocations.getLatLngByName(hall);
            if (p != null) {
                lat = p.latitude;
                lng = p.longitude;
            }
        }
        tvSavedGpsCoords.setText(String.format(Locale.getDefault(), "%.6f° N, %.6f° E", lat, lng));
    }

    private void saveDeliveryLocation() {
        if (currentUser == null) return;

        String selectedHall = (String) spinnerEditHall.getSelectedItem();
        String roomStr = etEditRoom.getText() != null ? etEditRoom.getText().toString().trim() : "101";
        String floorStr = etEditFloor.getText() != null ? etEditFloor.getText().toString().trim() : "1";
        String directionsStr = etEditDirections.getText() != null ? etEditDirections.getText().toString().trim() : "";

        int floor = 1;
        try {
            floor = Integer.parseInt(floorStr);
        } catch (Exception ignored) {}

        currentUser.setLocationName(selectedHall);
        currentUser.setRoomOrFlatNo(roomStr);
        currentUser.setFloorNumber(floor);
        currentUser.setOptionalDirections(directionsStr);
        currentUser.setLocationNotes(directionsStr);
        currentUser.setLatitude(editLatitude);
        currentUser.setLongitude(editLongitude);

        applyProfileData(currentUser);
        llEditLocationSection.setVisibility(View.GONE);
        btnToggleEditLocation.setText("✏️ Edit");

        Map<String, Object> updates = new HashMap<>();
        updates.put("locationName", selectedHall);
        updates.put("roomOrFlatNo", roomStr);
        updates.put("floorNumber", floor);
        updates.put("optionalDirections", directionsStr);
        updates.put("locationNotes", directionsStr);
        updates.put("latitude", editLatitude);
        updates.put("longitude", editLongitude);

        final String uid = currentUser.getUid();

        // 1. Realtime Database Write FIRST
        FirebaseManager.getInstance().getRtdbUsersRef().child(uid).updateChildren(updates)
                .addOnSuccessListener(unused -> Toast.makeText(BuyerProfileActivity.this,
                        "✓ Delivery location updated successfully!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Log.w("BuyerProfile", "RTDB save notice: " + e.getMessage()));

        // 2. Cloud Firestore Mirror silently
        try {
            FirebaseManager.getInstance().getUsersRef().document(uid)
                    .update(updates)
                    .addOnFailureListener(e -> Log.w("BuyerProfile", "Firestore mirror notice: " + e.getMessage()));
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FirebaseUser user = FirebaseManager.getInstance().getCurrentUser();
        if (user != null && rtdbUserListener != null) {
            FirebaseManager.getInstance().getRtdbUsersRef().child(user.getUid()).removeEventListener(rtdbUserListener);
        }
        if (firestoreUserListener != null) {
            firestoreUserListener.remove();
        }
    }
}

