package com.kuet.aquadrop.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.kuet.aquadrop.utils.FirebaseManager;

/**
 * Lightweight background presence service that monitors when the Seller
 * app is removed from Android recents (swiped away / force-killed).
 * Automatically sets readyForSupply = false across Realtime Database and Cloud Firestore.
 */
public class SellerPresenceService extends Service {

    private static final String TAG = "SellerPresenceService";
    private String sellerUid;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("sellerUid")) {
            sellerUid = intent.getStringExtra("sellerUid");
        }
        if (TextUtils.isEmpty(sellerUid)) {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                sellerUid = u.getUid();
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Seller app swiped away / task removed! Turning off readyForSupply.");
        setSellerOffline();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        setSellerOffline();
        super.onDestroy();
    }

    private void setSellerOffline() {
        if (TextUtils.isEmpty(sellerUid)) {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                sellerUid = u.getUid();
            }
        }

        if (!TextUtils.isEmpty(sellerUid)) {
            try {
                // 1. Firebase Realtime Database online_sellers
                FirebaseManager.getInstance().getDatabase().getReference("online_sellers")
                        .child(sellerUid)
                        .child("readyForSupply")
                        .setValue(false);

                // 2. Firebase Realtime Database users
                FirebaseManager.getInstance().getRtdbUsersRef()
                        .child(sellerUid)
                        .child("readyForSupply")
                        .setValue(false);

                // 3. Cloud Firestore users
                FirebaseManager.getInstance().getUsersRef()
                        .document(sellerUid)
                        .update("readyForSupply", false);

                Log.d(TAG, "Seller " + sellerUid + " flipped OFFLINE successfully on app cutoff.");
            } catch (Exception e) {
                Log.w(TAG, "Error flipping seller offline: " + e.getMessage());
            }
        }
    }
}

