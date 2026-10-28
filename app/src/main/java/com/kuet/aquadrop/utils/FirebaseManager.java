package com.kuet.aquadrop.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

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
}
