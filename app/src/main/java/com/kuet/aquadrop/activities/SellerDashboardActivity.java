package com.kuet.aquadrop.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.adapters.SellerOrderAdapter;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.models.WaterOrder;
import com.kuet.aquadrop.models.WaterQuality;
import com.kuet.aquadrop.receivers.DeliveryStatusReceiver;
import com.kuet.aquadrop.services.LocationBroadcastService;
import com.kuet.aquadrop.utils.FirebaseManager;
import com.kuet.aquadrop.utils.KuetLocations;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class SellerDashboardActivity extends AppCompatActivity implements SellerOrderAdapter.OnSellerOrderActionListener {

    // Summary Card Views
    private TextView tvSummaryTodayDelivered;
    private TextView tvSummaryTodayEarnings;
    private TextView tvSummaryPendingOrders;
    private TextView tvSummaryActiveDeliveries;
    private TextView tvSummaryLifetimeStats;
    private MaterialButton btnViewDailySalesHistory;
    private MaterialButton btnUpdateWaterQuality;
    private ImageView ivScanDeliveryQrHeader;
    private ImageView ivSellerProfile;
    private ImageView ivRefreshOrders;
    private ImageView ivSellerLogout;

    // Emergency Notice Views
    private MaterialCardView cardEmergencyNotice;
    private TextView tvEmergencyAlertSubtitle;
    private int lastKnownUrgentCount = -1;

    // Supply & Storage Control Views
    private com.google.android.material.switchmaterial.SwitchMaterial switchReadyForSupply;
    private TextView tvSupplyStatusBadge;
    private TextView tvSellerStorageStock;
    private MaterialButton btnUpdateStock;
    private TextView tvSellerAuthorCodeBadge;

    // Dispatch Queue Views
    private TextView tvOrderQueueCount;
    private TextView tvNoSellerOrders;
    private RecyclerView rvSellerOrders;
    private SellerOrderAdapter sellerOrderAdapter;

    // Feature 1: Hall-Wise Batch Filter Views & Dedicated Archive
    private Spinner spinnerHallFilter;
    private TextView tvHallBatchSummary;
    private MaterialButton btnDeliveredOrdersArchive;
    private String selectedHallFilter = "All KUET Locations";
    private List<WaterOrder> allOrdersList = new ArrayList<>();

    // State & Listeners
    private User currentSellerUser;
    private String currentSellerName = "Authorized Seller";
    private String currentSellerDepotTitle = "KUET Depot";
    private ListenerRegistration ordersListenerRegistration;
    private ValueEventListener rtdbOrdersListener;
    private final Map<String, WaterOrder> liveOrdersMap = new HashMap<>();
    private WaterOrder pendingScanOrder;
    private int lastKnownPendingCount = -1;
    private List<WaterOrder> cachedAllOrders = new ArrayList<>();

    // ZXing Scanner Activity Result Launcher
    private final ActivityResultLauncher<ScanOptions> qrScanLauncher = registerForActivityResult(
            new ScanContract(),
            new androidx.activity.result.ActivityResultCallback<ScanIntentResult>() {
                @Override
                public void onActivityResult(ScanIntentResult result) {
                    if (result != null && result.getContents() != null) {
                        String scannedToken = result.getContents().trim();
                        handleQrScanVerification(scannedToken);
                    } else {
                        Toast.makeText(SellerDashboardActivity.this, "Scan cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seller_dashboard);

        initViews();
        resetSessionSupplyStatus();
        setupRecyclerView();
        setupListeners();
        loadSellerProfile();
        subscribeToOrdersCollection();
    }

    /**
     * Session Start Reset: Guarantees that readyForSupply always starts as false (OFF)
     * whenever the seller opens the app, until they explicitly flip the toggle ON.
     */
    private void resetSessionSupplyStatus() {
        final String sellerUid = FirebaseManager.getInstance().getCurrentUserId();
        if (TextUtils.isEmpty(sellerUid)) return;

        if (currentSellerUser != null) {
            currentSellerUser.setReadyForSupply(false);
        }
        if (switchReadyForSupply != null) {
            switchReadyForSupply.setChecked(false);
        }
        if (tvSupplyStatusBadge != null) {
            tvSupplyStatusBadge.setText("OFFLINE");
            tvSupplyStatusBadge.setTextColor(Color.parseColor("#757575"));
            tvSupplyStatusBadge.setBackgroundColor(Color.parseColor("#EEEEEE"));
        }

        try {
            FirebaseManager.getInstance().getDatabase().getReference("online_sellers")
                    .child(sellerUid)
                    .child("readyForSupply")
                    .setValue(false);

            FirebaseManager.getInstance().getRtdbUsersRef()
                    .child(sellerUid)
                    .child("readyForSupply")
                    .setValue(false);

            FirebaseManager.getInstance().getUsersRef()
                    .document(sellerUid)
                    .update("readyForSupply", false);
        } catch (Exception e) {
            Log.w("SellerDashboard", "Error resetting session supply status: " + e.getMessage());
        }
    }

    private void loadSellerProfile() {
        String uid = FirebaseManager.getInstance().getCurrentUserId();
        if (TextUtils.isEmpty(uid)) return;

        // Fetch from Cloud Firestore
        FirebaseManager.getInstance().getUsersRef().document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot doc) {
                        if (doc != null && doc.exists()) {
                            currentSellerUser = doc.toObject(User.class);
                            if (currentSellerUser != null) {
                                // Enforce session-start offline default
                                currentSellerUser.setReadyForSupply(switchReadyForSupply != null && switchReadyForSupply.isChecked());
                                if (!TextUtils.isEmpty(currentSellerUser.getName())) {
                                    currentSellerName = currentSellerUser.getName();
                                }
                                if (!TextUtils.isEmpty(currentSellerUser.getDepotOrDriverTitle())) {
                                    currentSellerDepotTitle = currentSellerUser.getDepotOrDriverTitle();
                                }
                                updateSupplyUiState();
                            }
                        }
                    }
                });

        // Also fetch from Firebase Realtime Database
        FirebaseManager.getInstance().getRtdbUsersRef().child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            User u = User.fromDataSnapshot(snapshot);
                            if (u != null) {
                                currentSellerUser = u;
                                // Enforce session-start offline default
                                currentSellerUser.setReadyForSupply(switchReadyForSupply != null && switchReadyForSupply.isChecked());
                                if (!TextUtils.isEmpty(u.getName())) {
                                    currentSellerName = u.getName();
                                }
                                if (!TextUtils.isEmpty(u.getDepotOrDriverTitle())) {
                                    currentSellerDepotTitle = u.getDepotOrDriverTitle();
                                }
                                updateSupplyUiState();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateSupplyUiState() {
        if (currentSellerUser == null) return;
        boolean isOnline = currentSellerUser.isReadyForSupply();
        int stock = currentSellerUser.getAvailableStockJars();

        if (switchReadyForSupply != null) {
            switchReadyForSupply.setChecked(isOnline);
        }
        if (tvSupplyStatusBadge != null) {
            if (isOnline) {
                tvSupplyStatusBadge.setText("ONLINE - Taking Orders");
                tvSupplyStatusBadge.setTextColor(Color.parseColor("#2E7D32"));
                tvSupplyStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"));
            } else {
                tvSupplyStatusBadge.setText("OFFLINE");
                tvSupplyStatusBadge.setTextColor(Color.parseColor("#757575"));
                tvSupplyStatusBadge.setBackgroundColor(Color.parseColor("#EEEEEE"));
            }
        }
        if (tvSellerStorageStock != null) {
            tvSellerStorageStock.setText(String.format(Locale.getDefault(), "Storage: %d Jars Available", stock));
        }
        if (tvSellerAuthorCodeBadge != null && !TextUtils.isEmpty(currentSellerUser.getSellerAccessCode())) {
            tvSellerAuthorCodeBadge.setText("Author Code: " + currentSellerUser.getSellerAccessCode());
            tvSellerAuthorCodeBadge.setVisibility(View.VISIBLE);
        }
    }

    private void toggleReadyForSupply(final boolean isOnline) {
        final String sellerUid = FirebaseManager.getInstance().getCurrentUserId();
        if (TextUtils.isEmpty(sellerUid)) return;

        if (currentSellerUser != null) {
            currentSellerUser.setReadyForSupply(isOnline);
        }
        updateSupplyUiState();

        final DatabaseReference onlineRef = FirebaseManager.getInstance().getDatabase().getReference("online_sellers").child(sellerUid);
        final DatabaseReference userRef = FirebaseManager.getInstance().getRtdbUsersRef().child(sellerUid);

        if (isOnline) {
            // Firebase Realtime Database onDisconnect hook (handles force-close, swipe away, or network cutoff)
            onlineRef.child("readyForSupply").onDisconnect().setValue(false);
            userRef.child("readyForSupply").onDisconnect().setValue(false);

            // Start SellerPresenceService (handles onTaskRemoved swipe away)
            try {
                Intent serviceIntent = new Intent(this, com.kuet.aquadrop.services.SellerPresenceService.class);
                serviceIntent.putExtra("sellerUid", sellerUid);
                startService(serviceIntent);
            } catch (Exception e) {
                Log.w("SellerDashboard", "Error starting SellerPresenceService: " + e.getMessage());
            }
        } else {
            // Cancel onDisconnect hook
            try {
                onlineRef.child("readyForSupply").onDisconnect().cancel();
                userRef.child("readyForSupply").onDisconnect().cancel();
            } catch (Exception ignored) {}

            // Stop SellerPresenceService
            try {
                stopService(new Intent(this, com.kuet.aquadrop.services.SellerPresenceService.class));
            } catch (Exception ignored) {}
        }

        // 1. Update Firestore users collection
        FirebaseManager.getInstance().getUsersRef().document(sellerUid)
                .update("readyForSupply", isOnline)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(SellerDashboardActivity.this,
                            isOnline ? "🟢 You are now ONLINE! Buyers can order from you." : "⚪ You are now OFFLINE.",
                            Toast.LENGTH_SHORT).show();
                });

        // 2. Realtime Database: Mirror in online_sellers for instantaneous buyer discovery
        final int stock = (currentSellerUser != null) ? currentSellerUser.getAvailableStockJars() : 50;
        final String depot = !TextUtils.isEmpty(currentSellerDepotTitle) ? currentSellerDepotTitle : "KUET Depot";
        Map<String, Object> sellerMap = new HashMap<>();
        sellerMap.put("sellerUid", sellerUid);
        sellerMap.put("name", currentSellerName);
        sellerMap.put("depotOrDriverTitle", depot);
        sellerMap.put("availableStockJars", stock);
        sellerMap.put("readyForSupply", isOnline);
        sellerMap.put("timestamp", System.currentTimeMillis());

        onlineRef.setValue(sellerMap);
        userRef.child("readyForSupply").setValue(isOnline);
    }

    private void showRestockDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int currentStock = (currentSellerUser != null) ? currentSellerUser.getAvailableStockJars() : 50;
        input.setText(String.valueOf(currentStock));
        input.setSelection(input.getText().length());

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = (int) (24 * getResources().getDisplayMetrics().density);
        params.rightMargin = (int) (24 * getResources().getDisplayMetrics().density);
        params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Update Delivery Storage Inventory")
                .setMessage("Enter the total 20L water jars currently loaded in your van / depot storage:")
                .setView(container)
                .setPositiveButton("Save Storage", (dialog, which) -> {
                    String val = input.getText() != null ? input.getText().toString().trim() : "";
                    try {
                        int newStock = Integer.parseInt(val);
                        if (newStock < 0) newStock = 0;
                        updateStorageStock(newStock);
                    } catch (NumberFormatException e) {
                        Toast.makeText(SellerDashboardActivity.this, "Invalid number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStorageStock(int newStock) {
        final String sellerUid = FirebaseManager.getInstance().getCurrentUserId();
        if (TextUtils.isEmpty(sellerUid)) return;

        if (currentSellerUser != null) {
            currentSellerUser.setAvailableStockJars(newStock);
            if (newStock <= 0) {
                currentSellerUser.setReadyForSupply(false);
            }
        }
        updateSupplyUiState();

        Map<String, Object> updates = new HashMap<>();
        updates.put("availableStockJars", newStock);
        if (newStock <= 0) {
            updates.put("readyForSupply", false);
        }

        // 1. Update Firestore
        FirebaseManager.getInstance().getUsersRef().document(sellerUid).update(updates);

        // 2. Update RTDB online_sellers
        FirebaseManager.getInstance().getDatabase().getReference("online_sellers")
                .child(sellerUid)
                .updateChildren(updates);

        Toast.makeText(this, "Storage updated: " + newStock + " jars available", Toast.LENGTH_SHORT).show();
    }

    private void initViews() {
        tvSummaryTodayDelivered = findViewById(R.id.tvSummaryTodayDelivered);
        tvSummaryTodayEarnings = findViewById(R.id.tvSummaryTodayEarnings);
        tvSummaryPendingOrders = findViewById(R.id.tvSummaryPendingOrders);
        tvSummaryActiveDeliveries = findViewById(R.id.tvSummaryActiveDeliveries);
        tvSummaryLifetimeStats = findViewById(R.id.tvSummaryLifetimeStats);
        btnViewDailySalesHistory = findViewById(R.id.btnViewDailySalesHistory);
        btnUpdateWaterQuality = findViewById(R.id.btnUpdateWaterQuality);

        ivScanDeliveryQrHeader = findViewById(R.id.ivScanDeliveryQrHeader);
        ivSellerProfile = findViewById(R.id.ivSellerProfile);
        ivRefreshOrders = findViewById(R.id.ivRefreshOrders);
        ivSellerLogout = findViewById(R.id.ivSellerLogout);

        cardEmergencyNotice = findViewById(R.id.cardEmergencyNotice);
        tvEmergencyAlertSubtitle = findViewById(R.id.tvEmergencyAlertSubtitle);

        switchReadyForSupply = findViewById(R.id.switchReadyForSupply);
        tvSupplyStatusBadge = findViewById(R.id.tvSupplyStatusBadge);
        tvSellerStorageStock = findViewById(R.id.tvSellerStorageStock);
        btnUpdateStock = findViewById(R.id.btnUpdateStock);
        tvSellerAuthorCodeBadge = findViewById(R.id.tvSellerAuthorCodeBadge);

        spinnerHallFilter = findViewById(R.id.spinnerHallFilter);
        tvHallBatchSummary = findViewById(R.id.tvHallBatchSummary);
        btnDeliveredOrdersArchive = findViewById(R.id.btnDeliveredOrdersArchive);
        setupHallFilterSpinner();

        tvOrderQueueCount = findViewById(R.id.tvOrderQueueCount);
        tvNoSellerOrders = findViewById(R.id.tvNoSellerOrders);
        rvSellerOrders = findViewById(R.id.rvSellerOrders);
    }

    private void setupHallFilterSpinner() {
        if (spinnerHallFilter == null) return;
        List<String> hallOptions = new ArrayList<>();
        hallOptions.add("All KUET Locations");
        hallOptions.addAll(KuetLocations.getLocationNames());

        ArrayAdapter<String> hallAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hallOptions);
        hallAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerHallFilter.setAdapter(hallAdapter);

        spinnerHallFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedHallFilter = (String) parent.getItemAtPosition(position);
                recalculateAndDisplayOrders();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupRecyclerView() {
        rvSellerOrders.setLayoutManager(new LinearLayoutManager(this));
        sellerOrderAdapter = new SellerOrderAdapter(this);
        rvSellerOrders.setAdapter(sellerOrderAdapter);
    }

    private void setupListeners() {
        if (switchReadyForSupply != null) {
            switchReadyForSupply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean targetState = switchReadyForSupply.isChecked();
                    int currentStock = (currentSellerUser != null) ? currentSellerUser.getAvailableStockJars() : 50;

                    if (targetState && currentStock <= 0) {
                        switchReadyForSupply.setChecked(false);
                        Toast.makeText(SellerDashboardActivity.this,
                                "Cannot go online: Delivery storage is empty (0 jars). Please restock first!", Toast.LENGTH_LONG).show();
                        showRestockDialog();
                        return;
                    }

                    toggleReadyForSupply(targetState);
                }
            });
        }

        if (btnUpdateStock != null) {
            btnUpdateStock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRestockDialog();
                }
            });
        }

        if (cardEmergencyNotice != null) {
            cardEmergencyNotice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (rvSellerOrders != null) {
                        rvSellerOrders.smoothScrollToPosition(0);
                    }
                }
            });
        }

        if (ivScanDeliveryQrHeader != null) {
            ivScanDeliveryQrHeader.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startScanAnyOrderQr();
                }
            });
        }

        if (ivSellerProfile != null) {
            ivSellerProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SellerDashboardActivity.this, ProfileActivity.class));
                }
            });
        }

        if (ivRefreshOrders != null) {
            ivRefreshOrders.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fetchOrdersDirectly(true);
                }
            });
        }

        if (btnViewDailySalesHistory != null) {
            btnViewDailySalesHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDailySalesHistoryDialog();
                }
            });
        }

        ivSellerLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String sellerUid = FirebaseManager.getInstance().getCurrentUserId();
                if (!TextUtils.isEmpty(sellerUid)) {
                    try {
                        FirebaseManager.getInstance().getDatabase().getReference("online_sellers").child(sellerUid).child("readyForSupply").setValue(false);
                        FirebaseManager.getInstance().getRtdbUsersRef().child(sellerUid).child("readyForSupply").setValue(false);
                        FirebaseManager.getInstance().getUsersRef().document(sellerUid).update("readyForSupply", false);
                        stopService(new Intent(SellerDashboardActivity.this, com.kuet.aquadrop.services.SellerPresenceService.class));
                    } catch (Exception ignored) {}
                }
                FirebaseManager.getInstance().signOut();
                Intent intent = new Intent(SellerDashboardActivity.this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        btnUpdateWaterQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUpdateWaterQualityDialog();
            }
        });

        if (btnDeliveredOrdersArchive != null) {
            btnDeliveredOrdersArchive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SellerDashboardActivity.this, DeliveredHistoryActivity.class));
                }
            });
        }
    }

    @Override
    public void onNotifyGateArrival(final WaterOrder order, final MaterialButton button) {
        if (order == null || TextUtils.isEmpty(order.getBuyerId())) return;

        final String buyerId = order.getBuyerId();
        final String orderId = order.getOrderId();
        final String locationName = !TextUtils.isEmpty(order.getLocationName()) ? order.getLocationName() : "Campus";
        final String message = "Your water delivery van has arrived at " + locationName + " gate for your order! Please keep your empty jars ready.";

        Map<String, Object> alertPayload = new HashMap<>();
        alertPayload.put("orderId", orderId);
        alertPayload.put("buyerId", buyerId);
        alertPayload.put("locationName", locationName);
        alertPayload.put("message", message);
        alertPayload.put("timestamp", System.currentTimeMillis());
        alertPayload.put("active", true);

        FirebaseManager.getInstance().getDatabase()
                .getReference("buyer_alerts")
                .child(buyerId)
                .child(orderId)
                .setValue(alertPayload)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        if (sellerOrderAdapter != null) {
                            sellerOrderAdapter.markGateAlertSent(orderId);
                        } else if (button != null) {
                            button.setText("Gate Alert Sent ✓");
                            button.setEnabled(false);
                        }
                        Toast.makeText(SellerDashboardActivity.this, "Gate Alert Sent to Buyer for Order #" + orderId + "!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(SellerDashboardActivity.this, "Failed to send gate alert: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ordersListenerRegistration == null && rtdbOrdersListener == null) {
            subscribeToOrdersCollection();
        } else {
            fetchOrdersDirectly(false);
        }
    }

    private void subscribeToOrdersCollection() {
        // 1. Clear previous Firestore listener if exists
        if (ordersListenerRegistration != null) {
            ordersListenerRegistration.remove();
            ordersListenerRegistration = null;
        }

        try {
            // Direct query to db.collection("orders") without whereEqualTo(buyerId/sellerId) and without compound orderBy
            ordersListenerRegistration = FirebaseFirestore.getInstance().collection("orders")
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                            if (error != null) {
                                Log.w("SellerDashboard", "Firestore Orders SnapshotListener note (RTDB active): " + error.getMessage());
                                return;
                            }

                            if (snapshots != null) {
                                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                                    WaterOrder order = WaterOrder.fromSnapshot(doc);
                                    if (order != null) {
                                        liveOrdersMap.put(order.getOrderId(), order);
                                    }
                                }
                                recalculateAndDisplayOrders();
                            }
                        }
                    });
        } catch (Exception e) {
            Log.w("SellerDashboard", "Firestore listener init note: " + e.getMessage());
        }

        // 2. Realtime Database Live Listener (Guarantees 100% online real-time sync)
        if (rtdbOrdersListener != null) {
            FirebaseManager.getInstance().getRtdbOrdersRef().removeEventListener(rtdbOrdersListener);
            rtdbOrdersListener = null;
        }

        rtdbOrdersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        WaterOrder order = WaterOrder.fromDataSnapshot(child);
                        if (order != null) {
                            liveOrdersMap.put(order.getOrderId(), order);
                        }
                    }
                }
                recalculateAndDisplayOrders();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w("SellerDashboard", "RTDB orders sync note: " + error.getMessage());
            }
        };

        FirebaseManager.getInstance().getRtdbOrdersRef().addValueEventListener(rtdbOrdersListener);
    }

    private void fetchOrdersDirectly(final boolean userInitiated) {
        if (userInitiated) {
            Toast.makeText(this, "Fetching live depot orders...", Toast.LENGTH_SHORT).show();
        }

        // Fetch from Realtime Database
        FirebaseManager.getInstance().getRtdbOrdersRef().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        WaterOrder order = WaterOrder.fromDataSnapshot(child);
                        if (order != null) {
                            liveOrdersMap.put(order.getOrderId(), order);
                        }
                    }
                    recalculateAndDisplayOrders();
                    if (userInitiated) {
                        Toast.makeText(SellerDashboardActivity.this,
                                "Orders synchronized (" + liveOrdersMap.size() + " active)", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w("SellerDashboard", "RTDB single fetch note: " + error.getMessage());
            }
        });

        // Also fetch from Firestore directly without compound queries
        try {
            FirebaseFirestore.getInstance().collection("orders").get()
                    .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                        @Override
                        public void onSuccess(QuerySnapshot snapshots) {
                            if (snapshots != null) {
                                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                                    WaterOrder order = WaterOrder.fromSnapshot(doc);
                                    if (order != null) {
                                        liveOrdersMap.put(order.getOrderId(), order);
                                    }
                                }
                                recalculateAndDisplayOrders();
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d("SellerDashboard", "Firestore server fetch note: " + e.getMessage());
                        }
                    });
        } catch (Exception ignored) {}
    }

    private void recalculateAndDisplayOrders() {
        // Deduplicate orders from liveOrdersMap
        Map<String, WaterOrder> deduplicatedMasterMap = new HashMap<>(liveOrdersMap);
        this.allOrdersList = new ArrayList<>(deduplicatedMasterMap.values());
        List<WaterOrder> orderList = this.allOrdersList;
        final String currentSellerUid = FirebaseManager.getInstance().getCurrentUserId();

        int pendingCount = 0;
        int activeDeliveriesCount = 0;
        int emptyCollectedCount = 0;
        int urgentPendingCount = 0;
        WaterOrder latestUrgentOrder = null;

        int todayDeliveredCount = 0;
        double todayEarnings = 0.0;
        int lifetimeDeliveredCount = 0;
        double lifetimeRevenue = 0.0;

        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        java.util.Set<String> processedOrderIds = new java.util.HashSet<>();

        for (WaterOrder order : orderList) {
            if (order != null && !TextUtils.isEmpty(order.getOrderId())) {
                if (processedOrderIds.contains(order.getOrderId())) {
                    continue;
                }
                processedOrderIds.add(order.getOrderId());

                String status = order.getStatus();
                String assigned = order.getAssignedSellerId();
                if (TextUtils.isEmpty(assigned)) assigned = order.getSellerId();

                boolean isAssignedToMe = (currentSellerUid != null && currentSellerUid.equals(assigned))
                        || (TextUtils.isEmpty(assigned) && WaterOrder.STATUS_PENDING.equalsIgnoreCase(status));

                if (!isAssignedToMe) {
                    continue;
                }

                boolean isUnclaimedPending = WaterOrder.STATUS_PENDING.equalsIgnoreCase(status);
                boolean isOwnClaimed = WaterOrder.STATUS_ACCEPTED.equalsIgnoreCase(status)
                        || WaterOrder.STATUS_OUT_FOR_DELIVERY.equalsIgnoreCase(status);
                boolean isOwnDelivered = WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(status);

                if ((isUnclaimedPending || isOwnClaimed) && order.isUrgent()) {
                    urgentPendingCount++;
                    if (latestUrgentOrder == null) {
                        latestUrgentOrder = order;
                    }
                }

                if (isUnclaimedPending) {
                    pendingCount++;
                } else if (isOwnClaimed) {
                    activeDeliveriesCount++;
                } else if (isOwnDelivered) {
                    emptyCollectedCount += order.getEmptyJarsToReturn();
                    lifetimeDeliveredCount++;
                    lifetimeRevenue += order.getTotalPrice();

                    // Check if delivered today
                    String orderDate = order.getDeliveredDate();
                    if (TextUtils.isEmpty(orderDate)) {
                        orderDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(order.getTimestamp()));
                    }
                    if (todayDate.equals(orderDate)) {
                        todayDeliveredCount++;
                        todayEarnings += order.getTotalPrice();
                    }
                }
            }
        }

        this.cachedAllOrders = new ArrayList<>(allOrdersList);

        // Emergency Alert Notice Banner on Seller Dashboard
        if (cardEmergencyNotice != null) {
            if (urgentPendingCount > 0) {
                cardEmergencyNotice.setVisibility(View.VISIBLE);
                if (tvEmergencyAlertSubtitle != null) {
                    tvEmergencyAlertSubtitle.setText(String.format(Locale.getDefault(),
                            "🚨 %d order(s) marked for EMERGENCY EXPRESS delivery! Immediate priority dispatch requested.",
                            urgentPendingCount));
                }
            } else {
                cardEmergencyNotice.setVisibility(View.GONE);
            }
        }

        // Sound chime / vibration / dialog when a new order arrives online
        if (lastKnownUrgentCount != -1 && urgentPendingCount > lastKnownUrgentCount && latestUrgentOrder != null) {
            notifyEmergencyOrderArrived(latestUrgentOrder);
        } else if (lastKnownPendingCount != -1 && pendingCount > lastKnownPendingCount) {
            notifyNewOrderArrived();
        }
        lastKnownPendingCount = pendingCount;
        lastKnownUrgentCount = urgentPendingCount;

        // Update Dashboard Metrics Cards
        if (tvSummaryTodayDelivered != null) {
            tvSummaryTodayDelivered.setText(String.format(Locale.getDefault(), "%d Orders", todayDeliveredCount));
        }
        if (tvSummaryTodayEarnings != null) {
            tvSummaryTodayEarnings.setText(String.format(Locale.getDefault(), "%.0f BDT", todayEarnings));
        }
        if (tvSummaryPendingOrders != null) {
            tvSummaryPendingOrders.setText(String.valueOf(pendingCount));
        }
        if (tvSummaryActiveDeliveries != null) {
            tvSummaryActiveDeliveries.setText(String.valueOf(activeDeliveriesCount));
        }
        if (tvSummaryLifetimeStats != null) {
            tvSummaryLifetimeStats.setText(String.format(Locale.getDefault(),
                    "Lifetime: %d deliveries completed • %.0f BDT total revenue • %d empty jars returned",
                    lifetimeDeliveredCount, lifetimeRevenue, emptyCollectedCount));
        }

        // Feature 1: Filter allOrdersList by selectedHallFilter & compute zone trip load
        final boolean isAllHalls = "All KUET Locations".equalsIgnoreCase(selectedHallFilter)
                || TextUtils.isEmpty(selectedHallFilter);

        final Map<String, WaterOrder> deduplicatedFilteredMap = new HashMap<>();
        int zonePendingCount = 0;
        int zoneLoadOnVanJars = 0;
        int zoneEmptyToCollectJars = 0;

        for (WaterOrder order : allOrdersList) {
            if (order == null || TextUtils.isEmpty(order.getOrderId())) continue;

            String st = order.getStatus();
            String assigned = order.getAssignedSellerId();
            if (TextUtils.isEmpty(assigned)) assigned = order.getSellerId();

            // Strictly filter so each seller only sees orders assigned to THEM (or pending unclaimed orders)
            boolean isAssignedToMe = (currentSellerUid != null && currentSellerUid.equals(assigned))
                    || (TextUtils.isEmpty(assigned) && WaterOrder.STATUS_PENDING.equalsIgnoreCase(st));

            boolean isActive = WaterOrder.STATUS_PENDING.equalsIgnoreCase(st)
                    || WaterOrder.STATUS_ACCEPTED.equalsIgnoreCase(st)
                    || WaterOrder.STATUS_OUT_FOR_DELIVERY.equalsIgnoreCase(st);

            if (!isAssignedToMe || !isActive) {
                continue;
            }

            boolean matchesZone = isAllHalls || (order.getLocationName() != null
                    && order.getLocationName().equalsIgnoreCase(selectedHallFilter));

            if (matchesZone && !deduplicatedFilteredMap.containsKey(order.getOrderId())) {
                deduplicatedFilteredMap.put(order.getOrderId(), order);
                if (WaterOrder.STATUS_PENDING.equalsIgnoreCase(st)) {
                    zonePendingCount++;
                }
                zoneLoadOnVanJars += (order.getJarRefillCount() + order.getNewJarWithDepositCount());
                zoneEmptyToCollectJars += order.getEmptyJarsToReturn();
            }
        }

        final List<WaterOrder> filteredList = new ArrayList<>(deduplicatedFilteredMap.values());

        // Update Feature 1 Trip Load Summary TextView
        if (tvHallBatchSummary != null) {
            String summaryText = String.format(Locale.getDefault(),
                    "Pending in this zone: %d Orders | Load on Van: %d Jars | Empty to Collect: %d Jars",
                    zonePendingCount, zoneLoadOnVanJars, zoneEmptyToCollectJars);
            tvHallBatchSummary.setText(summaryText);
        }

        if (isAllHalls) {
            tvOrderQueueCount.setText(String.format(Locale.getDefault(), "%d Active Orders", filteredList.size()));
        } else {
            tvOrderQueueCount.setText(String.format(Locale.getDefault(), "%d Active in Zone", filteredList.size()));
        }

        // Urgent orders first, then serial chronological order (timestamp ascending)
        Collections.sort(filteredList, new Comparator<WaterOrder>() {
            @Override
            public int compare(WaterOrder o1, WaterOrder o2) {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return 1;
                if (o2 == null) return -1;
                if (o1.isUrgent() != o2.isUrgent()) {
                    return o1.isUrgent() ? -1 : 1;
                }
                return Long.compare(o1.getTimestamp(), o2.getTimestamp());
            }
        });

        sellerOrderAdapter.setOrders(filteredList);
        sellerOrderAdapter.notifyDataSetChanged();

        if (filteredList.isEmpty()) {
            String emptyMsg = isAllHalls
                    ? "No active orders in dispatch queue.\n(All orders delivered! Tap '📦 Delivered Orders Archive' to view completed orders)"
                    : "No active orders found for " + selectedHallFilter + ".\n(Select \"All KUET Locations\" or check Delivered Archive)";
            tvNoSellerOrders.setText(emptyMsg);
            tvNoSellerOrders.setVisibility(View.VISIBLE);
            rvSellerOrders.setVisibility(View.GONE);
        } else {
            tvNoSellerOrders.setVisibility(View.GONE);
            rvSellerOrders.setVisibility(View.VISIBLE);
        }
    }

    private void notifyEmergencyOrderArrived(final WaterOrder order) {
        if (order == null) return;

        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), soundUri);
            if (r != null) r.play();
        } catch (Exception ignored) {}

        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                long[] pattern = {0, 450, 200, 450, 200, 450};
                v.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, "🚨🚨 EMERGENCY ORDER ALERT! Order #" + order.getOrderId() + " requires EXPRESS delivery!", Toast.LENGTH_LONG).show();

        try {
            if (!isFinishing() && !isDestroyed()) {
                StringBuilder msg = new StringBuilder();
                msg.append("Order #").append(order.getOrderId()).append(" has been marked as EMERGENCY EXPRESS DELIVERY!\n\n");
                msg.append("📍 Location: ").append(order.getLocationName()).append(" • Room ").append(order.getRoomOrFlatNo())
                        .append(" (Floor ").append(order.getFloorNumber()).append(")\n");
                if (!TextUtils.isEmpty(order.getLocationNotes())) {
                    msg.append("📝 Exact Directions: ").append(order.getLocationNotes()).append("\n");
                }
                msg.append("👤 Buyer: ").append(order.getBuyerName()).append(" (").append(order.getBuyerPhone()).append(")\n");
                msg.append("💧 Bottles: ").append(order.getJarRefillCount()).append(" Refill Jar(s)\n");
                msg.append("💵 Total: ").append(String.format(Locale.getDefault(), "%.0f BDT", order.getTotalPrice()));

                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("🚨 EMERGENCY ORDER ALERT!")
                        .setMessage(msg.toString())
                        .setPositiveButton("View Order Now", (dialog, which) -> {
                            if (rvSellerOrders != null) {
                                rvSellerOrders.smoothScrollToPosition(0);
                            }
                        })
                        .setNegativeButton("Acknowledge", null)
                        .show();
            }
        } catch (Exception ignored) {}
    }

    private void notifyNewOrderArrived() {
        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), soundUri);
            if (r != null) r.play();
        } catch (Exception ignored) {}

        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(350);
        } catch (Exception ignored) {}

        Toast.makeText(this, "🔔 New Campus Order Received Online!", Toast.LENGTH_LONG).show();
    }

    // --- Order Action Callbacks ---

    @Override
    public void onCallBuyer(WaterOrder order) {
        if (!TextUtils.isEmpty(order.getBuyerPhone())) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + order.getBuyerPhone()));
            startActivity(callIntent);
        } else {
            Toast.makeText(this, "Buyer phone number not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAcceptOrder(final WaterOrder order) {
        if (order == null || TextUtils.isEmpty(order.getOrderId())) {
            Toast.makeText(this, "Order information missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (WaterOrder.STATUS_CANCELLED.equalsIgnoreCase(order.getStatus())) {
            Toast.makeText(this, "Cannot accept: This order was cancelled by the buyer.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String currentSellerUid = FirebaseManager.getInstance().getCurrentUserId();
        if (TextUtils.isEmpty(currentSellerUid)) {
            Toast.makeText(this, "Authentication error. Please re-login.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String depotTitle = !TextUtils.isEmpty(currentSellerDepotTitle) ? currentSellerDepotTitle : "KUET Depot";
        final String assignedName = currentSellerName + " (" + depotTitle + ")";

        // 1. Immediately update local state so UI transitions without lag
        order.setStatus(WaterOrder.STATUS_ACCEPTED);
        order.setAssignedSellerId(currentSellerUid);
        order.setSellerId(currentSellerUid);
        order.setAssignedSellerName(assignedName);
        liveOrdersMap.put(order.getOrderId(), order);
        recalculateAndDisplayOrders();

        // 2. Update Firebase Realtime Database FIRST (Primary authoritative real-time store)
        Map<String, Object> rtdbUpdates = new HashMap<>();
        rtdbUpdates.put("status", WaterOrder.STATUS_ACCEPTED);
        rtdbUpdates.put("assignedSellerId", currentSellerUid);
        rtdbUpdates.put("sellerId", currentSellerUid);
        rtdbUpdates.put("assignedSellerName", assignedName);

        FirebaseManager.getInstance().getRtdbOrdersRef().child(order.getOrderId())
                .updateChildren(rtdbUpdates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d("SellerDashboard", "Order #" + order.getOrderId() + " locked to seller in RTDB");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("SellerDashboard", "RTDB acceptOrder notice: " + e.getMessage());
                    }
                });

        // 3. Mirror to Cloud Firestore silently in background (log only, never block seller)
        try {
            FirebaseManager.getInstance().getOrdersRef().document(order.getOrderId())
                    .set(order, com.google.firebase.firestore.SetOptions.merge())
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w("SellerDashboard", "Firestore mirror notice (propagating): " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.w("SellerDashboard", "Firestore mirror exception: " + e.getMessage());
        }

        Toast.makeText(SellerDashboardActivity.this,
                "✓ Order #" + order.getOrderId() + " Accepted! Assigned to you.",
                Toast.LENGTH_SHORT).show();

        // 4. Prompt seller to launch delivery tracking controls immediately if ready
        new AlertDialog.Builder(this)
                .setTitle("Order #" + order.getOrderId() + " Accepted")
                .setMessage("Delivery is now assigned to you.\n\nWould you like to start van delivery and GPS tracking right now?")
                .setPositiveButton("Start Delivery & Track", (dialog, which) -> {
                    onStartDelivery(order);
                })
                .setNegativeButton("Keep in Queue", null)
                .show();
    }

    @Override
    public void onStartDelivery(final WaterOrder order) {
        if (order == null) return;
        final String currentSellerUid = FirebaseManager.getInstance().getCurrentUserId();
        final Map<String, Object> updates = new HashMap<>();
        updates.put("status", WaterOrder.STATUS_OUT_FOR_DELIVERY);
        if (!TextUtils.isEmpty(currentSellerUid)) {
            updates.put("assignedSellerId", currentSellerUid);
            updates.put("sellerId", currentSellerUid);
        }

        order.setStatus(WaterOrder.STATUS_OUT_FOR_DELIVERY);
        if (!TextUtils.isEmpty(currentSellerUid)) {
            order.setAssignedSellerId(currentSellerUid);
            order.setSellerId(currentSellerUid);
        }
        liveOrdersMap.put(order.getOrderId(), order);
        recalculateAndDisplayOrders();

        // 1. Update Realtime Database FIRST
        FirebaseManager.getInstance().getRtdbOrdersRef().child(order.getOrderId()).updateChildren(updates)
                .addOnFailureListener(e -> Log.w("SellerDashboard", "RTDB onStartDelivery error: " + e.getMessage()));

        // 2. Mirror to Firestore silently in background
        try {
            FirebaseManager.getInstance().getOrdersRef().document(order.getOrderId())
                    .set(order, com.google.firebase.firestore.SetOptions.merge())
                    .addOnFailureListener(e -> Log.w("SellerDashboard", "Firestore onStartDelivery mirror notice: " + e.getMessage()));
        } catch (Exception e) {
            Log.w("SellerDashboard", "Firestore mirror exception: " + e.getMessage());
        }

        // 3. Start Background Location Broadcast Service
        try {
            Intent serviceIntent = new Intent(SellerDashboardActivity.this, LocationBroadcastService.class);
            serviceIntent.putExtra("orderId", order.getOrderId());
            serviceIntent.putExtra("buyerLat", order.getBuyerLat());
            serviceIntent.putExtra("buyerLng", order.getBuyerLng());
            serviceIntent.putExtra("floorNumber", order.getFloorNumber());
            serviceIntent.putExtra("locationName", order.getLocationName());
            ContextCompat.startForegroundService(SellerDashboardActivity.this, serviceIntent);
        } catch (Exception e) {
            Log.w("SellerDashboard", "Foreground service start: " + e.getMessage());
        }

        // 4. Broadcast delivery status update
        Intent dispatchIntent = new Intent(DeliveryStatusReceiver.ACTION_DELIVERY_UPDATE);
        dispatchIntent.putExtra(DeliveryStatusReceiver.EXTRA_ORDER_ID, order.getOrderId());
        dispatchIntent.putExtra(DeliveryStatusReceiver.EXTRA_STATUS, WaterOrder.STATUS_OUT_FOR_DELIVERY);
        dispatchIntent.putExtra(DeliveryStatusReceiver.EXTRA_MESSAGE, "Order #" + order.getOrderId() + " is now OUT FOR DELIVERY! Van en route to " + order.getLocationName());
        dispatchIntent.putExtra(DeliveryStatusReceiver.EXTRA_BUYER_LAT, order.getBuyerLat());
        dispatchIntent.putExtra(DeliveryStatusReceiver.EXTRA_BUYER_LNG, order.getBuyerLng());
        dispatchIntent.putExtra(DeliveryStatusReceiver.EXTRA_LOCATION_NAME, order.getLocationName());
        sendBroadcast(dispatchIntent);

        Toast.makeText(SellerDashboardActivity.this,
                "Order #" + order.getOrderId() + " is out for delivery! Driver GPS tracking broadcast active.",
                Toast.LENGTH_SHORT).show();

        launchLiveMap(order);
    }

    @Override
    public void onOpenLiveMap(WaterOrder order) {
        launchLiveMap(order);
    }

    private void launchLiveMap(WaterOrder order) {
        Intent intent = new Intent(SellerDashboardActivity.this, LiveTrackingActivity.class);
        intent.putExtra("orderId", order.getOrderId());
        intent.putExtra("buyerLat", order.getBuyerLat());
        intent.putExtra("buyerLng", order.getBuyerLng());
        intent.putExtra("floorNumber", order.getFloorNumber());
        intent.putExtra("locationName", order.getLocationName());
        intent.putExtra("buyerName", order.getBuyerName());
        intent.putExtra("isSeller", true);
        startActivity(intent);
    }

    @Override
    public void onScanQr(final WaterOrder order) {
        if (order == null) return;
        this.pendingScanOrder = order;

        new AlertDialog.Builder(this)
                .setTitle("Complete Delivery: Order #" + order.getOrderId())
                .setMessage("Choose delivery confirmation method:\n\n• Scan QR with Camera: Scan physical QR code from buyer's phone.\n• Enter/Confirm Token (Demo Mode): Complete delivery on this device without needing a second phone camera.")
                .setPositiveButton("Scan QR with Camera", (dialog, which) -> {
                    ScanOptions options = new ScanOptions();
                    options.setPrompt("Scan Handover QR for Order #" + order.getOrderId());
                    options.setBeepEnabled(true);
                    options.setOrientationLocked(true);
                    options.setBarcodeImageEnabled(false);
                    qrScanLauncher.launch(options);
                })
                .setNeutralButton("Enter/Confirm Token (Demo)", (dialog, which) -> {
                    showDemoTokenConfirmationDialog(order);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startScanAnyOrderQr() {
        new AlertDialog.Builder(this)
                .setTitle("Scan Handover QR to Confirm Delivery")
                .setMessage("Choose delivery confirmation method:\n\n• Scan QR with Camera: Scan physical QR code from buyer's phone.\n• Enter Order ID / Token (Demo Mode): Complete delivery on this device.")
                .setPositiveButton("Scan with Camera", (dialog, which) -> {
                    this.pendingScanOrder = null;
                    ScanOptions options = new ScanOptions();
                    options.setPrompt("Scan Buyer Handover QR to Confirm Delivery");
                    options.setBeepEnabled(true);
                    options.setOrientationLocked(true);
                    options.setBarcodeImageEnabled(false);
                    qrScanLauncher.launch(options);
                })
                .setNeutralButton("Enter Order ID / Token", (dialog, which) -> {
                    showDemoTokenConfirmationDialog(null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDemoTokenConfirmationDialog(final WaterOrder order) {
        final android.widget.EditText inputToken = new android.widget.EditText(this);
        inputToken.setSingleLine(true);
        String prefill = (order != null)
                ? (!TextUtils.isEmpty(order.getQrToken()) ? order.getQrToken() : order.getOrderId())
                : "";
        inputToken.setText(prefill);
        inputToken.setHint("e.g. AQ-VERIFY-... or AQ-8912");
        if (!prefill.isEmpty()) {
            inputToken.setSelection(prefill.length());
        }

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = (int) (24 * getResources().getDisplayMetrics().density);
        params.rightMargin = (int) (24 * getResources().getDisplayMetrics().density);
        params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        inputToken.setLayoutParams(params);
        container.addView(inputToken);

        String title = (order != null)
                ? ("Confirm Delivery (Demo Mode) • " + order.getOrderId())
                : "Enter Order Token (Demo Mode)";

        String message = (order != null)
                ? ("Buyer: " + order.getBuyerName() + " (" + order.getLocationName() + ")\n"
                + "Bottles: " + order.getJarRefillCount() + " jar(s) • Cash to collect: "
                + String.format(Locale.getDefault(), "%.0f BDT", order.getTotalPrice())
                + "\n\nToken prefilled below for single-device testing:")
                : "Enter Order ID or Buyer Handover Token to confirm delivery & cash collection:";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(container)
                .setPositiveButton("Confirm Delivery & Cash", (dialog, which) -> {
                    String entered = inputToken.getText() != null ? inputToken.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(entered)) {
                        entered = prefill;
                    }
                    if (TextUtils.isEmpty(entered)) {
                        Toast.makeText(SellerDashboardActivity.this, "Please enter an order ID or token", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    this.pendingScanOrder = order;
                    handleQrScanVerification(entered);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onEditOrder(final WaterOrder order) {
        showEditOrderDialog(order);
    }

    @Override
    public void onViewGpsOnMap(final WaterOrder order) {
        if (order == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delivery Navigation: " + order.getLocationName())
                .setMessage("Choose your navigation view:\n• In-App Live Map (Real-time tracking)\n• Google Maps App (Turn-by-turn routing via Implicit Intent)")
                .setPositiveButton("In-App Live Map", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        launchLiveMap(order);
                    }
                })
                .setNeutralButton("Google Maps App", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        // Week 8 Syllabus: Implicit Intent (ACTION_VIEW with geo scheme)
                        double lat = order.getBuyerLat() != 0.0 ? order.getBuyerLat() : 22.8998;
                        double lng = order.getBuyerLng() != 0.0 ? order.getBuyerLng() : 89.5015;
                        Uri gmmIntentUri = Uri.parse("geo:" + lat + "," + lng + "?q=" + Uri.encode(order.getLocationName()));
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                        if (mapIntent.resolveActivity(getPackageManager()) != null) {
                            startActivity(mapIntent);
                        } else {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + lat + "," + lng)));
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditOrderDialog(final WaterOrder order) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_order);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialog.findViewById(R.id.tvEditOrderDialogTitle);
        final TextInputEditText etRefillJars = dialog.findViewById(R.id.etEditJarCount);
        final TextInputEditText etEmptyJars = dialog.findViewById(R.id.etEditEmptyJars);
        final TextInputEditText etTotalPrice = dialog.findViewById(R.id.etEditTotalPrice);
        MaterialButton btnAutoCalc = dialog.findViewById(R.id.btnAutoCalculatePrice);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancelEditOrder);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSaveEditOrder);

        tvTitle.setText(String.format("Adjust Order #%s", order.getOrderId()));
        etRefillJars.setText(String.valueOf(order.getJarRefillCount()));
        etEmptyJars.setText(String.valueOf(order.getEmptyJarsToReturn()));
        etTotalPrice.setText(String.format(Locale.getDefault(), "%.0f", order.getTotalPrice()));

        btnAutoCalc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String refillStr = etRefillJars.getText() != null ? etRefillJars.getText().toString().trim() : "";
                try {
                    int jars = Integer.parseInt(refillStr);
                    if (jars < 1) jars = 1;
                    int expressSurcharge = order.isUrgent() ? 15 : 0;
                    double autoPrice = (jars * 45) + expressSurcharge;
                    etTotalPrice.setText(String.format(Locale.getDefault(), "%.0f", autoPrice));
                    Toast.makeText(SellerDashboardActivity.this,
                            "Calculated: " + jars + " jars × 45 BDT" + (expressSurcharge > 0 ? " + 15 express" : ""),
                            Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(SellerDashboardActivity.this, "Enter valid jar count first", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String refillStr = etRefillJars.getText() != null ? etRefillJars.getText().toString().trim() : "";
                String emptyStr = etEmptyJars.getText() != null ? etEmptyJars.getText().toString().trim() : "";
                String priceStr = etTotalPrice.getText() != null ? etTotalPrice.getText().toString().trim() : "";

                try {
                    int newRefill = Integer.parseInt(refillStr);
                    int newEmpty = Integer.parseInt(emptyStr);
                    double newPrice = Double.parseDouble(priceStr);

                    if (newRefill < 1) {
                        etRefillJars.setError("At least 1 jar is required");
                        return;
                    }
                    if (newEmpty < 0) {
                        etEmptyJars.setError("Empty jars cannot be negative");
                        return;
                    }
                    if (newPrice < 0) {
                        etTotalPrice.setError("Price cannot be negative");
                        return;
                    }

                    final Map<String, Object> updates = new HashMap<>();
                    updates.put("jarRefillCount", newRefill);
                    updates.put("emptyJarsToReturn", newEmpty);
                    updates.put("totalPrice", newPrice);

                    order.setJarRefillCount(newRefill);
                    order.setEmptyJarsToReturn(newEmpty);
                    order.setTotalPrice(newPrice);
                    liveOrdersMap.put(order.getOrderId(), order);
                    recalculateAndDisplayOrders();

                    FirebaseManager.getInstance().updateOrderFields(order.getOrderId(), updates,
                            new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    Toast.makeText(SellerDashboardActivity.this,
                                            "Order #" + order.getOrderId() + " updated! Synced to buyer live.",
                                            Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                }
                            },
                            new OnFailureListener() {
                                @Override
                                public void onFailure(Exception e) {
                                    Toast.makeText(SellerDashboardActivity.this,
                                            "Order updated locally. Cloud notice: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                }
                            });

                } catch (NumberFormatException e) {
                    Toast.makeText(SellerDashboardActivity.this, "Please enter valid numeric values", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * Verifies scanned token and executes atomic transaction on Firestore and Realtime Database.
     */
    private void handleQrScanVerification(String scannedToken) {
        WaterOrder targetOrder = pendingScanOrder;

        if (targetOrder == null) {
            // Find any active order matching the scanned token or order ID
            for (WaterOrder o : cachedAllOrders) {
                if (!WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(o.getStatus())) {
                    if ((o.getQrToken() != null && o.getQrToken().trim().equalsIgnoreCase(scannedToken.trim()))
                            || o.getOrderId().equalsIgnoreCase(scannedToken.trim())) {
                        targetOrder = o;
                        break;
                    }
                }
            }
        }

        if (targetOrder == null) {
            Toast.makeText(this, "QR Verification Failed! No active order matches: " + scannedToken, Toast.LENGTH_LONG).show();
            return;
        }

        final WaterOrder order = targetOrder;
        final String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        final String sellerUid = FirebaseManager.getInstance().getCurrentUserId();

        // 1. Update local state immediately for instant responsive UX
        order.setStatus(WaterOrder.STATUS_DELIVERED);
        order.setDeliveredTimestamp(System.currentTimeMillis());
        order.setDeliveredDate(todayDate);
        if (!TextUtils.isEmpty(sellerUid)) {
            order.setSellerId(sellerUid);
            order.setAssignedSellerId(sellerUid);
        }
        liveOrdersMap.put(order.getOrderId(), order);
        recalculateAndDisplayOrders();

        // 2. Dual-database order status update (Realtime Database + Firestore)
        final Map<String, Object> orderUpdates = new HashMap<>();
        orderUpdates.put("status", WaterOrder.STATUS_DELIVERED);
        orderUpdates.put("deliveredTimestamp", System.currentTimeMillis());
        orderUpdates.put("deliveredDate", todayDate);
        if (!TextUtils.isEmpty(sellerUid)) {
            orderUpdates.put("sellerId", sellerUid);
            orderUpdates.put("assignedSellerId", sellerUid);
        }

        // 2A. Update Realtime Database FIRST
        FirebaseManager.getInstance().getRtdbOrdersRef().child(order.getOrderId()).updateChildren(orderUpdates)
                .addOnFailureListener(e -> Log.w("SellerDashboard", "RTDB order completion notice: " + e.getMessage()));

        // 2B. Store permanent copy in seller's dedicated delivery storage in Realtime Database FIRST
        if (!TextUtils.isEmpty(sellerUid)) {
            FirebaseManager.getInstance().getDatabase().getReference("seller_storage")
                    .child(sellerUid)
                    .child("delivered_orders")
                    .child(order.getOrderId())
                    .setValue(order)
                    .addOnFailureListener(e -> Log.w("SellerDashboard", "RTDB seller_storage notice: " + e.getMessage()));
        }

        // 2C. Deduct delivered jars from seller storage & increment totalDeliveredCount directly in Realtime Database FIRST
        final int totalJarsDelivered = order.getJarRefillCount() + order.getNewJarWithDepositCount();
        if (!TextUtils.isEmpty(sellerUid)) {
            int currentStock = (currentSellerUser != null) ? currentSellerUser.getAvailableStockJars() : 50;
            int currentDelivered = (currentSellerUser != null) ? currentSellerUser.getTotalDeliveredCount() : 0;
            int newStock = Math.max(0, currentStock - totalJarsDelivered);
            int newDelivered = currentDelivered + 1;
            boolean ready = (newStock > 0);

            if (currentSellerUser != null) {
                currentSellerUser.setAvailableStockJars(newStock);
                currentSellerUser.setTotalDeliveredCount(newDelivered);
                currentSellerUser.setReadyForSupply(ready);
                updateSupplyUiState();
            }

            Map<String, Object> sUpdates = new HashMap<>();
            sUpdates.put("availableStockJars", newStock);
            sUpdates.put("totalDeliveredCount", newDelivered);
            sUpdates.put("readyForSupply", ready);

            FirebaseManager.getInstance().getDatabase().getReference("online_sellers")
                    .child(sellerUid)
                    .updateChildren(sUpdates);
            FirebaseManager.getInstance().getRtdbUsersRef()
                    .child(sellerUid)
                    .updateChildren(sUpdates);

            // Mirror seller storage updates to Firestore silently in background
            try {
                FirebaseManager.getInstance().getUsersRef().document(sellerUid)
                        .update(sUpdates)
                        .addOnFailureListener(e -> Log.w("SellerDashboard", "Firestore seller stock mirror warning: " + e.getMessage()));
            } catch (Exception e) {
                Log.w("SellerDashboard", "Firestore seller stock update exception: " + e.getMessage());
            }

            // Mirror delivered order to Firestore seller_storage silently
            try {
                FirebaseFirestore.getInstance().collection("seller_storage")
                        .document(sellerUid)
                        .collection("delivered_orders")
                        .document(order.getOrderId())
                        .set(order, com.google.firebase.firestore.SetOptions.merge())
                        .addOnFailureListener(e -> Log.w("SellerDashboard", "Firestore seller_storage mirror warning: " + e.getMessage()));
            } catch (Exception e) {
                Log.w("SellerDashboard", "Firestore seller_storage exception: " + e.getMessage());
            }
        }

        // Mirror main order status to Firestore silently
        try {
            FirebaseManager.getInstance().getOrdersRef().document(order.getOrderId())
                    .set(order, com.google.firebase.firestore.SetOptions.merge())
                    .addOnFailureListener(e -> Log.w("SellerDashboard", "Firestore main order status mirror warning: " + e.getMessage()));
        } catch (Exception e) {
            Log.w("SellerDashboard", "Firestore main order exception: " + e.getMessage());
        }

        // 3. Update buyer bottle ledger in Realtime Database FIRST
        final int jarsDelivered = order.getJarRefillCount();
        final int emptyCollected = order.getEmptyJarsToReturn();
        if (!TextUtils.isEmpty(order.getBuyerId())) {
            FirebaseManager.getInstance().getRtdbUsersRef().child(order.getBuyerId()).child("emptyJarsHeld")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            long currentHeld = 0;
                            if (snapshot.exists() && snapshot.getValue() != null) {
                                try {
                                    currentHeld = Long.parseLong(String.valueOf(snapshot.getValue()));
                                } catch (Exception ignored) {}
                            }
                            long newHeld = Math.max(0, currentHeld + jarsDelivered - emptyCollected);
                            FirebaseManager.getInstance().updateBuyerEmptyJars(order.getBuyerId(), newHeld, null, null);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }

        // Stop location broadcast service and remove RTDB telemetry
        try {
            Intent stopIntent = new Intent(SellerDashboardActivity.this, LocationBroadcastService.class);
            stopService(stopIntent);
        } catch (Exception ignored) {}

        try {
            FirebaseManager.getInstance().getTelemetryRef(order.getOrderId()).removeValue();
        } catch (Exception ignored) {}

        // Auto-cleanup gate alert node upon delivery completion
        if (!TextUtils.isEmpty(order.getBuyerId()) && !TextUtils.isEmpty(order.getOrderId())) {
            try {
                FirebaseManager.getInstance().getDatabase()
                        .getReference("buyer_alerts")
                        .child(order.getBuyerId())
                        .child(order.getOrderId())
                        .removeValue();
            } catch (Exception ignored) {}
        }

        // Week 10: Broadcast delivery completion alert
        Intent deliveredBroadcast = new Intent(DeliveryStatusReceiver.ACTION_DELIVERY_UPDATE);
        deliveredBroadcast.putExtra(DeliveryStatusReceiver.EXTRA_ORDER_ID, order.getOrderId());
        deliveredBroadcast.putExtra(DeliveryStatusReceiver.EXTRA_STATUS, WaterOrder.STATUS_DELIVERED);
        deliveredBroadcast.putExtra(DeliveryStatusReceiver.EXTRA_MESSAGE, "✓ Delivery Completed! Cash & jars collected for Order #" + order.getOrderId());
        sendBroadcast(deliveredBroadcast);

        // Sound victory chime and vibration
        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), soundUri);
            if (r != null) r.play();
        } catch (Exception ignored) {}

        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(new long[]{0, 200, 100, 300}, -1);
        } catch (Exception ignored) {}

        // Display delivery completion notification dialog
        showDeliveryCompletedNotificationDialog(order);
        pendingScanOrder = null;
    }

    private void showDeliveryCompletedNotificationDialog(final WaterOrder order) {
        new AlertDialog.Builder(this)
                .setTitle("✅ Delivery Completed & Cash Received!")
                .setMessage("Order #" + order.getOrderId() + " has been verified delivered at doorstep.\n\n"
                        + "• Buyer: " + order.getBuyerName() + " (" + order.getLocationName() + ", Room " + order.getRoomOrFlatNo() + ")\n"
                        + "• Cash Received: " + String.format(Locale.getDefault(), "%.0f BDT", order.getTotalPrice()) + "\n"
                        + "• Jars Delivered: " + order.getJarRefillCount() + " x 20L\n"
                        + "• Empty Jars Collected: " + order.getEmptyJarsToReturn() + " Jar(s)\n\n"
                        + "This delivery and earnings record has been saved to your seller account.")
                .setIcon(R.drawable.ic_delivery_van)
                .setPositiveButton("Awesome", null)
                .show();
    }

    // --- Dialog: Daily Sales & Delivery History ---

    private void showDailySalesHistoryDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_daily_sales_history);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvLifetimeSummary = dialog.findViewById(R.id.tvDailyHistoryLifetimeSummary);
        TextView tvNoHistory = dialog.findViewById(R.id.tvNoDailyHistory);
        RecyclerView rvList = dialog.findViewById(R.id.rvDailyHistoryList);
        ImageView ivClose = dialog.findViewById(R.id.ivCloseDailyHistory);
        MaterialButton btnClose = dialog.findViewById(R.id.btnCloseDailyHistory);

        // Group delivered orders by date (newest date first)
        Map<String, List<WaterOrder>> dailyMap = new TreeMap<>(Collections.reverseOrder());
        double totalRevenue = 0.0;
        int totalDelivered = 0;
        final String currentSellerUid = FirebaseManager.getInstance().getCurrentUserId();

        for (WaterOrder order : cachedAllOrders) {
            String assigned = order.getAssignedSellerId();
            if (TextUtils.isEmpty(assigned)) assigned = order.getSellerId();
            boolean isMine = currentSellerUid != null && currentSellerUid.equals(assigned);

            if (WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(order.getStatus()) && isMine) {
                totalDelivered++;
                totalRevenue += order.getTotalPrice();

                String dateKey = order.getDeliveredDate();
                if (TextUtils.isEmpty(dateKey)) {
                    dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(order.getTimestamp()));
                }
                if (!dailyMap.containsKey(dateKey)) {
                    dailyMap.put(dateKey, new ArrayList<WaterOrder>());
                }
                dailyMap.get(dateKey).add(order);
            }
        }

        tvLifetimeSummary.setText(String.format(Locale.getDefault(),
                "Lifetime Total: %d Orders Delivered • %.0f BDT Earned", totalDelivered, totalRevenue));

        if (dailyMap.isEmpty()) {
            tvNoHistory.setVisibility(View.VISIBLE);
            rvList.setVisibility(View.GONE);
        } else {
            tvNoHistory.setVisibility(View.GONE);
            rvList.setVisibility(View.VISIBLE);

            rvList.setLayoutManager(new LinearLayoutManager(this));
            rvList.setAdapter(new DailyHistoryAdapter(new ArrayList<>(dailyMap.entrySet())));
        }

        View.OnClickListener dismissListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        };
        ivClose.setOnClickListener(dismissListener);
        btnClose.setOnClickListener(dismissListener);

        dialog.show();
    }

    private class DailyHistoryAdapter extends RecyclerView.Adapter<DailyHistoryAdapter.HistoryGroupViewHolder> {

        private final List<Map.Entry<String, List<WaterOrder>>> dailyGroups;
        private final String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        public DailyHistoryAdapter(List<Map.Entry<String, List<WaterOrder>>> dailyGroups) {
            this.dailyGroups = dailyGroups;
        }

        @NonNull
        @Override
        public HistoryGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_daily_history_group, parent, false);
            return new HistoryGroupViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryGroupViewHolder holder, int position) {
            Map.Entry<String, List<WaterOrder>> entry = dailyGroups.get(position);
            String dateKey = entry.getKey();
            List<WaterOrder> orders = entry.getValue();

            boolean isToday = todayStr.equals(dateKey);
            holder.tvDate.setText(isToday ? "📅 Today (" + dateKey + ")" : "📅 Date: " + dateKey);

            double dayRevenue = 0.0;
            int totalJars = 0;
            for (WaterOrder o : orders) {
                dayRevenue += o.getTotalPrice();
                totalJars += o.getJarRefillCount();
            }

            holder.tvRevenue.setText(String.format(Locale.getDefault(), "%.0f BDT", dayRevenue));
            holder.tvOrderCount.setText(String.format(Locale.getDefault(),
                    "%d orders delivered • %d jars supplied", orders.size(), totalJars));

            holder.container.removeAllViews();
            for (WaterOrder o : orders) {
                TextView itemTv = new TextView(SellerDashboardActivity.this);
                itemTv.setText(String.format(Locale.getDefault(),
                        "• Order #%s: %s (%s, Rm %s) — %d jars (%.0f BDT)",
                        o.getOrderId(), o.getBuyerName(), o.getLocationName(), o.getRoomOrFlatNo(),
                        o.getJarRefillCount(), o.getTotalPrice()));
                itemTv.setTextSize(11f);
                itemTv.setTextColor(ContextCompat.getColor(SellerDashboardActivity.this, R.color.text_secondary));
                itemTv.setPadding(0, 4, 0, 4);
                holder.container.addView(itemTv);
            }
        }

        @Override
        public int getItemCount() {
            return dailyGroups.size();
        }

        class HistoryGroupViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate;
            TextView tvRevenue;
            TextView tvOrderCount;
            LinearLayout container;

            public HistoryGroupViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tvDailyGroupDate);
                tvRevenue = itemView.findViewById(R.id.tvDailyGroupTotalRevenue);
                tvOrderCount = itemView.findViewById(R.id.tvDailyGroupOrderCount);
                container = itemView.findViewById(R.id.llDailyDeliveriesContainer);
            }
        }
    }

    // --- Dialog: Update Daily Water Quality ---

    private void showUpdateWaterQualityDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_update_water_quality);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        final TextInputEditText etTds = dialog.findViewById(R.id.etQualityTds);
        final TextInputEditText etPh = dialog.findViewById(R.id.etQualityPh);
        final TextInputEditText etBatch = dialog.findViewById(R.id.etQualityBatch);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancelQuality);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSaveQuality);

        // Pre-fill with current values if available
        FirebaseManager.getInstance().getWaterQualityRef().document("latest").get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot doc) {
                        if (doc != null && doc.exists()) {
                            WaterQuality wq = doc.toObject(WaterQuality.class);
                            if (wq != null) {
                                etTds.setText(String.format(Locale.getDefault(), "%.0f", wq.getTds()));
                                etPh.setText(String.format(Locale.getDefault(), "%.1f", wq.getPh()));
                                if (!TextUtils.isEmpty(wq.getBatchStatus())) {
                                    etBatch.setText(wq.getBatchStatus());
                                }
                            }
                        }
                    }
                });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tdsStr = etTds.getText() != null ? etTds.getText().toString().trim() : "";
                String phStr = etPh.getText() != null ? etPh.getText().toString().trim() : "";
                String batchStr = etBatch.getText() != null ? etBatch.getText().toString().trim() : "";

                if (TextUtils.isEmpty(tdsStr) || TextUtils.isEmpty(phStr)) {
                    Toast.makeText(SellerDashboardActivity.this, "TDS and pH are required", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    double tds = Double.parseDouble(tdsStr);
                    double ph = Double.parseDouble(phStr);
                    if (TextUtils.isEmpty(batchStr)) {
                        batchStr = "Certified Pure (Reverse Osmosis + UV)";
                    }

                    WaterQuality quality = new WaterQuality(tds, ph, batchStr, System.currentTimeMillis());
                    FirebaseManager.getInstance().getWaterQualityRef().document("latest").set(quality)
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    Toast.makeText(SellerDashboardActivity.this,
                                            "Daily Water Quality published to all KUET consumers!", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(Exception e) {
                                    Toast.makeText(SellerDashboardActivity.this,
                                            "Failed to publish: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                } catch (NumberFormatException e) {
                    Toast.makeText(SellerDashboardActivity.this, "Invalid TDS or pH numeric value", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.show();
    }

    // --- Week 8 Syllabus: Activity Lifecycle Callbacks & Observation ---

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("AquaDropLifecycle", "SellerDashboardActivity: onStart() called");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("AquaDropLifecycle", "SellerDashboardActivity: onPause() called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("AquaDropLifecycle", "SellerDashboardActivity: onStop() called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("AquaDropLifecycle", "SellerDashboardActivity: onDestroy() called");
        final String sellerUid = FirebaseManager.getInstance().getCurrentUserId();
        if (!TextUtils.isEmpty(sellerUid)) {
            try {
                FirebaseManager.getInstance().getDatabase().getReference("online_sellers").child(sellerUid).child("readyForSupply").setValue(false);
                FirebaseManager.getInstance().getRtdbUsersRef().child(sellerUid).child("readyForSupply").setValue(false);
                FirebaseManager.getInstance().getUsersRef().document(sellerUid).update("readyForSupply", false);
                stopService(new Intent(this, com.kuet.aquadrop.services.SellerPresenceService.class));
            } catch (Exception ignored) {}
        }
        if (ordersListenerRegistration != null) {
            ordersListenerRegistration.remove();
        }
        if (rtdbOrdersListener != null) {
            FirebaseManager.getInstance().getRtdbOrdersRef().removeEventListener(rtdbOrdersListener);
        }
    }
}
