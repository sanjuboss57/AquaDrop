package com.kuet.aquadrop.activities;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
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
    private static final int SECURITY_DEPOSIT_PER_JAR = 200;
    private static final int EXPRESS_DELIVERY_SURCHARGE = 15;

    // Header Views
    private TextView tvBuyerGreeting;
    private TextView tvBuyerLocationHeader;
    private ImageView ivBuyerProfile;
    private ImageView ivLogout;

    // Water Quality Views
    private TextView tvTdsLevel;
    private TextView tvPhLevel;
    private TextView tvBatchStatus;

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

        initViews();
        setupOrderCounters();
        setupPaymentOptions();
        setupRecyclerView();
        setupListeners();
        subscribeToFirestoreData();
    }

    private void initViews() {
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
    }

    private void subscribeToFirestoreData() {
        FirebaseUser user = FirebaseManager.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        final String uid = user.getUid();

        // 1. Real-time User Profile & Bottle Ledger
        userListenerRegistration = FirebaseManager.getInstance().getUsersRef().document(uid)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException error) {
                        if (error != null || snapshot == null || !snapshot.exists()) {
                            return;
                        }

                        currentUser = snapshot.toObject(User.class);
                        if (currentUser != null) {
                            tvBuyerGreeting.setText(String.format("Hello, %s", currentUser.getName()));
                            tvBuyerLocationHeader.setText(String.format(Locale.getDefault(),
                                    "%s • Room %s (Fl %d)",
                                    currentUser.getLocationName(),
                                    currentUser.getRoomOrFlatNo(),
                                    currentUser.getFloorNumber()));

                            tvEmptyJarsHeld.setText(String.format(Locale.getDefault(), "%d Jars", currentUser.getEmptyJarsHeld()));
                            tvDepositBalance.setText(String.format(Locale.getDefault(), "%.0f BDT", currentUser.getDepositBalance()));

                            // Synchronize order GPS state with profile data
                            if (TextUtils.isEmpty(orderLocationName)) {
                                orderLocationName = currentUser.getLocationName();
                                orderRoom = currentUser.getRoomOrFlatNo();
                                orderFloor = currentUser.getFloorNumber();
                                orderLatitude = currentUser.getLatitude();
                                orderLongitude = currentUser.getLongitude();

                                if (orderLatitude == 0.0 || orderLongitude == 0.0) {
                                    LatLng p = KuetLocations.getLatLngByName(orderLocationName);
                                    if (p != null) {
                                        orderLatitude = p.latitude;
                                        orderLongitude = p.longitude;
                                    }
                                }
                                updateDeliveryGpsDisplay();
                            }
                        }
                    }
                });

        // 2. Real-time Water Quality Badge
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
                                return;
                            }
                        }
                        // Default fallback
                        tvTdsLevel.setText("42 ppm");
                        tvPhLevel.setText("7.2 pH");
                        tvBatchStatus.setText("Batch: Certified Reverse Osmosis + UV Sterilized");
                    }
                });

        // 3. Real-time Orders for Current Buyer
        ordersListenerRegistration = FirebaseManager.getInstance().getOrdersRef()
                .whereEqualTo("buyerId", uid)
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            android.util.Log.e("BuyerDashboard", "Error listening to orders: " + error.getMessage(), error);
                            return;
                        }
                        if (queryDocumentSnapshots == null) {
                            return;
                        }

                        List<WaterOrder> orders = new ArrayList<>();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            WaterOrder order = WaterOrder.fromSnapshot(doc);
                            if (order != null) {
                                orders.add(order);
                            }
                        }

                        // In-memory timestamp sorting (avoids composite index requirements)
                        Collections.sort(orders, new Comparator<WaterOrder>() {
                            @Override
                            public int compare(WaterOrder o1, WaterOrder o2) {
                                if (o1 == null && o2 == null) return 0;
                                if (o1 == null) return 1;
                                if (o2 == null) return -1;
                                return Long.compare(o2.getTimestamp(), o1.getTimestamp());
                            }
                        });

                        orderAdapter.setOrders(orders);

                        if (orders.isEmpty()) {
                            tvNoOrdersPlaceholder.setVisibility(View.VISIBLE);
                            rvBuyerOrders.setVisibility(View.GONE);
                        } else {
                            tvNoOrdersPlaceholder.setVisibility(View.GONE);
                            rvBuyerOrders.setVisibility(View.VISIBLE);
                        }
                    }
                });
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

                int f = 1;
                try { f = Integer.parseInt(floorStr); } catch (Exception ignored) {}

                orderLocationName = selLoc;
                orderRoom = !TextUtils.isEmpty(selRoom) ? selRoom : "101";
                orderFloor = f;
                orderLatitude = tempCoords[0];
                orderLongitude = tempCoords[1];

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

        FirebaseManager.getInstance().getOrdersRef().document(orderId).set(newOrder)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        btnPlaceOrder.setEnabled(true);
                        Toast.makeText(BuyerDashboardActivity.this, "Order " + orderId + " placed!", Toast.LENGTH_SHORT).show();

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
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        btnPlaceOrder.setEnabled(true);
                        Toast.makeText(BuyerDashboardActivity.this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
    protected void onDestroy() {
        super.onDestroy();
        if (userListenerRegistration != null) userListenerRegistration.remove();
        if (ordersListenerRegistration != null) ordersListenerRegistration.remove();
        if (qualityListenerRegistration != null) qualityListenerRegistration.remove();
    }
}
