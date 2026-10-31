package com.kuet.aquadrop.utils;

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

    public interface OnWipeCompleteListener {
        void onSuccess(int deletedUsersCount, int deletedOrdersCount);
        void onFailure(Exception e);
    }

    /**
     * Wipes all user registration documents and orders from Cloud Firestore.
     * Also deletes the current user's Auth account if signed in, and signs out.
     */
    public void wipeAllRegistrationDocuments(final OnWipeCompleteListener listener) {
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
                                FirebaseUser current = auth.getCurrentUser();
                                if (current != null) {
                                    try {
                                        current.delete();
                                    } catch (Exception ignored) {}
                                }
                                auth.signOut();

                                if (listener != null) {
                                    listener.onSuccess(usersCount, ordersCount);
                                }
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                if (listener != null) listener.onFailure(e);
                            }
                        });
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        if (listener != null) listener.onFailure(e);
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                if (listener != null) listener.onFailure(e);
            }
        });
    }
}
