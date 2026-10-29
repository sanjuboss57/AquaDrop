package com.kuet.aquadrop.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.utils.FirebaseManager;
import com.kuet.aquadrop.utils.KuetLocations;

import java.util.List;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etFullName;
    private TextInputEditText etRegEmail;
    private TextInputEditText etRegPhone;
    private TextInputEditText etRegPassword;
    private RadioGroup rgRole;
    private RadioButton rbBuyer;
    private RadioButton rbSeller;
    private Spinner spinnerLocation;
    private TextInputEditText etRoomOrFlat;
    private TextInputEditText etFloorNumber;
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
        etRegEmail = findViewById(R.id.etRegEmail);
        etRegPhone = findViewById(R.id.etRegPhone);
        etRegPassword = findViewById(R.id.etRegPassword);
        rgRole = findViewById(R.id.rgRole);
        rbBuyer = findViewById(R.id.rbBuyer);
        rbSeller = findViewById(R.id.rbSeller);
        spinnerLocation = findViewById(R.id.spinnerLocation);
        etRoomOrFlat = findViewById(R.id.etRoomOrFlat);
        etFloorNumber = findViewById(R.id.etFloorNumber);
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

    private void attemptRegistration() {
        final String name = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        final String email = etRegEmail.getText() != null ? etRegEmail.getText().toString().trim() : "";
        final String phone = etRegPhone.getText() != null ? etRegPhone.getText().toString().trim() : "";
        final String password = etRegPassword.getText() != null ? etRegPassword.getText().toString().trim() : "";
        final String roomOrFlat = etRoomOrFlat.getText() != null ? etRoomOrFlat.getText().toString().trim() : "";
        final String floorStr = etFloorNumber.getText() != null ? etFloorNumber.getText().toString().trim() : "0";

        // Field Validation
        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etRegEmail.setError("Email is required");
            etRegEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etRegEmail.setError("Please enter a valid email address");
            etRegEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            etRegPhone.setError("Phone number is required");
            etRegPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etRegPassword.setError("Password must be at least 6 characters");
            etRegPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(roomOrFlat)) {
            etRoomOrFlat.setError("Room or house number is required");
            etRoomOrFlat.requestFocus();
            return;
        }

        int floorNumber = 0;
        try {
            floorNumber = Integer.parseInt(floorStr);
            if (floorNumber < 0 || floorNumber > 6) {
                etFloorNumber.setError("Floor must be between 0 (Ground) and 6");
                etFloorNumber.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            etFloorNumber.setError("Invalid floor number");
            etFloorNumber.requestFocus();
            return;
        }

        // Determine Selected Role
        final String role = rbSeller.isChecked() ? User.ROLE_SELLER : User.ROLE_BUYER;

        // Selected KUET Location and GPS coordinates
        final String selectedLocation = (String) spinnerLocation.getSelectedItem();
        final LatLng latLng = KuetLocations.getLatLngByName(selectedLocation);

        final int finalFloorNumber = floorNumber;

        setLoadingState(true);

        FirebaseManager.getInstance().getAuth().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        FirebaseUser firebaseUser = authResult.getUser();
                        if (firebaseUser != null) {
                            String uid = firebaseUser.getUid();

                            // Construct User Model
                            User newUser = new User(
                                    uid,
                                    name,
                                    email,
                                    phone,
                                    role,
                                    selectedLocation,
                                    roomOrFlat,
                                    finalFloorNumber,
                                    latLng.latitude,
                                    latLng.longitude,
                                    0,      // initial empty jars held
                                    0.0     // initial deposit balance
                            );

                            saveUserToFirestore(newUser);
                        } else {
                            setLoadingState(false);
                            Toast.makeText(RegisterActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        setLoadingState(false);
                        Toast.makeText(RegisterActivity.this, "Registration Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(final User user) {
        FirebaseManager.getInstance().getUsersRef().document(user.getUid()).set(user)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        setLoadingState(false);
                        Toast.makeText(RegisterActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();

                        Class<?> targetActivity = user.isSeller()
                                ? SellerDashboardActivity.class
                                : BuyerDashboardActivity.class;

                        Intent intent = new Intent(RegisterActivity.this, targetActivity);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
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
        etRegEmail.setEnabled(!isLoading);
        etRegPhone.setEnabled(!isLoading);
        etRegPassword.setEnabled(!isLoading);
        etRoomOrFlat.setEnabled(!isLoading);
        etFloorNumber.setEnabled(!isLoading);
        spinnerLocation.setEnabled(!isLoading);
        rbBuyer.setEnabled(!isLoading);
        rbSeller.setEnabled(!isLoading);
        tvGoToLogin.setEnabled(!isLoading);
    }
}

