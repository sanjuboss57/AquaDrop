package com.kuet.aquadrop.utils;

import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.models.WaterOrder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton wrapper providing unified access to Firebase 3-tier services:
 * 1. Authentication (FirebaseAuth)
 * 2. Cloud Firestore (users, products, orders, water_quality)
 * 3. Realtime Database (active_deliveries GPS telemetry)
 */
public class FirebaseManager {

    public static final String DATABASE_URL = "https://aquadrop-kuet-default-rtdb.firebaseio.com";

    // Firestore Collections
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_PRODUCTS = "products";
    public static final String COLLECTION_ORDERS = "orders";
    public static final String COLLECTION_WATER_QUALITY = "water_quality";

    // Realtime Database Paths
    public static final String PATH_ACTIVE_DELIVERIES = "active_deliveries";

    private static volatile FirebaseManager instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseDatabase database;

    private FirebaseManager() {
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();

        FirebaseDatabase rtdbInstance;
        try {
            rtdbInstance = FirebaseDatabase.getInstance(DATABASE_URL);
        } catch (Exception e) {
            rtdbInstance = FirebaseDatabase.getInstance();
        }
        this.database = rtdbInstance;
    }

    public static FirebaseManager getInstance() {
        if (instance == null) {
            synchronized (FirebaseManager.class) {
                if (instance == null) {
                    instance = new FirebaseManager();
                }
            }
        }
        return instance;
    }

    // Auth Accessors
    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    public boolean isUserLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public void signOut() {
        auth.signOut();
    }

    // Firestore Accessors
    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    public CollectionReference getUsersRef() {
        return firestore.collection(COLLECTION_USERS);
    }

    public CollectionReference getProductsRef() {
        return firestore.collection(COLLECTION_PRODUCTS);
    }

    public CollectionReference getOrdersRef() {
        return firestore.collection(COLLECTION_ORDERS);
    }

    public CollectionReference getWaterQualityRef() {
        return firestore.collection(COLLECTION_WATER_QUALITY);
    }

    // Realtime Database Accessors
    public FirebaseDatabase getDatabase() {
        return database;
    }

    public DatabaseReference getActiveDeliveriesRef() {
        return database.getReference(PATH_ACTIVE_DELIVERIES);
    }

    public DatabaseReference getTelemetryRef(String orderId) {
        return database.getReference(PATH_ACTIVE_DELIVERIES).child(orderId);
    }

    public DatabaseReference getRtdbUsersRef() {
        return database.getReference(COLLECTION_USERS);
    }

    public DatabaseReference getRtdbOrdersRef() {
        return database.getReference(COLLECTION_ORDERS);
    }

    public DatabaseReference getRtdbWaterQualityRef() {
        return database.getReference(COLLECTION_WATER_QUALITY);
    }

    /**
     * Dual-storage save for User profile: writes to both Firebase Realtime Database
     * and Cloud Firestore. Fires onSuccess as soon as at least one succeeds.
     */
    public void saveUser(final User user, final OnSuccessListener<Void> success, final OnFailureListener failure) {
        if (user == null || user.getUid() == null) {
            if (failure != null) failure.onFailure(new IllegalArgumentException("User or UID cannot be null"));
            return;
        }

        final AtomicBoolean hasNotified = new AtomicBoolean(false);

        // 1. Write to Realtime Database
        getRtdbUsersRef().child(user.getUid()).setValue(user)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (hasNotified.compareAndSet(false, true)) {
                            if (success != null) success.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w("FirebaseManager", "RTDB saveUser warning: " + e.getMessage());
                    }
                });

        // 2. Write to Cloud Firestore
        getUsersRef().document(user.getUid()).set(user)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (hasNotified.compareAndSet(false, true)) {
                            if (success != null) success.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w("FirebaseManager", "Firestore saveUser warning: " + e.getMessage());
                        // If RTDB also failed and hasNotified is still false after a short delay
                    }
                });
    }

    /**
     * Dual-storage save for WaterOrder: writes to both Firebase Realtime Database
     * and Cloud Firestore. Fires onSuccess immediately upon first successful write.
     */
    public void saveOrder(final WaterOrder order, final OnSuccessListener<Void> success, final OnFailureListener failure) {
        if (order == null || order.getOrderId() == null) {
            if (failure != null) failure.onFailure(new IllegalArgumentException("Order or OrderId cannot be null"));
            return;
        }

        final AtomicBoolean hasNotified = new AtomicBoolean(false);

        // 1. Realtime Database Write
        getRtdbOrdersRef().child(order.getOrderId()).setValue(order)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (hasNotified.compareAndSet(false, true)) {
                            if (success != null) success.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w("FirebaseManager", "RTDB saveOrder warning: " + e.getMessage());
                    }
                });

        // 2. Cloud Firestore Write
        getOrdersRef().document(order.getOrderId()).set(order)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (hasNotified.compareAndSet(false, true)) {
                            if (success != null) success.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w("FirebaseManager", "Firestore saveOrder warning: " + e.getMessage());
                    }
                });
    }

    /**
     * Dual-storage update for order fields (status, sellerId, prices, etc.).
     */
    public void updateOrderFields(final String orderId, final Map<String, Object> updates,
                                  final OnSuccessListener<Void> success, final OnFailureListener failure) {
        if (orderId == null || updates == null) {
            if (failure != null) failure.onFailure(new IllegalArgumentException("OrderId and updates required"));
            return;
        }

        final AtomicBoolean hasNotified = new AtomicBoolean(false);

        // Realtime Database update
        getRtdbOrdersRef().child(orderId).updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (hasNotified.compareAndSet(false, true)) {
                            if (success != null) success.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w("FirebaseManager", "RTDB updateOrderFields warning: " + e.getMessage());
                    }
                });

        // Firestore update
        getOrdersRef().document(orderId).update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (hasNotified.compareAndSet(false, true)) {
                            if (success != null) success.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w("FirebaseManager", "Firestore updateOrderFields warning: " + e.getMessage());
                    }
                });
    }

    /**
     * Dual-storage update for buyer bottle ledger (emptyJarsHeld).
     */
    public void updateBuyerEmptyJars(final String buyerUid, final long newHeld,
                                     final OnSuccessListener<Void> success, final OnFailureListener failure) {
        if (buyerUid == null) return;

        // Realtime Database
        getRtdbUsersRef().child(buyerUid).child("emptyJarsHeld").setValue(newHeld);

        // Firestore
        getUsersRef().document(buyerUid).update("emptyJarsHeld", newHeld)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (success != null) success.onSuccess(null);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w("FirebaseManager", "Firestore updateBuyerEmptyJars warning: " + e.getMessage());
                        if (success != null) success.onSuccess(null); // RTDB write already dispatched
                    }
                });
    }

    public interface OnWipeCompleteListener {
        void onSuccess(int deletedUsersCount, int deletedOrdersCount);
        void onFailure(Exception e);
    }

    /**
     * Wipes all user registration documents and orders from both Cloud Firestore
     * and Firebase Realtime Database. Signs out current user session.
     */
    public void wipeAllRegistrationDocuments(final OnWipeCompleteListener listener) {
        // 1. Wipe Realtime Database
        try {
            getRtdbUsersRef().removeValue();
            getRtdbOrdersRef().removeValue();
            getActiveDeliveriesRef().removeValue();
            Log.d("FirebaseManager", "Realtime Database users, orders, and telemetry cleared.");
        } catch (Exception e) {
            Log.w("FirebaseManager", "RTDB wipe error: " + e.getMessage());
        }

        // 2. Wipe Firestore (resilient if enabled, ignored if disabled)
        try {
            getUsersRef().get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                @Override
                public void onSuccess(final QuerySnapshot userSnapshots) {
                    final int usersCount = userSnapshots != null ? userSnapshots.size() : 0;
                    final WriteBatch batch = firestore.batch();
                    if (userSnapshots != null) {
                        for (DocumentSnapshot doc : userSnapshots.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                    }

                    getOrdersRef().get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                        @Override
                        public void onSuccess(final QuerySnapshot orderSnapshots) {
                            final int ordersCount = orderSnapshots != null ? orderSnapshots.size() : 0;
                            if (orderSnapshots != null) {
                                for (DocumentSnapshot doc : orderSnapshots.getDocuments()) {
                                    batch.delete(doc.getReference());
                                }
                            }

                            batch.commit().addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    finishWipeAndSignOut(usersCount, ordersCount, listener);
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(Exception e) {
                                    finishWipeAndSignOut(0, 0, listener);
                                }
                            });
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            finishWipeAndSignOut(0, 0, listener);
                        }
                    });
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    finishWipeAndSignOut(0, 0, listener);
                }
            });
        } catch (Exception e) {
            finishWipeAndSignOut(0, 0, listener);
        }
    }

    private void finishWipeAndSignOut(int usersCount, int ordersCount, OnWipeCompleteListener listener) {
        try {
            FirebaseUser current = auth.getCurrentUser();
            if (current != null) {
                try {
                    current.delete();
                } catch (Exception ignored) {}
            }
            auth.signOut();
        } catch (Exception ignored) {}

        if (listener != null) {
            listener.onSuccess(usersCount, ordersCount);
        }
    }
}
