package com.kuet.aquadrop.activities;

import android.Manifest;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.adapters.BuyerOrderAdapter;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.models.WaterOrder;
import com.kuet.aquadrop.models.WaterQuality;
import com.kuet.aquadrop.providers.AquaDropContentProvider;
import com.kuet.aquadrop.receivers.DeliveryStatusReceiver;
import com.kuet.aquadrop.utils.FirebaseManager;
import com.kuet.aquadrop.utils.KuetLocations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BuyerDashboardActivity extends AppCompatActivity implements BuyerOrderAdapter.OnOrderActionListener {

    private static final int PRICE_REFILL_JAR = 45;
    private static final int EXPRESS_DELIVERY_SURCHARGE = 15;

    // Targeted Gate Arrival Live Alert Banner Views & State
    private MaterialCardView cardGateAlertBanner;
    private TextView tvGateAlertMessage;
    private ImageView ivDismissGateAlert;
    private ValueEventListener rtdbGateAlertListener;
    private String activeGateAlertOrderId;
    private long lastNotifiedGateAlertTimestamp = 0;

    // Header Views
    private TextView tvBuyerGreeting;
    private ImageView btnBuyerHistory;
    private ImageView btnBuyerProfile;
    private ImageView ivLogout;

    // Section 1: Bottle Ledger & Water Quality Views
    private TextView tvEmptyJarsHeld;
    private TextView tvDepositBalance;
    private MaterialButton btnOpenSplitter;
    private TextView tvTdsLevel;
    private TextView tvPhLevel;
    private TextView tvBatchStatus;

    // Section 2: Order Form Views
    private Spinner spinnerOnlineSellers;
    private TextView tvOnlineSellersBadge;
    private TextView tvNoOnlineSellersWarning;
    private final List<User> onlineSellersList = new ArrayList<>();
    private final Map<String, User> liveOnlineSellersMap = new HashMap<>();
    private User selectedOnlineSeller = null;
    private ValueEventListener rtdbOnlineSellersListener;
    private ListenerRegistration firestoreOnlineSellersListener;

    private MaterialButton btnRefillMinus;
    private TextView tvRefillCount;
    private MaterialButton btnRefillPlus;

    private MaterialButton btnReturnMinus;
    private TextView tvReturnCount;
    private MaterialButton btnReturnPlus;

    private TextView tvDepositNotice;
    private CheckBox cbExpressDelivery;
    private TextView tvExpressAlertNotice;

    private RadioGroup rgPaymentMethod;
    private RadioButton rbCashOnDelivery;
    private RadioButton rbBkash;
    private TextInputLayout tilBkashTrxId;
    private TextInputEditText etBkashTrxId;

    private TextView tvOrderTotalPrice;
    private MaterialButton btnPlaceOrder;
    private boolean isSubmittingOrder = false;

    // Section 3: Active Orders Only Views & Adapter
    private RecyclerView rvActiveOrders;
    private TextView tvNoActiveOrdersPlaceholder;
    private TextView tvActiveOrdersCountBadge;
    private BuyerOrderAdapter activeOrderAdapter;

    // State Variables
    private User currentUser;
    private int refillJarCount = 1;
    private int returnJarCount = 1;
    private double calculatedTotal = PRICE_REFILL_JAR;

    // Firestore Registrations
    private ListenerRegistration userListenerRegistration;
    private ListenerRegistration ordersListenerRegistration;
    private ListenerRegistration qualityListenerRegistration;

    // Realtime Database Listeners & Map
    private ValueEventListener rtdbUserListener;
    private ValueEventListener rtdbBuyerOrdersListener;
    private ValueEventListener rtdbQualityListener;
    private final Map<String, WaterOrder> buyerOrdersMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_dashboard);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        initViews();
        setupOrderCounters();
        setupPaymentOptions();
        setupRecyclerView();
        setupListeners();
        subscribeToFirestoreData();
    }

    private void initViews() {
        cardGateAlertBanner = findViewById(R.id.cardGateAlertBanner);
        tvGateAlertMessage = findViewById(R.id.tvGateAlertMessage);
        ivDismissGateAlert = findViewById(R.id.ivDismissGateAlert);

        tvBuyerGreeting = findViewById(R.id.tvBuyerGreeting);
        btnBuyerHistory = findViewById(R.id.btnBuyerHistory);
        btnBuyerProfile = findViewById(R.id.btnBuyerProfile);
        ivLogout = findViewById(R.id.ivLogout);

        tvTdsLevel = findViewById(R.id.tvTdsLevel);
        tvPhLevel = findViewById(R.id.tvPhLevel);
        tvBatchStatus = findViewById(R.id.tvBatchStatus);

        tvEmptyJarsHeld = findViewById(R.id.tvEmptyJarsHeld);
        tvDepositBalance = findViewById(R.id.tvDepositBalance);
        btnOpenSplitter = findViewById(R.id.btnOpenSplitter);

        spinnerOnlineSellers = findViewById(R.id.spinnerOnlineSellers);
        tvOnlineSellersBadge = findViewById(R.id.tvOnlineSellersBadge);
        tvNoOnlineSellersWarning = findViewById(R.id.tvNoOnlineSellersWarning);

        btnRefillMinus = findViewById(R.id.btnRefillMinus);
        tvRefillCount = findViewById(R.id.tvRefillCount);
        btnRefillPlus = findViewById(R.id.btnRefillPlus);

        btnReturnMinus = findViewById(R.id.btnReturnMinus);
        tvReturnCount = findViewById(R.id.tvReturnCount);
        btnReturnPlus = findViewById(R.id.btnReturnPlus);

        tvDepositNotice = findViewById(R.id.tvDepositNotice);
        cbExpressDelivery = findViewById(R.id.cbExpressDelivery);
        tvExpressAlertNotice = findViewById(R.id.tvExpressAlertNotice);

        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        rbCashOnDelivery = findViewById(R.id.rbCashOnDelivery);
        rbBkash = findViewById(R.id.rbBkash);
        tilBkashTrxId = findViewById(R.id.tilBkashTrxId);
        etBkashTrxId = findViewById(R.id.etBkashTrxId);

        tvOrderTotalPrice = findViewById(R.id.tvOrderTotalPrice);
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);

        rvActiveOrders = findViewById(R.id.rvActiveOrders);
        tvNoActiveOrdersPlaceholder = findViewById(R.id.tvNoActiveOrdersPlaceholder);
        tvActiveOrdersCountBadge = findViewById(R.id.tvActiveOrdersCountBadge);
    }

    private void setupOrderCounters() {
        btnRefillMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (refillJarCount > 1) {
                    refillJarCount--;
                    tvRefillCount.setText(String.valueOf(refillJarCount));
                    recalculateOrderTotal();
                }
            }
        });

        btnRefillPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (refillJarCount < 20) {
                    refillJarCount++;
                    tvRefillCount.setText(String.valueOf(refillJarCount));
                    recalculateOrderTotal();
                }
            }
        });

        btnReturnMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (returnJarCount > 0) {
                    returnJarCount--;
                    tvReturnCount.setText(String.valueOf(returnJarCount));
                    recalculateOrderTotal();
                }
            }
        });

        btnReturnPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (returnJarCount < 20) {
                    returnJarCount++;
                    tvReturnCount.setText(String.valueOf(returnJarCount));
                    recalculateOrderTotal();
                }
            }
        });

        cbExpressDelivery.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (tvExpressAlertNotice != null) {
                    tvExpressAlertNotice.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
                recalculateOrderTotal();
            }
        });

        recalculateOrderTotal();
    }

    private void recalculateOrderTotal() {
        int baseCost = refillJarCount * PRICE_REFILL_JAR;
        int expressSurcharge = cbExpressDelivery.isChecked() ? EXPRESS_DELIVERY_SURCHARGE : 0;
        calculatedTotal = baseCost + expressSurcharge;

        // Returning jars is 100% FREE (0 BDT) - no deposit surcharge is charged to the buyer
        if (tvDepositNotice != null) {
            tvDepositNotice.setVisibility(View.VISIBLE);
            tvDepositNotice.setBackgroundColor(Color.parseColor("#E8F5E9"));
            tvDepositNotice.setTextColor(Color.parseColor("#2E7D32"));
            tvDepositNotice.setText("✓ Returning empty jars is 100% FREE (0 BDT). Only refill water is charged.");
        }

        if (tvOrderTotalPrice != null) {
            tvOrderTotalPrice.setText(String.format(Locale.getDefault(), "%.0f BDT", calculatedTotal));
        }
    }

    private void setupPaymentOptions() {
        rgPaymentMethod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rbBkash) {
                    tilBkashTrxId.setVisibility(View.VISIBLE);
                } else {
                    tilBkashTrxId.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupRecyclerView() {
        rvActiveOrders.setLayoutManager(new LinearLayoutManager(this));
        activeOrderAdapter = new BuyerOrderAdapter(this);
        rvActiveOrders.setAdapter(activeOrderAdapter);
    }

    private void setupListeners() {
        if (ivDismissGateAlert != null) {
            ivDismissGateAlert.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (cardGateAlertBanner != null) {
                        cardGateAlertBanner.setVisibility(View.GONE);
                    }
                    FirebaseUser user = FirebaseManager.getInstance().getCurrentUser();
                    if (user != null && !TextUtils.isEmpty(activeGateAlertOrderId)) {
                        FirebaseManager.getInstance().getDatabase()
                                .getReference("buyer_alerts")
                                .child(user.getUid())
                                .child(activeGateAlertOrderId)
                                .child("active")
                                .setValue(false);
                    }
                }
            });
        }

        if (spinnerOnlineSellers != null) {
            spinnerOnlineSellers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < onlineSellersList.size()) {
                        selectedOnlineSeller = onlineSellersList.get(position);
                    } else {
                        selectedOnlineSeller = null;
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedOnlineSeller = null;
                }
            });
        }

        View.OnClickListener profileOpener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BuyerDashboardActivity.this, BuyerProfileActivity.class));
            }
        };

        if (btnBuyerProfile != null) {
            btnBuyerProfile.setOnClickListener(profileOpener);
        }
        if (tvBuyerGreeting != null) {
            tvBuyerGreeting.setOnClickListener(profileOpener);
        }

        if (btnBuyerHistory != null) {
            btnBuyerHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(BuyerDashboardActivity.this, BuyerOrderHistoryActivity.class));
                }
            });
        }

        if (ivLogout != null) {
            ivLogout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FirebaseManager.getInstance().signOut();
                    Intent intent = new Intent(BuyerDashboardActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            });
        }

        if (btnOpenSplitter != null) {
            btnOpenSplitter.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMessBillSplitterDialog();
                }
            });
        }

        if (btnPlaceOrder != null) {
            btnPlaceOrder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    placeWaterOrder();
                }
            });
        }
    }

    private void applyBuyerProfile(User user) {
        if (user == null) return;
        this.currentUser = user;
        if (tvBuyerGreeting != null) {
            tvBuyerGreeting.setText(String.format("Hello, %s", user.getName()));
        }
        if (tvEmptyJarsHeld != null) {
            tvEmptyJarsHeld.setText(String.format(Locale.getDefault(), "%d Jars", user.getEmptyJarsHeld()));
        }
        if (tvDepositBalance != null) {
            tvDepositBalance.setText(String.format(Locale.getDefault(), "%.0f BDT", user.getDepositBalance()));
        }
    }

    private void refreshBuyerOrdersUi() {
        Map<String, WaterOrder> deduplicatedActiveMap = new HashMap<>();

        for (WaterOrder order : buyerOrdersMap.values()) {
            if (order == null || TextUtils.isEmpty(order.getOrderId())) continue;
            String status = order.getStatus();
            boolean isPast = WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(status)
                    || WaterOrder.STATUS_CANCELLED.equalsIgnoreCase(status);
            if (!isPast) {
                deduplicatedActiveMap.put(order.getOrderId(), order);
            }
        }

        List<WaterOrder> activeOrders = new ArrayList<>(deduplicatedActiveMap.values());

        // Sort active orders: Urgent (isUrgent == true) first, then newest timestamp
        Collections.sort(activeOrders, new Comparator<WaterOrder>() {
            @Override
            public int compare(WaterOrder o1, WaterOrder o2) {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return 1;
                if (o2 == null) return -1;
                if (o1.isUrgent() != o2.isUrgent()) {
                    return o1.isUrgent() ? -1 : 1;
                }
                return Long.compare(o2.getTimestamp(), o1.getTimestamp());
            }
        });

        if (activeOrderAdapter != null) {
            activeOrderAdapter.setOrders(activeOrders);
            activeOrderAdapter.notifyDataSetChanged();
        }

        if (tvActiveOrdersCountBadge != null) {
            tvActiveOrdersCountBadge.setText(String.format(Locale.getDefault(), "%d Active", activeOrders.size()));
        }

        if (activeOrders.isEmpty()) {
            if (tvNoActiveOrdersPlaceholder != null) tvNoActiveOrdersPlaceholder.setVisibility(View.VISIBLE);
            if (rvActiveOrders != null) rvActiveOrders.setVisibility(View.GONE);
        } else {
            if (tvNoActiveOrdersPlaceholder != null) tvNoActiveOrdersPlaceholder.setVisibility(View.GONE);
            if (rvActiveOrders != null) rvActiveOrders.setVisibility(View.VISIBLE);
        }
    }

    private void subscribeToFirestoreData() {
        FirebaseUser user = FirebaseManager.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        final String uid = user.getUid();

        // 1A. Real-time User Profile (Realtime Database - 100% active)
        if (rtdbUserListener != null) {
            FirebaseManager.getInstance().getRtdbUsersRef().child(uid).removeEventListener(rtdbUserListener);
        }
        rtdbUserListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User u = User.fromDataSnapshot(snapshot);
                    if (u != null) {
                        applyBuyerProfile(u);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseManager.getInstance().getRtdbUsersRef().child(uid).addValueEventListener(rtdbUserListener);

        // 1B. Real-time User Profile (Cloud Firestore)
        try {
            userListenerRegistration = FirebaseManager.getInstance().getUsersRef().document(uid)
                    .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                        @Override
                        public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException error) {
                            if (error != null || snapshot == null || !snapshot.exists()) {
                                return;
                            }
                            User u = snapshot.toObject(User.class);
                            if (u != null) {
                                applyBuyerProfile(u);
                            }
                        }
                    });
        } catch (Exception ignored) {}

        // 2A. Real-time Water Quality Badge (Realtime Database)
        if (rtdbQualityListener != null) {
            FirebaseManager.getInstance().getRtdbWaterQualityRef().child("latest").removeEventListener(rtdbQualityListener);
        }
        rtdbQualityListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        WaterQuality quality = snapshot.getValue(WaterQuality.class);
                        if (quality != null) {
                            if (tvTdsLevel != null) tvTdsLevel.setText(String.format(Locale.getDefault(), "%.0f ppm", quality.getTds()));
                            if (tvPhLevel != null) tvPhLevel.setText(String.format(Locale.getDefault(), "%.1f pH", quality.getPh()));
                            if (tvBatchStatus != null) tvBatchStatus.setText(String.format("Batch: %s", quality.getBatchStatus()));
                        }
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseManager.getInstance().getRtdbWaterQualityRef().child("latest").addValueEventListener(rtdbQualityListener);

        // 2B. Real-time Water Quality Badge (Firestore)
        try {
            qualityListenerRegistration = FirebaseManager.getInstance().getWaterQualityRef().document("latest")
                    .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                        @Override
                        public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException error) {
                            if (snapshot != null && snapshot.exists()) {
                                WaterQuality quality = snapshot.toObject(WaterQuality.class);
                                if (quality != null) {
                                    if (tvTdsLevel != null) tvTdsLevel.setText(String.format(Locale.getDefault(), "%.0f ppm", quality.getTds()));
                                    if (tvPhLevel != null) tvPhLevel.setText(String.format(Locale.getDefault(), "%.1f pH", quality.getPh()));
                                    if (tvBatchStatus != null) tvBatchStatus.setText(String.format("Batch: %s", quality.getBatchStatus()));
                                }
                            }
                        }
                    });
        } catch (Exception ignored) {}

        // 3A. Real-time Orders for Current Buyer (Realtime Database)
        if (rtdbBuyerOrdersListener != null) {
            FirebaseManager.getInstance().getRtdbOrdersRef().removeEventListener(rtdbBuyerOrdersListener);
        }
        rtdbBuyerOrdersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        WaterOrder order = WaterOrder.fromDataSnapshot(child);
                        if (order != null && uid.equals(order.getBuyerId())) {
                            buyerOrdersMap.put(order.getOrderId(), order);
                        }
                    }
                    refreshBuyerOrdersUi();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseManager.getInstance().getRtdbOrdersRef().addValueEventListener(rtdbBuyerOrdersListener);

        // 3B. Real-time Orders for Current Buyer (Firestore)
        try {
            ordersListenerRegistration = FirebaseManager.getInstance().getOrdersRef()
                    .whereEqualTo("buyerId", uid)
                    .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException error) {
                            if (error != null || queryDocumentSnapshots == null) {
                                return;
                            }

                            for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                                WaterOrder order = WaterOrder.fromSnapshot(doc);
                                if (order != null) {
                                    buyerOrdersMap.put(order.getOrderId(), order);
                                }
                            }
                            refreshBuyerOrdersUi();
                        }
                    });
        } catch (Exception ignored) {}

        // 4. Targeted Buyer-Specific Gate Arrival Alert (Realtime Database: buyer_alerts/{uid})
        if (rtdbGateAlertListener != null) {
            FirebaseManager.getInstance().getDatabase().getReference("buyer_alerts")
                    .child(uid).removeEventListener(rtdbGateAlertListener);
        }
        rtdbGateAlertListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                activeGateAlertOrderId = null;
                boolean foundActive = false;
                if (snapshot.exists() && snapshot.hasChildren()) {
                    for (DataSnapshot orderAlertSnapshot : snapshot.getChildren()) {
                        Boolean active = orderAlertSnapshot.child("active").getValue(Boolean.class);
                        if (Boolean.TRUE.equals(active)) {
                            String orderId = orderAlertSnapshot.child("orderId").getValue(String.class);
                            String message = orderAlertSnapshot.child("message").getValue(String.class);
                            String locationName = orderAlertSnapshot.child("locationName").getValue(String.class);
                            Long timestamp = orderAlertSnapshot.child("timestamp").getValue(Long.class);

                            activeGateAlertOrderId = orderId != null ? orderId : orderAlertSnapshot.getKey();
                            foundActive = true;

                            if (cardGateAlertBanner != null) {
                                cardGateAlertBanner.setVisibility(View.VISIBLE);
                            }
                            if (tvGateAlertMessage != null && message != null) {
                                tvGateAlertMessage.setText(message);
                            }

                            // Trigger local notification via NotificationCompat if not already notified for this timestamp
                            if (timestamp != null && timestamp != lastNotifiedGateAlertTimestamp) {
                                lastNotifiedGateAlertTimestamp = timestamp;
                                triggerGateArrivalNotification(locationName, message);
                            }
                            break;
                        }
                    }
                }

                if (!foundActive) {
                    if (cardGateAlertBanner != null) {
                        cardGateAlertBanner.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseManager.getInstance().getDatabase().getReference("buyer_alerts")
                .child(uid).addValueEventListener(rtdbGateAlertListener);

        // 5A. Live Online Sellers (Realtime Database: online_sellers)
        if (rtdbOnlineSellersListener != null) {
            FirebaseManager.getInstance().getDatabase().getReference("online_sellers")
                    .removeEventListener(rtdbOnlineSellersListener);
        }
        rtdbOnlineSellersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, User> freshMap = new HashMap<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Boolean ready = child.child("readyForSupply").getValue(Boolean.class);
                        if (Boolean.TRUE.equals(ready)) {
                            String sellerUid = child.child("sellerUid").getValue(String.class);
                            if (TextUtils.isEmpty(sellerUid)) sellerUid = child.getKey();
                            String name = child.child("name").getValue(String.class);
                            String depot = child.child("depotOrDriverTitle").getValue(String.class);
                            Integer stock = child.child("availableStockJars").getValue(Integer.class);
                            if (stock == null) stock = 50;

                            if (stock > 0 && !TextUtils.isEmpty(sellerUid)) {
                                User seller = new User();
                                seller.setUid(sellerUid);
                                seller.setName(name != null ? name : "Campus Water Supplier");
                                seller.setDepotOrDriverTitle(depot != null ? depot : "KUET Depot");
                                seller.setRole(User.ROLE_SELLER);
                                seller.setReadyForSupply(true);
                                seller.setAvailableStockJars(stock);
                                freshMap.put(sellerUid, seller);
                            }
                        }
                    }
                }
                updateOnlineSellersFromSource(freshMap, true);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseManager.getInstance().getDatabase().getReference("online_sellers")
                .addValueEventListener(rtdbOnlineSellersListener);

        // 5B. Live Online Sellers (Firestore fallback: users where role == "SELLER")
        try {
            if (firestoreOnlineSellersListener != null) {
                firestoreOnlineSellersListener.remove();
            }
            firestoreOnlineSellersListener = FirebaseManager.getInstance().getUsersRef()
                    .whereEqualTo("role", User.ROLE_SELLER)
                    .whereEqualTo("readyForSupply", true)
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                            if (error != null || snapshots == null) return;
                            Map<String, User> fsMap = new HashMap<>();
                            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                                User u = doc.toObject(User.class);
                                if (u != null && u.isReadyForSupply() && u.getAvailableStockJars() > 0) {
                                    if (TextUtils.isEmpty(u.getUid())) u.setUid(doc.getId());
                                    fsMap.put(u.getUid(), u);
                                }
                            }
                            updateOnlineSellersFromSource(fsMap, false);
                        }
                    });
        } catch (Exception ignored) {}
    }

    private synchronized void updateOnlineSellersFromSource(Map<String, User> incomingMap, boolean isRtdb) {
        if (isRtdb) {
            liveOnlineSellersMap.clear();
            if (incomingMap != null) {
                liveOnlineSellersMap.putAll(incomingMap);
            }
        } else {
            // Firestore fallback: only populate if RTDB has not provided data yet
            if (liveOnlineSellersMap.isEmpty() && incomingMap != null) {
                liveOnlineSellersMap.putAll(incomingMap);
            }
        }

        List<User> validSellers = new ArrayList<>();
        for (User u : liveOnlineSellersMap.values()) {
            if (u != null && u.isReadyForSupply() && u.getAvailableStockJars() > 0) {
                validSellers.add(u);
            }
        }

        Collections.sort(validSellers, new Comparator<User>() {
            @Override
            public int compare(User u1, User u2) {
                int stockCmp = Integer.compare(u2.getAvailableStockJars(), u1.getAvailableStockJars());
                if (stockCmp != 0) return stockCmp;
                String n1 = u1.getName() != null ? u1.getName() : "";
                String n2 = u2.getName() != null ? u2.getName() : "";
                return n1.compareToIgnoreCase(n2);
            }
        });

        String previousSelectedId = selectedOnlineSeller != null ? selectedOnlineSeller.getUid() : null;

        onlineSellersList.clear();
        onlineSellersList.addAll(validSellers);

        List<String> displayItems = new ArrayList<>();
        int selectedIndex = 0;

        if (onlineSellersList.isEmpty()) {
            selectedOnlineSeller = null;
            displayItems.add("⚠️ No suppliers currently online");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, displayItems);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            if (spinnerOnlineSellers != null) {
                spinnerOnlineSellers.setAdapter(adapter);
                spinnerOnlineSellers.setEnabled(false);
            }

            if (tvNoOnlineSellersWarning != null) {
                tvNoOnlineSellersWarning.setVisibility(View.VISIBLE);
            }
            if (tvOnlineSellersBadge != null) {
                tvOnlineSellersBadge.setText("0 Online (Offline)");
                tvOnlineSellersBadge.setTextColor(Color.parseColor("#D32F2F"));
            }
            if (btnPlaceOrder != null && !isSubmittingOrder) {
                btnPlaceOrder.setEnabled(false);
                btnPlaceOrder.setText("No Supplier Online");
            }
        } else {
            for (int i = 0; i < onlineSellersList.size(); i++) {
                User s = onlineSellersList.get(i);
                String depot = !TextUtils.isEmpty(s.getDepotOrDriverTitle()) ? s.getDepotOrDriverTitle() : "Campus Depot";
                displayItems.add(String.format(Locale.getDefault(), "🟢 %s (%s) — %d Jars in Stock",
                        s.getName(), depot, s.getAvailableStockJars()));

                if (previousSelectedId != null && previousSelectedId.equals(s.getUid())) {
                    selectedIndex = i;
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, displayItems);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            if (spinnerOnlineSellers != null) {
                spinnerOnlineSellers.setAdapter(adapter);
                spinnerOnlineSellers.setEnabled(true);
                spinnerOnlineSellers.setSelection(selectedIndex);
            }
            selectedOnlineSeller = onlineSellersList.get(selectedIndex);

            if (tvNoOnlineSellersWarning != null) {
                tvNoOnlineSellersWarning.setVisibility(View.GONE);
            }
            if (tvOnlineSellersBadge != null) {
                tvOnlineSellersBadge.setText(String.format(Locale.getDefault(), "%d Supplier%s Online",
                        onlineSellersList.size(), onlineSellersList.size() > 1 ? "s" : ""));
                tvOnlineSellersBadge.setTextColor(Color.parseColor("#2E7D32"));
            }
            if (btnPlaceOrder != null && !isSubmittingOrder) {
                btnPlaceOrder.setEnabled(true);
                btnPlaceOrder.setText("Place Water Order");
            }
        }
    }

    private void triggerGateArrivalNotification(String locationName, String message) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        String channelId = "campus_gate_alerts";
        String channelName = "Campus Gate Arrival Alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("High-priority alerts when water delivery van reaches your hall gate");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            notificationManager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(this, BuyerDashboardActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                openIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        String title = "🚚 Water Van at " + (locationName != null ? locationName : "Gate") + "!";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_delivery_van)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        int notificationId = 1001;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            notificationManager.notify(notificationId, builder.build());
        } catch (Exception e) {
            Log.e("BuyerDashboard", "Notification error: " + e.getMessage());
        }
    }

    private void placeWaterOrder() {
        if (isSubmittingOrder) {
            return;
        }

        if (currentUser == null) {
            Toast.makeText(this, "Profile not loaded yet. Please wait…", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedOnlineSeller == null) {
            Toast.makeText(this, "No supplier is currently online. Please wait for a supplier to go online.", Toast.LENGTH_LONG).show();
            if (btnPlaceOrder != null) btnPlaceOrder.setEnabled(false);
            return;
        }

        if (refillJarCount > selectedOnlineSeller.getAvailableStockJars()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Insufficient Supplier Stock")
                    .setMessage(String.format(Locale.getDefault(),
                            "Supplier \"%s\" currently only has %d jar(s) in delivery stock.\n\nYou requested %d jar(s). Please reduce quantity or choose another online supplier.",
                            selectedOnlineSeller.getName(),
                            selectedOnlineSeller.getAvailableStockJars(),
                            refillJarCount))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        final boolean isBkash = rbBkash.isChecked();
        final String bkashTrxId = etBkashTrxId.getText() != null ? etBkashTrxId.getText().toString().trim() : "";

        if (isBkash && TextUtils.isEmpty(bkashTrxId)) {
            etBkashTrxId.setError("Enter bKash Transaction ID");
            etBkashTrxId.requestFocus();
            return;
        }

        isSubmittingOrder = true;
        if (btnPlaceOrder != null) {
            btnPlaceOrder.setEnabled(false);
            btnPlaceOrder.setText("Placing Order...");
        }

        // Generate ONE single unified orderId
        final String orderId = "AQ-" + System.currentTimeMillis();
        final String qrToken = "AQ-VERIFY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        final int extraDepositJars = 0; // Empty jars are free (0 BDT)

        final String finalLocName = !TextUtils.isEmpty(currentUser.getLocationName())
                ? currentUser.getLocationName()
                : "KUET Campus";
        final String finalRoom = !TextUtils.isEmpty(currentUser.getRoomOrFlatNo())
                ? currentUser.getRoomOrFlatNo()
                : "101";
        final int finalFloor = currentUser.getFloorNumber() > 0
                ? currentUser.getFloorNumber()
                : 1;

        double finalLat = currentUser.getLatitude();
        double finalLng = currentUser.getLongitude();
        if (finalLat == 0.0 || finalLng == 0.0) {
            LatLng p = KuetLocations.getLatLngByName(finalLocName);
            if (p != null) {
                finalLat = p.latitude;
                finalLng = p.longitude;
            } else {
                finalLat = 22.8998;
                finalLng = 89.5015;
            }
        }

        String exactLocationNotes = !TextUtils.isEmpty(currentUser.getOptionalDirections())
                ? currentUser.getOptionalDirections()
                : (!TextUtils.isEmpty(currentUser.getLocationNotes()) ? currentUser.getLocationNotes() : "");

        final boolean isUrgent = cbExpressDelivery != null && cbExpressDelivery.isChecked();

        final WaterOrder newOrder = new WaterOrder(
                orderId,
                currentUser.getUid(),
                currentUser.getName(),
                currentUser.getPhone(),
                finalLocName,
                finalRoom,
                finalFloor,
                finalLat,
                finalLng,
                refillJarCount,
                extraDepositJars,
                returnJarCount,
                calculatedTotal,
                isBkash ? WaterOrder.PAYMENT_BKASH : WaterOrder.PAYMENT_CASH,
                bkashTrxId,
                isUrgent,
                WaterOrder.STATUS_PENDING,
                System.currentTimeMillis(),
                qrToken
        );

        newOrder.setLocationNotes(exactLocationNotes);
        newOrder.setOptionalDirections(exactLocationNotes);

        final String assignedSellerId = selectedOnlineSeller.getUid();
        final String depot = !TextUtils.isEmpty(selectedOnlineSeller.getDepotOrDriverTitle())
                ? selectedOnlineSeller.getDepotOrDriverTitle()
                : "Campus Depot";
        final String assignedSellerName = selectedOnlineSeller.getName() + " (" + depot + ")";

        newOrder.setSellerId(assignedSellerId);
        newOrder.setAssignedSellerId(assignedSellerId);
        newOrder.setAssignedSellerName(assignedSellerName);

        // Add immediately to local buyer map for instantaneous responsiveness
        buyerOrdersMap.put(orderId, newOrder);
        refreshBuyerOrdersUi();

        // Dual-storage write: Realtime Database (guaranteed online sync) + Cloud Firestore
        final String finalExactDirections = exactLocationNotes;
        final double latForIntent = finalLat;
        final double lngForIntent = finalLng;

        FirebaseManager.getInstance().saveOrder(newOrder,
                new com.google.android.gms.tasks.OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        isSubmittingOrder = false;
                        if (btnPlaceOrder != null) {
                            btnPlaceOrder.setEnabled(!onlineSellersList.isEmpty());
                            btnPlaceOrder.setText("Place Water Order");
                        }
                        if (cbExpressDelivery != null) {
                            cbExpressDelivery.setChecked(false);
                        }

                        Toast.makeText(BuyerDashboardActivity.this, "Order " + orderId + " placed!", Toast.LENGTH_SHORT).show();

                        // Week 10 Component: BroadcastReceiver alert intent
                        Intent alertIntent = new Intent(DeliveryStatusReceiver.ACTION_ORDER_ALERT);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_ORDER_ID, orderId);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_STATUS, WaterOrder.STATUS_PENDING);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_MESSAGE, "Order #" + orderId + " placed! Waiting for depot dispatch.");
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_BUYER_LAT, latForIntent);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_BUYER_LNG, lngForIntent);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_LOCATION_NAME, finalLocName);
                        alertIntent.putExtra("locationNotes", finalExactDirections);
                        alertIntent.putExtra("optionalDirections", finalExactDirections);
                        alertIntent.putExtra("isUrgent", isUrgent);
                        sendBroadcast(alertIntent);

                        // Week 10 Component: ContentProvider record insertion
                        try {
                            ContentValues cv = new ContentValues();
                            cv.put(AquaDropContentProvider.COL_ORDER_ID, orderId);
                            cv.put(AquaDropContentProvider.COL_BUYER_NAME, currentUser.getName());
                            cv.put(AquaDropContentProvider.COL_BUYER_PHONE, currentUser.getPhone());
                            cv.put(AquaDropContentProvider.COL_LOCATION, finalLocName);
                            cv.put(AquaDropContentProvider.COL_STATUS, WaterOrder.STATUS_PENDING);
                            cv.put(AquaDropContentProvider.COL_TOTAL_PRICE, calculatedTotal);
                            getContentResolver().insert(AquaDropContentProvider.CONTENT_URI_ORDERS, cv);
                            Log.d("BuyerDashboard", "Order synced via ContentProvider: " + orderId);
                        } catch (Exception cpErr) {
                            Log.w("BuyerDashboard", "ContentProvider sync notice: " + cpErr.getMessage());
                        }

                        // Reset form
                        refillJarCount = 1;
                        returnJarCount = 1;
                        if (tvRefillCount != null) tvRefillCount.setText("1");
                        if (tvReturnCount != null) tvReturnCount.setText("1");
                        if (rbCashOnDelivery != null) rbCashOnDelivery.setChecked(true);
                        if (etBkashTrxId != null) etBkashTrxId.setText("");
                        recalculateOrderTotal();

                        // Present handover QR dialog safely without any chance of crash
                        try {
                            if (!isFinishing() && !isDestroyed()) {
                                showHandoverQrDialog(newOrder);
                            }
                        } catch (Exception e) {
                            Log.e("BuyerDashboard", "Error presenting QR dialog: " + e.getMessage(), e);
                        }
                    }
                },
                new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        isSubmittingOrder = false;
                        if (btnPlaceOrder != null) {
                            btnPlaceOrder.setEnabled(!onlineSellersList.isEmpty());
                            btnPlaceOrder.setText("Place Water Order");
                        }
                        if (cbExpressDelivery != null) {
                            cbExpressDelivery.setChecked(false);
                        }

                        Toast.makeText(BuyerDashboardActivity.this, "Order placed locally. Network notice: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        try {
                            if (!isFinishing() && !isDestroyed()) {
                                showHandoverQrDialog(newOrder);
                            }
                        } catch (Exception ignored) {}
                    }
                });
    }

    // --- Dialogs ---

    private void showHandoverQrDialog(WaterOrder order) {
        if (isFinishing() || isDestroyed() || order == null) {
            return;
        }

        try {
            final Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_qr_code);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            ImageView ivQr = dialog.findViewById(R.id.ivQrCode);
            TextView tvToken = dialog.findViewById(R.id.tvDialogTokenValue);
            MaterialButton btnClose = dialog.findViewById(R.id.btnDialogCloseQr);

            String qrToken = order.getQrToken() != null ? order.getQrToken() : ("AQ-" + order.getOrderId());
            tvToken.setText(qrToken);

            // ZXing 500x500 QR Code Generation
            try {
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Bitmap bitmap = barcodeEncoder.encodeBitmap(qrToken, BarcodeFormat.QR_CODE, 500, 500);
                ivQr.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e("BuyerDashboard", "Could not render QR: " + e.getMessage());
            }

            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

            dialog.show();
        } catch (Exception e) {
            Log.e("BuyerDashboard", "Error showing handover dialog: " + e.getMessage(), e);
        }
    }

    private void showMessBillSplitterDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_mess_splitter);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        final TextInputEditText etCost = dialog.findViewById(R.id.etTotalWaterCost);
        final TextInputEditText etRoommates = dialog.findViewById(R.id.etRoommatesCount);
        final TextView tvShare = dialog.findViewById(R.id.tvPerPersonShare);
        MaterialButton btnCalculate = dialog.findViewById(R.id.btnCalculateSplit);
        MaterialButton btnDismiss = dialog.findViewById(R.id.btnSplitDismiss);

        btnCalculate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String costStr = etCost.getText() != null ? etCost.getText().toString().trim() : "";
                String countStr = etRoommates.getText() != null ? etRoommates.getText().toString().trim() : "";

                if (TextUtils.isEmpty(costStr) || TextUtils.isEmpty(countStr)) {
                    Toast.makeText(BuyerDashboardActivity.this, "Enter both total cost and roommate count", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    double totalCost = Double.parseDouble(costStr);
                    int roommates = Integer.parseInt(countStr);

                    if (roommates <= 0) {
                        etRoommates.setError("Roommates must be at least 1");
                        return;
                    }

                    double perPerson = totalCost / roommates;
                    tvShare.setText(String.format(Locale.getDefault(), "%.2f BDT / person", perPerson));
                } catch (NumberFormatException e) {
                    Toast.makeText(BuyerDashboardActivity.this, "Invalid number format", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnDismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    // --- Order Action Callbacks from Adapter ---

    @Override
    public void onTrackLiveClicked(WaterOrder order) {
        Intent intent = new Intent(this, LiveTrackingActivity.class);
        intent.putExtra("orderId", order.getOrderId());
        intent.putExtra("buyerLat", order.getBuyerLat());
        intent.putExtra("buyerLng", order.getBuyerLng());
        intent.putExtra("floorNumber", order.getFloorNumber());
        intent.putExtra("locationName", order.getLocationName());
        startActivity(intent);
    }

    @Override
    public void onShowQrClicked(WaterOrder order) {
        showHandoverQrDialog(order);
    }

    @Override
    public void onCancelOrderClicked(final WaterOrder order) {
        if (order == null || TextUtils.isEmpty(order.getOrderId())) {
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cancel Order #" + order.getOrderId())
                .setMessage("Are you sure you want to cancel this water delivery request? This action cannot be undone.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Cancel", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        order.setStatus(WaterOrder.STATUS_CANCELLED);
                        buyerOrdersMap.put(order.getOrderId(), order);
                        refreshBuyerOrdersUi();

                        final Map<String, Object> updates = new HashMap<>();
                        updates.put("status", WaterOrder.STATUS_CANCELLED);

                        FirebaseManager.getInstance().updateOrderFields(order.getOrderId(), updates,
                                new com.google.android.gms.tasks.OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Toast.makeText(BuyerDashboardActivity.this,
                                                "Order #" + order.getOrderId() + " cancelled successfully.",
                                                Toast.LENGTH_SHORT).show();

                                        // Week 10: Broadcast cancellation event
                                        Intent cancelIntent = new Intent(DeliveryStatusReceiver.ACTION_DELIVERY_UPDATE);
                                        cancelIntent.putExtra(DeliveryStatusReceiver.EXTRA_ORDER_ID, order.getOrderId());
                                        cancelIntent.putExtra(DeliveryStatusReceiver.EXTRA_STATUS, WaterOrder.STATUS_CANCELLED);
                                        cancelIntent.putExtra(DeliveryStatusReceiver.EXTRA_MESSAGE, "Order #" + order.getOrderId() + " has been cancelled.");
                                        sendBroadcast(cancelIntent);
                                    }
                                },
                                new com.google.android.gms.tasks.OnFailureListener() {
                                    @Override
                                    public void onFailure(Exception e) {
                                        Toast.makeText(BuyerDashboardActivity.this,
                                                "Order marked cancelled locally. Sync notice: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton("Keep Order", null)
                .show();
    }

    // --- Week 8 Syllabus: Activity Lifecycle Callbacks & Observation ---

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("AquaDropLifecycle", "BuyerDashboardActivity: onStart() called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("AquaDropLifecycle", "BuyerDashboardActivity: onResume() called");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("AquaDropLifecycle", "BuyerDashboardActivity: onPause() called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d("AquaDropLifecycle", "BuyerDashboardActivity: onStop() called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("AquaDropLifecycle", "BuyerDashboardActivity: onDestroy() called");
        if (userListenerRegistration != null) userListenerRegistration.remove();
        if (ordersListenerRegistration != null) ordersListenerRegistration.remove();
        if (qualityListenerRegistration != null) qualityListenerRegistration.remove();
        if (rtdbUserListener != null) FirebaseManager.getInstance().getRtdbUsersRef().removeEventListener(rtdbUserListener);
        if (rtdbBuyerOrdersListener != null) FirebaseManager.getInstance().getRtdbOrdersRef().removeEventListener(rtdbBuyerOrdersListener);
        if (rtdbQualityListener != null) FirebaseManager.getInstance().getRtdbWaterQualityRef().removeEventListener(rtdbQualityListener);
        if (rtdbGateAlertListener != null) {
            FirebaseUser u = FirebaseManager.getInstance().getCurrentUser();
            if (u != null) {
                FirebaseManager.getInstance().getDatabase().getReference("buyer_alerts")
                        .child(u.getUid()).removeEventListener(rtdbGateAlertListener);
            }
        }
        if (rtdbOnlineSellersListener != null) {
            FirebaseManager.getInstance().getDatabase().getReference("online_sellers")
                    .removeEventListener(rtdbOnlineSellersListener);
        }
        if (firestoreOnlineSellersListener != null) {
            firestoreOnlineSellersListener.remove();
        }
    }

    // --- Week 8 Syllabus: Implicit Intent Demonstration ---

    /**
     * Demonstrates an Implicit Intent (Intent.ACTION_SEND) to share order receipt / invite peers.
     */
    public void shareOrderReceipt(WaterOrder order) {
        if (order == null) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareBody = String.format(Locale.getDefault(),
                "AquaDrop KUET Water Delivery Receipt\nOrder: %s\nJars: %d x 20L\nTotal: %.0f BDT\nDelivery Location: %s\nTrack & Order via AquaDrop!",
                order.getOrderId(), order.getJarRefillCount(), order.getTotalPrice(), order.getLocationName());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "AquaDrop Order #" + order.getOrderId());
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(shareIntent, "Share Order Receipt via"));
    }
}
