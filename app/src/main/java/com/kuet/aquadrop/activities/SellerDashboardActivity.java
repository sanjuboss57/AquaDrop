package com.kuet.aquadrop.activities;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.adapters.SellerOrderAdapter;
import com.kuet.aquadrop.models.WaterOrder;
import com.kuet.aquadrop.models.WaterQuality;
import com.kuet.aquadrop.services.LocationBroadcastService;
import com.kuet.aquadrop.utils.FirebaseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SellerDashboardActivity extends AppCompatActivity implements SellerOrderAdapter.OnSellerOrderActionListener {

    // Summary Card Views
    private TextView tvSummaryPendingOrders;
    private TextView tvSummaryActiveDeliveries;
    private TextView tvSummaryEmptyCollected;
    private TextView tvSummaryTotalRevenue;
    private MaterialButton btnUpdateWaterQuality;
    private ImageView ivSellerLogout;

    // Dispatch Queue Views
    private TextView tvOrderQueueCount;
    private TextView tvNoSellerOrders;
    private RecyclerView rvSellerOrders;
    private SellerOrderAdapter sellerOrderAdapter;

    // State & Listeners
    private ListenerRegistration ordersListenerRegistration;
    private WaterOrder pendingScanOrder;

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
        setupRecyclerView();
        setupListeners();
        subscribeToOrdersCollection();
    }

    private void initViews() {
        tvSummaryPendingOrders = findViewById(R.id.tvSummaryPendingOrders);
        tvSummaryActiveDeliveries = findViewById(R.id.tvSummaryActiveDeliveries);
        tvSummaryEmptyCollected = findViewById(R.id.tvSummaryEmptyCollected);
        tvSummaryTotalRevenue = findViewById(R.id.tvSummaryTotalRevenue);
        btnUpdateWaterQuality = findViewById(R.id.btnUpdateWaterQuality);
        ivSellerLogout = findViewById(R.id.ivSellerLogout);

        tvOrderQueueCount = findViewById(R.id.tvOrderQueueCount);
        tvNoSellerOrders = findViewById(R.id.tvNoSellerOrders);
        rvSellerOrders = findViewById(R.id.rvSellerOrders);
    }

    private void setupRecyclerView() {
        rvSellerOrders.setLayoutManager(new LinearLayoutManager(this));
        sellerOrderAdapter = new SellerOrderAdapter(this);
        rvSellerOrders.setAdapter(sellerOrderAdapter);
    }

    private void setupListeners() {
        ivSellerLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
    }

    private void subscribeToOrdersCollection() {
        ordersListenerRegistration = FirebaseManager.getInstance().getOrdersRef()
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                        if (error != null || snapshots == null) {
                            return;
                        }

                        List<WaterOrder> orderList = new ArrayList<>();
                        int pendingCount = 0;
                        int activeDeliveriesCount = 0;
                        int emptyCollectedCount = 0;
                        double totalRevenue = 0.0;

                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            WaterOrder order = doc.toObject(WaterOrder.class);
                            if (order != null) {
                                orderList.add(order);

                                String status = order.getStatus();
                                if (WaterOrder.STATUS_PENDING.equalsIgnoreCase(status)) {
                                    pendingCount++;
                                } else if (WaterOrder.STATUS_ACCEPTED.equalsIgnoreCase(status)
                                        || WaterOrder.STATUS_OUT_FOR_DELIVERY.equalsIgnoreCase(status)) {
                                    activeDeliveriesCount++;
                                } else if (WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(status)) {
                                    emptyCollectedCount += order.getEmptyJarsToReturn();
                                    totalRevenue += order.getTotalPrice();
                                }
                            }
                        }

                        // Update Dashboard Metrics Cards
                        tvSummaryPendingOrders.setText(String.valueOf(pendingCount));
                        tvSummaryActiveDeliveries.setText(String.valueOf(activeDeliveriesCount));
                        tvSummaryEmptyCollected.setText(String.format(Locale.getDefault(), "%d Jars", emptyCollectedCount));
                        tvSummaryTotalRevenue.setText(String.format(Locale.getDefault(), "%.0f BDT", totalRevenue));
                        tvOrderQueueCount.setText(String.format(Locale.getDefault(), "%d Orders", orderList.size()));

                        // Sorting Strategy: Active before Delivered, Urgent (isUrgent == true) at the top, newest first
                        Collections.sort(orderList, new Comparator<WaterOrder>() {
                            @Override
                            public int compare(WaterOrder o1, WaterOrder o2) {
                                boolean o1Delivered = WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(o1.getStatus());
                                boolean o2Delivered = WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(o2.getStatus());

                                if (o1Delivered != o2Delivered) {
                                    return o1Delivered ? 1 : -1;
                                }

                                if (o1.isUrgent() != o2.isUrgent()) {
                                    return o1.isUrgent() ? -1 : 1;
                                }

                                return Long.compare(o2.getTimestamp(), o1.getTimestamp());
                            }
                        });

                        sellerOrderAdapter.setOrders(orderList);

                        if (orderList.isEmpty()) {
                            tvNoSellerOrders.setVisibility(View.VISIBLE);
                            rvSellerOrders.setVisibility(View.GONE);
                        } else {
                            tvNoSellerOrders.setVisibility(View.GONE);
                            rvSellerOrders.setVisibility(View.VISIBLE);
                        }
                    }
                });
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
        FirebaseManager.getInstance().getOrdersRef().document(order.getOrderId())
                .update("status", WaterOrder.STATUS_ACCEPTED)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Toast.makeText(SellerDashboardActivity.this,
                                "Order #" + order.getOrderId() + " accepted for dispatch", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(SellerDashboardActivity.this,
                                "Failed to accept order: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onStartDelivery(final WaterOrder order) {
        // 1. Update order status to OUT_FOR_DELIVERY in Cloud Firestore
        FirebaseManager.getInstance().getOrdersRef().document(order.getOrderId())
                .update("status", WaterOrder.STATUS_OUT_FOR_DELIVERY)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        // 2. Start Background Location Broadcast Service
                        Intent serviceIntent = new Intent(SellerDashboardActivity.this, LocationBroadcastService.class);
                        serviceIntent.putExtra("orderId", order.getOrderId());
                        serviceIntent.putExtra("buyerLat", order.getBuyerLat());
                        serviceIntent.putExtra("buyerLng", order.getBuyerLng());
                        serviceIntent.putExtra("floorNumber", order.getFloorNumber());
                        ContextCompat.startForegroundService(SellerDashboardActivity.this, serviceIntent);

                        // 3. Launch Live Tracking Map
                        launchLiveMap(order);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(SellerDashboardActivity.this,
                                "Could not start delivery: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
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
    public void onScanQr(WaterOrder order) {
        this.pendingScanOrder = order;

        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Handover QR for Order #" + order.getOrderId());
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setBarcodeImageEnabled(false);
        qrScanLauncher.launch(options);
    }

    /**
     * Verifies scanned token and executes atomic transaction on Firestore and Realtime Database.
     */
    private void handleQrScanVerification(String scannedToken) {
        if (pendingScanOrder == null) {
            Toast.makeText(this, "No active order selected for verification", Toast.LENGTH_SHORT).show();
            return;
        }

        final WaterOrder order = pendingScanOrder;
        final String expectedToken = order.getQrToken() != null ? order.getQrToken().trim() : "";

        if (!expectedToken.equalsIgnoreCase(scannedToken.trim())) {
            Toast.makeText(this, "QR Verification Failed! Token mismatch.", Toast.LENGTH_LONG).show();
            return;
        }

        // Atomic Transaction: Update Order Status + Buyer's Bottle Ledger
        final DocumentReference orderRef = FirebaseManager.getInstance().getOrdersRef().document(order.getOrderId());
        final DocumentReference buyerRef = FirebaseManager.getInstance().getUsersRef().document(order.getBuyerId());

        FirebaseManager.getInstance().getFirestore().runTransaction(new Transaction.Function<Void>() {
            @Nullable
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot buyerSnapshot = transaction.get(buyerRef);
                long currentHeld = 0;
                if (buyerSnapshot.exists() && buyerSnapshot.contains("emptyJarsHeld")) {
                    Long val = buyerSnapshot.getLong("emptyJarsHeld");
                    if (val != null) {
                        currentHeld = val;
                    }
                }

                // New empty jars held = previous held + new jars delivered - empty jars returned
                long newHeld = Math.max(0, currentHeld + order.getJarRefillCount() - order.getEmptyJarsToReturn());

                transaction.update(orderRef, "status", WaterOrder.STATUS_DELIVERED);
                transaction.update(buyerRef, "emptyJarsHeld", newHeld);
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // Remove live telemetry node from Realtime Database
                FirebaseManager.getInstance().getTelemetryRef(order.getOrderId()).removeValue();
                Toast.makeText(SellerDashboardActivity.this,
                        "Handover Verified! Order #" + order.getOrderId() + " marked Delivered.", Toast.LENGTH_LONG).show();
                pendingScanOrder = null;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                Toast.makeText(SellerDashboardActivity.this,
                        "Atomic update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ordersListenerRegistration != null) {
            ordersListenerRegistration.remove();
        }
    }
}
