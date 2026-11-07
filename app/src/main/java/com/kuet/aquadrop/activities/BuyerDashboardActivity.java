package com.kuet.aquadrop.activities;

import android.Manifest;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import com.google.android.material.card.MaterialCardView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
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
import com.kuet.aquadrop.utils.CampusWeatherApiClient;
import com.kuet.aquadrop.utils.FirebaseManager;
import com.kuet.aquadrop.utils.KuetLocations;

import android.content.ContentValues;

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
    private static final int SECURITY_DEPOSIT_PER_JAR = 200;
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
    private TextView tvBuyerLocationHeader;
    private ImageView ivBuyerProfile;
    private ImageView ivLogout;

    // Water Quality Views
    private TextView tvTdsLevel;
    private TextView tvPhLevel;
    private TextView tvBatchStatus;

    // Live Campus Weather Views (Week 11 HTTP REST API + JSON)
    private TextView tvWeatherTemp;
    private TextView tvWeatherHumidity;
    private TextView tvWeatherAdvisory;
    private ImageView ivRefreshWeatherApi;

    // Bottle Ledger Views
    private TextView tvEmptyJarsHeld;
    private TextView tvDepositBalance;
    private MaterialButton btnOpenSplitter;

    // Delivery GPS Location Views
    private LinearLayout llDeliveryGpsSelector;
    private TextView tvSelectedDeliveryLocation;
    private TextView tvSelectedDeliveryGpsCoords;
    private MaterialButton btnChangeDeliveryGps;

    // Order Form Views
    private MaterialButton btnRefillMinus;
    private TextView tvRefillCount;
    private MaterialButton btnRefillPlus;

    private MaterialButton btnReturnMinus;
    private TextView tvReturnCount;
    private MaterialButton btnReturnPlus;

    private TextView tvDepositNotice;
    private CheckBox cbExpressDelivery;
    private TextView tvExpressAlertNotice;
    private TextInputEditText etExactLocationDirections;

    private RadioGroup rgPaymentMethod;
    private RadioButton rbCashOnDelivery;
    private RadioButton rbBkash;
    private TextInputLayout tilBkashTrxId;
    private TextInputEditText etBkashTrxId;

    private TextView tvOrderTotalPrice;
    private MaterialButton btnPlaceOrder;

    // Orders RecyclerView
    private RecyclerView rvBuyerOrders;
    private TextView tvNoOrdersPlaceholder;
    private BuyerOrderAdapter orderAdapter;

    // State Variables
    private User currentUser;
    private int refillJarCount = 1;
    private int returnJarCount = 1;
    private double calculatedTotal = PRICE_REFILL_JAR;

    // Delivery GPS State
    private double orderLatitude = 22.8998;
    private double orderLongitude = 89.5015;
    private String orderLocationName = "";
    private String orderRoom = "";
    private int orderFloor = 1;
    private FusedLocationProviderClient fusedLocationClient;

    // Firestore Registrations
    private ListenerRegistration userListenerRegistration;
    private ListenerRegistration ordersListenerRegistration;
    private ListenerRegistration qualityListenerRegistration;

    // Realtime Database Listeners & Map
    private ValueEventListener rtdbUserListener;
    private ValueEventListener rtdbBuyerOrdersListener;
    private ValueEventListener rtdbQualityListener;
    private final Map<String, WaterOrder> buyerOrdersMap = new HashMap<>();

    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    detectDeviceGpsForDelivery();
                } else {
                    Toast.makeText(this, "GPS permission denied. Using selected campus building GPS.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_dashboard);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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
        refreshCampusWeather();
    }

    private void initViews() {
        cardGateAlertBanner = findViewById(R.id.cardGateAlertBanner);
        tvGateAlertMessage = findViewById(R.id.tvGateAlertMessage);
        ivDismissGateAlert = findViewById(R.id.ivDismissGateAlert);

        tvBuyerGreeting = findViewById(R.id.tvBuyerGreeting);
        tvBuyerLocationHeader = findViewById(R.id.tvBuyerLocationHeader);
        ivBuyerProfile = findViewById(R.id.ivBuyerProfile);
        ivLogout = findViewById(R.id.ivLogout);

        llDeliveryGpsSelector = findViewById(R.id.llDeliveryGpsSelector);
        tvSelectedDeliveryLocation = findViewById(R.id.tvSelectedDeliveryLocation);
        tvSelectedDeliveryGpsCoords = findViewById(R.id.tvSelectedDeliveryGpsCoords);
        btnChangeDeliveryGps = findViewById(R.id.btnChangeDeliveryGps);

        tvTdsLevel = findViewById(R.id.tvTdsLevel);
        tvPhLevel = findViewById(R.id.tvPhLevel);
        tvBatchStatus = findViewById(R.id.tvBatchStatus);

        tvWeatherTemp = findViewById(R.id.tvWeatherTemp);
        tvWeatherHumidity = findViewById(R.id.tvWeatherHumidity);
        tvWeatherAdvisory = findViewById(R.id.tvWeatherAdvisory);
        ivRefreshWeatherApi = findViewById(R.id.ivRefreshWeatherApi);

        tvEmptyJarsHeld = findViewById(R.id.tvEmptyJarsHeld);
        tvDepositBalance = findViewById(R.id.tvDepositBalance);
        btnOpenSplitter = findViewById(R.id.btnOpenSplitter);

        btnRefillMinus = findViewById(R.id.btnRefillMinus);
        tvRefillCount = findViewById(R.id.tvRefillCount);
        btnRefillPlus = findViewById(R.id.btnRefillPlus);

        btnReturnMinus = findViewById(R.id.btnReturnMinus);
        tvReturnCount = findViewById(R.id.tvReturnCount);
        btnReturnPlus = findViewById(R.id.btnReturnPlus);

        tvDepositNotice = findViewById(R.id.tvDepositNotice);
        cbExpressDelivery = findViewById(R.id.cbExpressDelivery);
        tvExpressAlertNotice = findViewById(R.id.tvExpressAlertNotice);
        etExactLocationDirections = findViewById(R.id.etExactLocationDirections);

        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        rbCashOnDelivery = findViewById(R.id.rbCashOnDelivery);
        rbBkash = findViewById(R.id.rbBkash);
        tilBkashTrxId = findViewById(R.id.tilBkashTrxId);
        etBkashTrxId = findViewById(R.id.etBkashTrxId);

        tvOrderTotalPrice = findViewById(R.id.tvOrderTotalPrice);
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);

        rvBuyerOrders = findViewById(R.id.rvBuyerOrders);
        tvNoOrdersPlaceholder = findViewById(R.id.tvNoOrdersPlaceholder);
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
        tvDepositNotice.setVisibility(View.VISIBLE);
        tvDepositNotice.setBackgroundColor(Color.parseColor("#E8F5E9"));
        tvDepositNotice.setTextColor(Color.parseColor("#2E7D32"));
        tvDepositNotice.setText("✓ Returning empty jars is 100% FREE (0 BDT). Only refill water is charged.");

        tvOrderTotalPrice.setText(String.format(Locale.getDefault(), "%.0f BDT", calculatedTotal));
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
        rvBuyerOrders.setLayoutManager(new LinearLayoutManager(this));
        orderAdapter = new BuyerOrderAdapter(this);
        rvBuyerOrders.setAdapter(orderAdapter);
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

        View.OnClickListener profileOpener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BuyerDashboardActivity.this, ProfileActivity.class));
            }
        };

        if (ivBuyerProfile != null) {
            ivBuyerProfile.setOnClickListener(profileOpener);
        }
        tvBuyerGreeting.setOnClickListener(profileOpener);
        tvBuyerLocationHeader.setOnClickListener(profileOpener);

        View.OnClickListener gpsSelectListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelectDeliveryGpsDialog();
            }
        };

        if (btnChangeDeliveryGps != null) {
            btnChangeDeliveryGps.setOnClickListener(gpsSelectListener);
        }
        if (llDeliveryGpsSelector != null) {
            llDeliveryGpsSelector.setOnClickListener(gpsSelectListener);
        }

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

        btnOpenSplitter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessBillSplitterDialog();
            }
        });

        btnPlaceOrder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                placeWaterOrder();
            }
        });

        if (ivRefreshWeatherApi != null) {
            ivRefreshWeatherApi.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    refreshCampusWeather();
                }
            });
        }
    }

    /**
     * Week 11 Syllabus: Networking & Data Handling via HTTP REST API and JSON parsing.
     * Connects to Open-Meteo REST API using native HttpURLConnection on background ExecutorService.
     */
    private void refreshCampusWeather() {
        if (tvWeatherAdvisory != null) {
            tvWeatherAdvisory.setText("Connecting to Open-Meteo REST API via HTTP GET (JSON parsing)...");
        }
        CampusWeatherApiClient.fetchCampusHydrationMetrics(new CampusWeatherApiClient.WeatherCallback() {
            @Override
            public void onSuccess(double temperatureCelsius, double humidityPercent, String hydrationAdvisory) {
                if (tvWeatherTemp != null) {
                    tvWeatherTemp.setText(String.format(Locale.getDefault(), "%.1f °C", temperatureCelsius));
                }
                if (tvWeatherHumidity != null) {
                    tvWeatherHumidity.setText(String.format(Locale.getDefault(), "%.0f %%", humidityPercent));
                }
                if (tvWeatherAdvisory != null) {
                    tvWeatherAdvisory.setText(hydrationAdvisory);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                if (tvWeatherAdvisory != null) {
                    tvWeatherAdvisory.setText("Offline Advisory: Maintain 3L healthy hydration on campus.");
                }
            }
        });
    }

    private void applyBuyerProfile(User user) {
        if (user == null) return;
        this.currentUser = user;
        tvBuyerGreeting.setText(String.format("Hello, %s", user.getName()));
        tvBuyerLocationHeader.setText(String.format(Locale.getDefault(),
                "%s • Room %s (Fl %d)",
                user.getLocationName(),
                user.getRoomOrFlatNo(),
                user.getFloorNumber()));

        tvEmptyJarsHeld.setText(String.format(Locale.getDefault(), "%d Jars", user.getEmptyJarsHeld()));
        tvDepositBalance.setText(String.format(Locale.getDefault(), "%.0f BDT", user.getDepositBalance()));

        // Synchronize order GPS state with profile data
        if (TextUtils.isEmpty(orderLocationName)) {
            orderLocationName = user.getLocationName();
            orderRoom = user.getRoomOrFlatNo();
            orderFloor = user.getFloorNumber();
            orderLatitude = user.getLatitude();
            orderLongitude = user.getLongitude();

            if (orderLatitude == 0.0 || orderLongitude == 0.0) {
                LatLng p = KuetLocations.getLatLngByName(orderLocationName);
                if (p != null) {
                    orderLatitude = p.latitude;
                    orderLongitude = p.longitude;
                }
            }
            updateDeliveryGpsDisplay();
        }

        if (etExactLocationDirections != null && !TextUtils.isEmpty(user.getLocationNotes())
                && TextUtils.isEmpty(etExactLocationDirections.getText())) {
            etExactLocationDirections.setText(user.getLocationNotes());
        }
    }

    private void refreshBuyerOrdersUi() {
        List<WaterOrder> orders = new ArrayList<>(buyerOrdersMap.values());
        Collections.sort(orders, new Comparator<WaterOrder>() {
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

        orderAdapter.setOrders(orders);
        orderAdapter.notifyDataSetChanged();

        if (orders.isEmpty()) {
            tvNoOrdersPlaceholder.setVisibility(View.VISIBLE);
            rvBuyerOrders.setVisibility(View.GONE);
        } else {
            tvNoOrdersPlaceholder.setVisibility(View.GONE);
            rvBuyerOrders.setVisibility(View.VISIBLE);
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
                            tvTdsLevel.setText(String.format(Locale.getDefault(), "%.0f ppm", quality.getTds()));
                            tvPhLevel.setText(String.format(Locale.getDefault(), "%.1f pH", quality.getPh()));
                            tvBatchStatus.setText(String.format("Batch: %s", quality.getBatchStatus()));
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
                                    tvTdsLevel.setText(String.format(Locale.getDefault(), "%.0f ppm", quality.getTds()));
                                    tvPhLevel.setText(String.format(Locale.getDefault(), "%.1f pH", quality.getPh()));
                                    tvBatchStatus.setText(String.format("Batch: %s", quality.getBatchStatus()));
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

    private void updateDeliveryGpsDisplay() {
        if (tvSelectedDeliveryLocation != null) {
            tvSelectedDeliveryLocation.setText(String.format(Locale.getDefault(),
                    "%s • Room %s (Fl %d)",
                    !TextUtils.isEmpty(orderLocationName) ? orderLocationName : "Campus Hall",
                    !TextUtils.isEmpty(orderRoom) ? orderRoom : "101",
                    orderFloor));
        }
        if (tvSelectedDeliveryGpsCoords != null) {
            tvSelectedDeliveryGpsCoords.setText(String.format(Locale.getDefault(),
                    "GPS: %.5f° N, %.5f° E", orderLatitude, orderLongitude));
        }
    }

    private void showSelectDeliveryGpsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_select_gps);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        final Spinner spinner = dialog.findViewById(R.id.spinnerSelectGpsLocation);
        final TextInputEditText etRoom = dialog.findViewById(R.id.etSelectGpsRoom);
        final TextInputEditText etFloor = dialog.findViewById(R.id.etSelectGpsFloor);
        final TextInputEditText etSelectGpsDirections = dialog.findViewById(R.id.etSelectGpsDirections);
        final TextView tvCoords = dialog.findViewById(R.id.tvActiveGpsCoords);
        MaterialButton btnDetect = dialog.findViewById(R.id.btnDetectDeviceGps);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancelSelectGps);
        MaterialButton btnConfirm = dialog.findViewById(R.id.btnConfirmSelectGps);

        final double[] tempCoords = new double[]{orderLatitude, orderLongitude};

        List<String> locations = KuetLocations.getLocationNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, locations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int idx = locations.indexOf(!TextUtils.isEmpty(orderLocationName) ? orderLocationName : (currentUser != null ? currentUser.getLocationName() : ""));
        if (idx >= 0) spinner.setSelection(idx);

        etRoom.setText(!TextUtils.isEmpty(orderRoom) ? orderRoom : (currentUser != null ? currentUser.getRoomOrFlatNo() : ""));
        etFloor.setText(String.valueOf(orderFloor > 0 ? orderFloor : (currentUser != null ? currentUser.getFloorNumber() : 1)));
        if (etSelectGpsDirections != null && etExactLocationDirections != null && !TextUtils.isEmpty(etExactLocationDirections.getText())) {
            etSelectGpsDirections.setText(etExactLocationDirections.getText());
        }
        tvCoords.setText(String.format(Locale.getDefault(), "%.6f° N, %.6f° E", tempCoords[0], tempCoords[1]));

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String loc = (String) parent.getItemAtPosition(position);
                LatLng p = KuetLocations.getLatLngByName(loc);
                if (p != null) {
                    tempCoords[0] = p.latitude;
                    tempCoords[1] = p.longitude;
                    tvCoords.setText(String.format(Locale.getDefault(), "%.6f° N, %.6f° E", tempCoords[0], tempCoords[1]));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(BuyerDashboardActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener(loc -> {
                        if (loc != null) {
                            tempCoords[0] = loc.getLatitude();
                            tempCoords[1] = loc.getLongitude();
                            tvCoords.setText(String.format(Locale.getDefault(), "%.6f° N, %.6f° E (Live Device)", tempCoords[0], tempCoords[1]));
                            Toast.makeText(BuyerDashboardActivity.this, "Live GPS coordinates detected!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(BuyerDashboardActivity.this, "Could not acquire GPS fix. Ensure GPS is enabled.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
                }
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selLoc = (String) spinner.getSelectedItem();
                String selRoom = etRoom.getText() != null ? etRoom.getText().toString().trim() : "";
                String floorStr = etFloor.getText() != null ? etFloor.getText().toString().trim() : "1";
                String directions = etSelectGpsDirections != null && etSelectGpsDirections.getText() != null
                        ? etSelectGpsDirections.getText().toString().trim() : "";

                int f = 1;
                try { f = Integer.parseInt(floorStr); } catch (Exception ignored) {}

                orderLocationName = selLoc;
                orderRoom = !TextUtils.isEmpty(selRoom) ? selRoom : "101";
                orderFloor = f;
                orderLatitude = tempCoords[0];
                orderLongitude = tempCoords[1];

                if (etExactLocationDirections != null && !TextUtils.isEmpty(directions)) {
                    etExactLocationDirections.setText(directions);
                }

                updateDeliveryGpsDisplay();
                Toast.makeText(BuyerDashboardActivity.this, "Delivery GPS set to " + selLoc, Toast.LENGTH_SHORT).show();
                dialog.dismiss();

                // Silently sync updated delivery location & GPS to buyer's profile in Firestore
                if (currentUser != null) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("locationName", selLoc);
                    update.put("roomOrFlatNo", orderRoom);
                    update.put("floorNumber", orderFloor);
                    update.put("latitude", orderLatitude);
                    update.put("longitude", orderLongitude);
                    if (!TextUtils.isEmpty(directions)) {
                        update.put("locationNotes", directions);
                    }
                    FirebaseManager.getInstance().getUsersRef().document(currentUser.getUid()).update(update);
                }
            }
        });

        dialog.show();
    }

    private void detectDeviceGpsForDelivery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener(loc -> {
            if (loc != null) {
                orderLatitude = loc.getLatitude();
                orderLongitude = loc.getLongitude();
                updateDeliveryGpsDisplay();
                Toast.makeText(this, "Live GPS coordinates updated!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void placeWaterOrder() {
        if (currentUser == null) {
            Toast.makeText(this, "Profile not loaded yet. Please wait…", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean isBkash = rbBkash.isChecked();
        final String bkashTrxId = etBkashTrxId.getText() != null ? etBkashTrxId.getText().toString().trim() : "";

        if (isBkash && TextUtils.isEmpty(bkashTrxId)) {
            etBkashTrxId.setError("Enter bKash Transaction ID");
            etBkashTrxId.requestFocus();
            return;
        }

        btnPlaceOrder.setEnabled(false);

        // Generate IDs
        final String orderId = "AQ-" + (System.currentTimeMillis() % 1000000);
        final String qrToken = "AQ-VERIFY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        final int extraDepositJars = 0; // Empty jars are free (0 BDT)

        final String finalLocName = !TextUtils.isEmpty(orderLocationName) ? orderLocationName : currentUser.getLocationName();
        final String finalRoom = !TextUtils.isEmpty(orderRoom) ? orderRoom : currentUser.getRoomOrFlatNo();
        final int finalFloor = orderFloor > 0 ? orderFloor : currentUser.getFloorNumber();
        final double finalLat = orderLatitude != 0.0 ? orderLatitude : currentUser.getLatitude();
        final double finalLng = orderLongitude != 0.0 ? orderLongitude : currentUser.getLongitude();

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
                cbExpressDelivery.isChecked(),
                WaterOrder.STATUS_PENDING,
                System.currentTimeMillis(),
                qrToken
        );

        final String exactLocationNotes = etExactLocationDirections != null && etExactLocationDirections.getText() != null
                ? etExactLocationDirections.getText().toString().trim() : "";
        newOrder.setLocationNotes(exactLocationNotes);

        // Add immediately to local buyer map for instantaneous responsiveness
        buyerOrdersMap.put(orderId, newOrder);
        refreshBuyerOrdersUi();

        // Dual-storage write: Realtime Database (guaranteed online sync) + Cloud Firestore
        FirebaseManager.getInstance().saveOrder(newOrder,
                new com.google.android.gms.tasks.OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        btnPlaceOrder.setEnabled(true);
                        Toast.makeText(BuyerDashboardActivity.this, "Order " + orderId + " placed!", Toast.LENGTH_SHORT).show();

                        // Week 10 Component: BroadcastReceiver alert intent
                        Intent alertIntent = new Intent(DeliveryStatusReceiver.ACTION_ORDER_ALERT);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_ORDER_ID, orderId);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_STATUS, WaterOrder.STATUS_PENDING);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_MESSAGE, "Order #" + orderId + " placed! Waiting for depot dispatch.");
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_BUYER_LAT, finalLat);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_BUYER_LNG, finalLng);
                        alertIntent.putExtra(DeliveryStatusReceiver.EXTRA_LOCATION_NAME, finalLocName);
                        alertIntent.putExtra("locationNotes", exactLocationNotes);
                        alertIntent.putExtra("isUrgent", cbExpressDelivery.isChecked());
                        sendBroadcast(alertIntent);

                        if (currentUser != null && !TextUtils.isEmpty(exactLocationNotes)) {
                            FirebaseManager.getInstance().getUsersRef().document(currentUser.getUid()).update("locationNotes", exactLocationNotes);
                        }

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
                        tvRefillCount.setText("1");
                        tvReturnCount.setText("1");
                        cbExpressDelivery.setChecked(false);
                        rbCashOnDelivery.setChecked(true);
                        etBkashTrxId.setText("");
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
                        btnPlaceOrder.setEnabled(true);
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
