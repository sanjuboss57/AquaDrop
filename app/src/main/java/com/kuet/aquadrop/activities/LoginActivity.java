package com.kuet.aquadrop.activities;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.utils.FirebaseManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class LoginActivity extends AppCompatActivity {

    public static final String APP_DOWNLOAD_URL = "https://files.catbox.moe/xb1dae.apk";

    private TextInputEditText etPhone;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private TextView tvGoToRegister;
    private MaterialCardView cardAppDownloadQr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupListeners();

        // Check if redirected from registration with prefilled phone
        if (getIntent() != null && getIntent().hasExtra("prefillPhone")) {
            String prefilled = getIntent().getStringExtra("prefillPhone");
            if (!TextUtils.isEmpty(prefilled)) {
                etPhone.setText(prefilled);
                etPassword.requestFocus();
            }
        }
    }

    private void initViews() {
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBarLogin);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        cardAppDownloadQr = findViewById(R.id.cardAppDownloadQr);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });

        tvGoToRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });

        if (cardAppDownloadQr != null) {
            cardAppDownloadQr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAppDownloadQrDialog();
                }
            });
        }
    }

    /**
     * Displays a dialog containing a 500x500 QR Code linking to the APK download,
     * allowing anyone with the app to share it with classmates, teachers, or delivery drivers.
     */
    private void showAppDownloadQrDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_app_download_qr);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView ivQr = dialog.findViewById(R.id.ivAppDownloadQr);
        TextView tvUrl = dialog.findViewById(R.id.tvDownloadUrl);
        View llCopy = dialog.findViewById(R.id.llCopyLink);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSaveQrToGallery);
        MaterialButton btnShare = dialog.findViewById(R.id.btnShareApk);
        MaterialButton btnClose = dialog.findViewById(R.id.btnCloseAppQrDialog);

        tvUrl.setText(APP_DOWNLOAD_URL);

        // Generate 500x500 QR Code Bitmap using ZXing
        Bitmap qrBitmap = null;
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            qrBitmap = barcodeEncoder.encodeBitmap(APP_DOWNLOAD_URL, BarcodeFormat.QR_CODE, 500, 500);
            ivQr.setImageBitmap(qrBitmap);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not render QR code: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        final Bitmap finalQrBitmap = qrBitmap;

        // Copy download URL to clipboard
        View.OnClickListener copyListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("AquaDrop APK Link", APP_DOWNLOAD_URL);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(LoginActivity.this, "Download link copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            }
        };
        llCopy.setOnClickListener(copyListener);

        // Save QR Bitmap to device Gallery / Downloads
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveQrBitmapToGallery(finalQrBitmap);
            }
        });

        // Share APK link via Android system sheet
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Install AquaDrop KUET");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Download & Install AquaDrop (KUET Drinking Water Logistics):\n" + APP_DOWNLOAD_URL);
                startActivity(Intent.createChooser(shareIntent, "Share AquaDrop APK"));
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * Saves the generated QR code bitmap to the device's public storage so the user can keep or print it.
     */
    private void saveQrBitmapToGallery(Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, "QR image not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String filename = "AquaDrop_Install_QR_" + (System.currentTimeMillis() / 1000) + ".png";
            OutputStream fos = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AquaDrop");
                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri);
                }
            } else {
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File aquaDropFolder = new File(picturesDir, "AquaDrop");
                if (!aquaDropFolder.exists()) {
                    aquaDropFolder.mkdirs();
                }
                File imageFile = new File(aquaDropFolder, filename);
                fos = new FileOutputStream(imageFile);
            }

            if (fos != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();
                Toast.makeText(this, "QR Code saved to Pictures/AquaDrop!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to open storage output stream", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not save QR code: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String normalizePhone(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("880") && digits.length() >= 13) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private void attemptLogin() {
        final String input = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        final String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(input)) {
            etPhone.setError("Phone number is required");
            etPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        setLoadingState(true);

        // Smart Authentication: check if input is an email address (backward-compatibility) or phone number
        if (input.contains("@")) {
            signInWithEmailDirectly(input, password);
        } else {
            final String normalizedPhone = normalizePhone(input);
            if (normalizedPhone.length() < 10) {
                setLoadingState(false);
                etPhone.setError("Please enter a valid 11-digit phone number");
                etPhone.requestFocus();
                return;
            }

            final String syntheticEmail = normalizedPhone + "@aquadrop.kuet.ac.bd";

            // 1. Direct fast login using synthetic phone email
            FirebaseManager.getInstance().getAuth().signInWithEmailAndPassword(syntheticEmail, password)
                    .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<AuthResult>() {
                        @Override
                        public void onSuccess(AuthResult authResult) {
                            FirebaseUser firebaseUser = authResult.getUser();
                            if (firebaseUser != null) {
                                fetchUserRoleAndNavigate(firebaseUser.getUid());
                            } else {
                                setLoadingState(false);
                                Toast.makeText(LoginActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                        @Override
                        public void onFailure(final Exception directErr) {
                            // 2. Fallback: Check if user registered previously with a custom email but has this phone stored
                            FirebaseManager.getInstance().getUsersRef()
                                    .whereEqualTo("phone", normalizedPhone)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<QuerySnapshot>() {
                                        @Override
                                        public void onSuccess(QuerySnapshot querySnapshot) {
                                            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                                DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                                                String registeredEmail = doc.getString("email");
                                                if (!TextUtils.isEmpty(registeredEmail)) {
                                                    signInWithEmailDirectly(registeredEmail, password);
                                                    return;
                                                }
                                            }
                                            setLoadingState(false);
                                            Toast.makeText(LoginActivity.this, "Invalid phone number or password.", Toast.LENGTH_LONG).show();
                                        }
                                    })
                                    .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                                        @Override
                                        public void onFailure(Exception e) {
                                            setLoadingState(false);
                                            Toast.makeText(LoginActivity.this, "Login Failed: " + directErr.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    });
        }
    }

    private void signInWithEmailDirectly(String email, String password) {
        FirebaseManager.getInstance().getAuth().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        FirebaseUser firebaseUser = authResult.getUser();
                        if (firebaseUser != null) {
                            fetchUserRoleAndNavigate(firebaseUser.getUid());
                        } else {
                            setLoadingState(false);
                            Toast.makeText(LoginActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        setLoadingState(false);
                        Toast.makeText(LoginActivity.this, "Login Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void fetchUserRoleAndNavigate(String uid) {
        FirebaseManager.getInstance().getUsersRef().document(uid).get()
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        setLoadingState(false);
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            User user = documentSnapshot.toObject(User.class);
                            if (user != null && User.ROLE_SELLER.equalsIgnoreCase(user.getRole())) {
                                routeTo(SellerDashboardActivity.class);
                            } else {
                                routeTo(BuyerDashboardActivity.class);
                            }
                        } else {
                            // Default fallback if profile incomplete
                            routeTo(BuyerDashboardActivity.class);
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        setLoadingState(false);
                        Toast.makeText(LoginActivity.this, "Profile query failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        // Proceed to default dashboard
                        routeTo(BuyerDashboardActivity.class);
                    }
                });
    }

    private void routeTo(Class<?> target) {
        Intent intent = new Intent(LoginActivity.this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoadingState(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        etPhone.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
        tvGoToRegister.setEnabled(!isLoading);
        if (cardAppDownloadQr != null) {
            cardAppDownloadQr.setEnabled(!isLoading);
        }
    }
}


