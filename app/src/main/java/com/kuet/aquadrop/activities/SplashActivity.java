package com.kuet.aquadrop.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.utils.FirebaseManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 1000;
    private static final String PREF_NAME = "AquaDropPrefs";
    private static final String KEY_CLEAN_SLATE = "clean_slate_reset_v6";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        final SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        final boolean isCleanSlateDone = prefs.getBoolean(KEY_CLEAN_SLATE, false);

        if (!isCleanSlateDone) {
            // Automatic silent database & login reset for fresh registration
            Log.d("SplashActivity", "Executing clean slate reset: signing out & wiping registration data.");
            try {
                FirebaseManager.getInstance().wipeAllRegistrationDocuments(null);
                FirebaseManager.getInstance().signOut();
            } catch (Exception e) {
                Log.w("SplashActivity", "Clean slate error: " + e.getMessage());
            }

            prefs.edit().putBoolean(KEY_CLEAN_SLATE, true).apply();

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    navigateTo(LoginActivity.class);
                }
            }, SPLASH_DELAY_MS);
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAuthSession();
            }
        }, SPLASH_DELAY_MS);
    }

    private void checkAuthSession() {
        final FirebaseUser currentUser = FirebaseManager.getInstance().getCurrentUser();

        if (currentUser == null) {
            navigateTo(LoginActivity.class);
            return;
        }

        final String uid = currentUser.getUid();
        final AtomicBoolean navigated = new AtomicBoolean(false);

        // 1. Check Realtime Database first (guaranteed online access)
        FirebaseManager.getInstance().getRtdbUsersRef().child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            User user = User.fromDataSnapshot(snapshot);
                            if (user != null && navigated.compareAndSet(false, true)) {
                                routeUser(user);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // 2. Also check Firestore as fallback/parallel
        try {
            FirebaseManager.getInstance().getUsersRef().document(uid).get()
                    .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            if (documentSnapshot != null && documentSnapshot.exists()) {
                                User user = documentSnapshot.toObject(User.class);
                                if (user != null && navigated.compareAndSet(false, true)) {
                                    routeUser(user);
                                }
                            }
                        }
                    })
                    .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w("SplashActivity", "Firestore profile check note: " + e.getMessage());
                        }
                    });
        } catch (Exception ignored) {}

        // Timeout fallback if profile isn't found within 3.5 seconds
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (navigated.compareAndSet(false, true)) {
                    navigateTo(LoginActivity.class);
                }
            }
        }, 3500);
    }

    private void routeUser(User user) {
        if (user != null && User.ROLE_SELLER.equalsIgnoreCase(user.getRole())) {
            navigateTo(SellerDashboardActivity.class);
        } else {
            navigateTo(BuyerDashboardActivity.class);
        }
    }

    private void navigateTo(Class<?> target) {
        Intent intent = new Intent(SplashActivity.this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
