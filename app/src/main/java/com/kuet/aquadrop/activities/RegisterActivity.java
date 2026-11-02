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
    private LinearLayout llSellerAccessSection;
    private TextInputEditText etSellerAccessKey;
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
        llSellerAccessSection = findViewById(R.id.llSellerAccessSection);
        etSellerAccessKey = findViewById(R.id.etSellerAccessKey);
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
                    if (llSellerAccessSection != null) {
                        llSellerAccessSection.setVisibility(checkedId == R.id.rbSeller ? View.VISIBLE : View.GONE);
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

        // Seller Special Authorization Verification
        final String sellerKeyInput;
        if (rbSeller.isChecked()) {
            String enteredKey = etSellerAccessKey != null && etSellerAccessKey.getText() != null
                    ? etSellerAccessKey.getText().toString().trim() : "";
            if (TextUtils.isEmpty(enteredKey)) {
                if (etSellerAccessKey != null) {
                    etSellerAccessKey.setError("Authority Special Access Key required for seller registration");
                    etSellerAccessKey.requestFocus();
                }
                Toast.makeText(this, "Special Access Key is required for Seller accounts", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isValidSellerAccessKey(enteredKey)) {
                if (etSellerAccessKey != null) {
                    etSellerAccessKey.setError("Invalid Access Key! Contact AquaDrop Authority (KUET).");
                    etSellerAccessKey.requestFocus();
                }
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("🔒 Special Access Restricted")
                        .setMessage("Seller / Van accounts are restricted to authorized KUET depot personnel.\n\nYou must possess an official Special Access Key provided by the AquaDrop Authority to create a seller account.\n\nPlease contact administration or select 'Buyer / Consumer' to register.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
            sellerKeyInput = enteredKey;
        } else {
            sellerKeyInput = "";
        }

        // Selected KUET Location and GPS coordinates
        final String selectedLocation = (String) spinnerLocation.getSelectedItem();
        final LatLng latLng = KuetLocations.getLatLngByName(selectedLocation);

        final int finalFloorNumber = floorNumber;
        final String finalRoom = roomOrFlat;

        // Fast Phone-Based Auth Email
        final String authEmail = normalizedPhone + "@aquadrop.kuet.ac.bd";

        setLoadingState(true);

        FirebaseManager.getInstance().getAuth().createUserWithEmailAndPassword(authEmail, password)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        FirebaseUser firebaseUser = authResult.getUser();
                        if (firebaseUser != null) {
                            String uid = firebaseUser.getUid();

                            // Construct User Model with normalized phone
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
                                    0,      // initial empty jars held
                                    0.0     // initial deposit balance
                            );
                            newUser.setSellerAccessKey(sellerKeyInput);
                            newUser.setSellerAuthorized(rbSeller.isChecked());

                            saveUserToFirestore(newUser);
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
                            // User exists in Firebase Auth: seamlessly authenticate & update registration document
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
                                                updatedUser.setSellerAccessKey(sellerKeyInput);
                                                updatedUser.setSellerAuthorized(rbSeller.isChecked());
                                                saveUserToFirestore(updatedUser);
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
                                            Toast.makeText(RegisterActivity.this, "This phone is already registered. Redirecting to login...", Toast.LENGTH_LONG).show();
                                            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                            intent.putExtra("prefillPhone", normalizedPhone);
                                            startActivity(intent);
                                            finish();
                                        }
                                    });
                        } else {
                            setLoadingState(false);
                            Toast.makeText(RegisterActivity.this, "Registration Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveUserToFirestore(final User user) {
        FirebaseManager.getInstance().getUsersRef().document(user.getUid()).set(user)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
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
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        setLoadingState(false);
                        Toast.makeText(RegisterActivity.this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        if (etSellerAccessKey != null) {
            etSellerAccessKey.setEnabled(!isLoading);
        }
        tvGoToLogin.setEnabled(!isLoading);
    }

    public static boolean isValidSellerAccessKey(String key) {
        if (key == null) return false;
        String clean = key.trim().toUpperCase(Locale.ROOT);
        return clean.equals("AQUADROP_SELLER_2026")
                || clean.equals("KUET_ADMIN_AQUA")
                || clean.equals("AQUA_SELLER_KEY")
                || clean.equals("AQUA2026")
                || clean.equals("SELLER77")
                || clean.equals("KUETSELLER")
                || clean.equals("AQUA_SELLER")
                || clean.equals("SELLER123");
    }
}
