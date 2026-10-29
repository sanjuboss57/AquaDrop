package com.kuet.aquadrop.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.utils.FirebaseManager;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 1200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAuthSession();
            }
        }, SPLASH_DELAY_MS);
    }

    private void checkAuthSession() {
        FirebaseUser currentUser = FirebaseManager.getInstance().getCurrentUser();

        if (currentUser == null) {
            // Not authenticated, navigate to Login
            navigateTo(LoginActivity.class);
            return;
        }

        // Fetch user profile from Firestore to determine role
        FirebaseManager.getInstance().getUsersRef().document(currentUser.getUid()).get()
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            User user = documentSnapshot.toObject(User.class);
                            if (user != null && User.ROLE_SELLER.equalsIgnoreCase(user.getRole())) {
                                navigateTo(SellerDashboardActivity.class);
                            } else {
                                navigateTo(BuyerDashboardActivity.class);
                            }
                        } else {
                            // User record missing, require re-authentication
                            navigateTo(LoginActivity.class);
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(SplashActivity.this, "Session error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        navigateTo(LoginActivity.class);
                    }
                });
    }

    private void navigateTo(Class<?> target) {
        Intent intent = new Intent(SplashActivity.this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

