package com.kuet.aquadrop.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.utils.FirebaseManager;
import com.kuet.aquadrop.utils.KuetLocations;

import java.util.List;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etFullName;
    private TextInputEditText etRegPhone;
    private TextInputEditText etRegPassword;
    private RadioGroup rgRole;
    private RadioButton rbBuyer;
    private RadioButton rbSeller;
    private Spinner spinnerLocation;
    private TextInputEditText etRoomOrFlat;
    private TextInputEditText etFloorNumber;
    private LinearLayout layoutSellerSpecialAccess;
    private TextInputEditText etSellerAccessCode;
    private TextInputEditText etDepotName;
    private View tilOptionalDirections;
    private TextInputEditText etOptionalDirections;
    private MaterialButton btnRegister;
    private ProgressBar progressBar;
    private TextView tvGoToLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupLocationSpinner();
        setupListeners();
    }

    private void initViews() {
        etFullName = findViewById(R.id.etFullName);
        etRegPhone = findViewById(R.id.etRegPhone);
        etRegPassword = findViewById(R.id.etRegPassword);
        rgRole = findViewById(R.id.rgRole);
        rbBuyer = findViewById(R.id.rbBuyer);
        rbSeller = findViewById(R.id.rbSeller);
        spinnerLocation = findViewById(R.id.spinnerLocation);
        etRoomOrFlat = findViewById(R.id.etRoomOrFlat);
        etFloorNumber = findViewById(R.id.etFloorNumber);

        layoutSellerSpecialAccess = findViewById(R.id.layoutSellerSpecialAccess);
        etSellerAccessCode = findViewById(R.id.etSellerAccessCode);
        etDepotName = findViewById(R.id.etDepotName);
        tilOptionalDirections = findViewById(R.id.tilOptionalDirections);
        etOptionalDirections = findViewById(R.id.etOptionalDirections);

        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBarRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
    }

    private void setupLocationSpinner() {
        List<String> locations = KuetLocations.getLocationNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                locations
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLocation.setAdapter(adapter);
    }

    private void setupListeners() {
        if (rgRole != null) {
            rgRole.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    boolean isSeller = (checkedId == R.id.rbSeller);
                    if (layoutSellerSpecialAccess != null) {
                        layoutSellerSpecialAccess.setVisibility(isSeller ? View.VISIBLE : View.GONE);
                    }
                    if (tilOptionalDirections != null) {
                        tilOptionalDirections.setVisibility(isSeller ? View.GONE : View.VISIBLE);
                    }
                }
            });
        }

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptRegistration();
            }
        });

        tvGoToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    public static String normalizePhoneNumber(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("880") && digits.length() >= 13) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private void attemptRegistration() {
        final String name = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        final String rawPhone = etRegPhone.getText() != null ? etRegPhone.getText().toString().trim() : "";
        final String password = etRegPassword.getText() != null ? etRegPassword.getText().toString().trim() : "";
        String roomOrFlat = etRoomOrFlat.getText() != null ? etRoomOrFlat.getText().toString().trim() : "";
        final String floorStr = etFloorNumber.getText() != null ? etFloorNumber.getText().toString().trim() : "1";

        // Fast & Lean Validation
        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }

        final String normalizedPhone = normalizePhoneNumber(rawPhone);
        if (TextUtils.isEmpty(normalizedPhone) || normalizedPhone.length() < 10) {
            etRegPhone.setError("Please enter a valid phone number (min 10 digits)");
            etRegPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etRegPassword.setError("Password must be at least 6 characters");
            etRegPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(roomOrFlat)) {
            roomOrFlat = "Room 101"; // Sensible default if left blank
        }

        int floorNumber = 1;
        try {
            floorNumber = Integer.parseInt(floorStr);
            if (floorNumber < 0 || floorNumber > 6) {
                floorNumber = 1;
            }
        } catch (NumberFormatException e) {
            floorNumber = 1;
        }

        // Determine Selected Role
        final String role = rbSeller.isChecked() ? User.ROLE_SELLER : User.ROLE_BUYER;

        // Selected KUET Location and GPS coordinates
        final String selectedLocation = (String) spinnerLocation.getSelectedItem();
        final LatLng latLng = KuetLocations.getLatLngByName(selectedLocation);

        final int finalFloorNumber = floorNumber;
        final String finalRoom = roomOrFlat;

        final String optionalDirections = (etOptionalDirections != null && etOptionalDirections.getText() != null)
                ? etOptionalDirections.getText().toString().trim() : "";

        // Seller Special Authorization Verification
        if (rbSeller.isChecked()) {
            String enteredKey = etSellerAccessCode != null && etSellerAccessCode.getText() != null
                    ? etSellerAccessCode.getText().toString().trim() : "";
            if (TextUtils.isEmpty(enteredKey)) {
                if (etSellerAccessCode != null) {
                    etSellerAccessCode.setError("Special Seller Access Code is required");
                    etSellerAccessCode.requestFocus();
                }
                Toast.makeText(this, "Special Seller Access Code is required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isValidSellerAccessKey(enteredKey)) {
                if (etSellerAccessCode != null) {
                    etSellerAccessCode.setError("Invalid Access Code! Contact AquaDrop Authority (KUET).");
                    etSellerAccessCode.requestFocus();
                }
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("🔒 Special Access Code Required")
                        .setMessage("Seller / Van accounts require an authorized Special Access Code (e.g., AQUA-SELLER-1, AQUA-SELLER-2, AQUA-SELLER-3, AQUA-SELLER-4, KUET-WATER-ADMIN).\n\nPlease enter an official code or select 'Buyer / Consumer' to register.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            final String cleanCode = enteredKey.toUpperCase(Locale.ROOT).trim();
            final String enteredDepot = (etDepotName != null && etDepotName.getText() != null
                    && !TextUtils.isEmpty(etDepotName.getText().toString().trim()))
                    ? etDepotName.getText().toString().trim()
                    : "KUET Depot";

            setLoadingState(true);

            // Check Cloud Firestore users collection to ensure no existing Seller has claimed this code
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                    .whereEqualTo("sellerAccessCode", cleanCode)
                    .get()
                    .addOnSuccessListener(new OnSuccessListener<com.google.firebase.firestore.QuerySnapshot>() {
                        @Override
                        public void onSuccess(com.google.firebase.firestore.QuerySnapshot querySnapshot) {
                            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                setLoadingState(false);
                                new androidx.appcompat.app.AlertDialog.Builder(RegisterActivity.this)
                                        .setTitle("🔒 Code Already Claimed")
                                        .setMessage("Seller Access Code \"" + cleanCode + "\" is already registered to another Seller!\n\nEach special access code belongs strictly to one seller.")
                                        .setPositiveButton("OK", null)
                                        .show();
                                return;
                            }

                            // Also check legacy sellerAccessKey field
                            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                                    .whereEqualTo("sellerAccessKey", cleanCode)
                                    .get()
                                    .addOnSuccessListener(new OnSuccessListener<com.google.firebase.firestore.QuerySnapshot>() {
                                        @Override
                                        public void onSuccess(com.google.firebase.firestore.QuerySnapshot legacySnap) {
                                            if (legacySnap != null && !legacySnap.isEmpty()) {
                                                setLoadingState(false);
                                                new androidx.appcompat.app.AlertDialog.Builder(RegisterActivity.this)
                                                        .setTitle("🔒 Code Already Claimed")
                                                        .setMessage("Seller Access Code \"" + cleanCode + "\" is already registered to another Seller!\n\nEach special access code belongs strictly to one seller.")
                                                        .setPositiveButton("OK", null)
                                                        .show();
                                                return;
                                            }

                                            proceedWithRegistration(name, normalizedPhone, password, role,
                                                    selectedLocation, finalRoom, finalFloorNumber, latLng,
                                                    cleanCode, enteredDepot, optionalDirections);
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            proceedWithRegistration(name, normalizedPhone, password, role,
                                                    selectedLocation, finalRoom, finalFloorNumber, latLng,
                                                    cleanCode, enteredDepot, optionalDirections);
                                        }
                                    });
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            proceedWithRegistration(name, normalizedPhone, password, role,
                                    selectedLocation, finalRoom, finalFloorNumber, latLng,
                                    cleanCode, enteredDepot, optionalDirections);
                        }
                    });

        } else {
            setLoadingState(true);
            proceedWithRegistration(name, normalizedPhone, password, role,
                    selectedLocation, finalRoom, finalFloorNumber, latLng,
                    "", "", optionalDirections);
        }
    }

    private void proceedWithRegistration(final String name, final String normalizedPhone, final String password,
                                        final String role, final String selectedLocation, final String finalRoom,
                                        final int finalFloorNumber, final LatLng latLng,
                                        final String sellerAccessCode, final String depotOrDriverTitle,
                                        final String optionalDirections) {
        final String authEmail = normalizedPhone + "@aquadrop.kuet.ac.bd";

        FirebaseManager.getInstance().getAuth().createUserWithEmailAndPassword(authEmail, password)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        FirebaseUser firebaseUser = authResult.getUser();
                        if (firebaseUser != null) {
                            String uid = firebaseUser.getUid();

                            User newUser = new User(
                                    uid,
                                    name,
                                    authEmail,
                                    normalizedPhone,
                                    role,
                                    selectedLocation,
                                    finalRoom,
                                    finalFloorNumber,
                                    latLng.latitude,
                                    latLng.longitude,
                                    0,
                                    0.0
                            );
                            newUser.setSellerAccessCode(sellerAccessCode);
                            newUser.setSellerAccessKey(sellerAccessCode);
                            newUser.setDepotOrDriverTitle(depotOrDriverTitle);
                            newUser.setOptionalDirections(optionalDirections);
                            newUser.setLocationNotes(optionalDirections);
                            newUser.setSellerAuthorized(User.ROLE_SELLER.equalsIgnoreCase(role));

                            saveUserProfile(newUser);
                        } else {
                            setLoadingState(false);
                            Toast.makeText(RegisterActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            FirebaseManager.getInstance().getAuth().signInWithEmailAndPassword(authEmail, password)
                                    .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                                        @Override
                                        public void onSuccess(AuthResult authResult) {
                                            FirebaseUser firebaseUser = authResult.getUser();
                                            if (firebaseUser != null) {
                                                String uid = firebaseUser.getUid();
                                                User updatedUser = new User(
                                                        uid,
                                                        name,
                                                        authEmail,
                                                        normalizedPhone,
                                                        role,
                                                        selectedLocation,
                                                        finalRoom,
                                                        finalFloorNumber,
                                                        latLng.latitude,
                                                        latLng.longitude,
                                                        0,
                                                        0.0
                                                );
                                                updatedUser.setSellerAccessCode(sellerAccessCode);
                                                updatedUser.setSellerAccessKey(sellerAccessCode);
                                                updatedUser.setDepotOrDriverTitle(depotOrDriverTitle);
                                                updatedUser.setOptionalDirections(optionalDirections);
                                                updatedUser.setLocationNotes(optionalDirections);
                                                updatedUser.setSellerAuthorized(User.ROLE_SELLER.equalsIgnoreCase(role));
                                                saveUserProfile(updatedUser);
                                            } else {
                                                setLoadingState(false);
                                                Toast.makeText(RegisterActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(Exception signErr) {
                                            setLoadingState(false);
                                            new androidx.appcompat.app.AlertDialog.Builder(RegisterActivity.this)
                                                    .setTitle("Phone Already Registered")
                                                    .setMessage("Phone " + normalizedPhone + " is already registered with a different password.\n\nPlease log in using your existing password, or enter the password previously used.")
                                                    .setPositiveButton("Go to Login", (dialog, which) -> {
                                                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                                        intent.putExtra("prefillPhone", normalizedPhone);
                                                        startActivity(intent);
                                                        finish();
                                                    })
                                                    .setNegativeButton("Cancel", null)
                                                    .show();
                                        }
                                    });
                        } else {
                            setLoadingState(false);
                            Toast.makeText(RegisterActivity.this, "Registration Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveUserProfile(final User user) {
        FirebaseManager.getInstance().saveUser(user,
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        setLoadingState(false);
                        Toast.makeText(RegisterActivity.this, "Welcome to AquaDrop, " + user.getName() + "!", Toast.LENGTH_SHORT).show();

                        Class<?> targetActivity = user.isSeller()
                                ? SellerDashboardActivity.class
                                : BuyerDashboardActivity.class;

                        Intent intent = new Intent(RegisterActivity.this, targetActivity);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                },
                new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        setLoadingState(false);
                        Toast.makeText(RegisterActivity.this, "Warning saving profile: " + e.getMessage(), Toast.LENGTH_LONG).show();

                        // Still proceed since user is authenticated
                        Class<?> targetActivity = user.isSeller()
                                ? SellerDashboardActivity.class
                                : BuyerDashboardActivity.class;
                        Intent intent = new Intent(RegisterActivity.this, targetActivity);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                });
    }

    private void setLoadingState(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!isLoading);
        etFullName.setEnabled(!isLoading);
        etRegPhone.setEnabled(!isLoading);
        etRegPassword.setEnabled(!isLoading);
        etRoomOrFlat.setEnabled(!isLoading);
        etFloorNumber.setEnabled(!isLoading);
        spinnerLocation.setEnabled(!isLoading);
        rbBuyer.setEnabled(!isLoading);
        rbSeller.setEnabled(!isLoading);
        if (etSellerAccessCode != null) {
            etSellerAccessCode.setEnabled(!isLoading);
        }
        if (etDepotName != null) {
            etDepotName.setEnabled(!isLoading);
        }
        if (etOptionalDirections != null) {
            etOptionalDirections.setEnabled(!isLoading);
        }
        tvGoToLogin.setEnabled(!isLoading);
    }

    public static boolean isValidSellerAccessKey(String key) {
        if (key == null) return false;
        String clean = key.trim().toUpperCase(Locale.ROOT);
        return clean.equals("AQUA-SELLER-1")
                || clean.equals("AQUA-SELLER-2")
                || clean.equals("AQUA-SELLER-3")
                || clean.equals("AQUA-SELLER-4")
                || clean.equals("KUET-WATER-ADMIN")
                || clean.equals("AQUADROP_SELLER_2026")
                || clean.equals("KUET_ADMIN_AQUA")
                || clean.equals("AQUA_SELLER_KEY")
                || clean.equals("AQUA2026")
                || clean.equals("SELLER77")
                || clean.equals("KUETSELLER")
                || clean.equals("AQUA_SELLER")
                || clean.equals("SELLER123");
    }
}
