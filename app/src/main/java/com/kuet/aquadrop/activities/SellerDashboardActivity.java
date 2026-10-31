package com.kuet.aquadrop.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
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
import com.kuet.aquadrop.models.WaterOrder;
import com.kuet.aquadrop.models.WaterQuality;
import com.kuet.aquadrop.services.LocationBroadcastService;
import com.kuet.aquadrop.utils.FirebaseManager;

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

    // Dispatch Queue Views
    private TextView tvOrderQueueCount;
    private TextView tvNoSellerOrders;
    private RecyclerView rvSellerOrders;
    private SellerOrderAdapter sellerOrderAdapter;

    // State & Listeners
    private ListenerRegistration ordersListenerRegistration;
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
        setupRecyclerView();
        setupListeners();
        subscribeToOrdersCollection();
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

    @Override
    protected void onResume() {
        super.onResume();
        if (ordersListenerRegistration == null) {
            subscribeToOrdersCollection();
        } else {
            fetchOrdersDirectly(false);
        }
    }

    private void subscribeToOrdersCollection() {
        if (ordersListenerRegistration != null) {
            ordersListenerRegistration.remove();
        }

        ordersListenerRegistration = FirebaseManager.getInstance().getOrdersRef()
                .addSnapshotListener(MetadataChanges.INCLUDE, new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Log.e("SellerDashboard", "Orders snapshot listener error: " + error.getMessage(), error);
                            fetchOrdersDirectly(false);
                            return;
                        }

                        if (snapshots != null) {
                            processOrdersSnapshot(snapshots);
                        }
                    }
                });
    }

    private void fetchOrdersDirectly(final boolean userInitiated) {
        if (userInitiated) {
            Toast.makeText(this, "Fetching live depot orders...", Toast.LENGTH_SHORT).show();
        }

        FirebaseManager.getInstance().getOrdersRef()
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot snapshots) {
                        if (snapshots != null) {
                            processOrdersSnapshot(snapshots);
                            if (userInitiated) {
                                Toast.makeText(SellerDashboardActivity.this,
                                        "Orders synchronized (" + snapshots.size() + " active)", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("SellerDashboard", "Server fetch failed, trying default cache: " + e.getMessage());
                        FirebaseManager.getInstance().getOrdersRef()
                                .get(Source.DEFAULT)
                                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                    @Override
                                    public void onSuccess(QuerySnapshot cachedSnapshots) {
                                        if (cachedSnapshots != null) {
                                            processOrdersSnapshot(cachedSnapshots);
                                        }
                                    }
                                });
                    }
                });
    }

    private void processOrdersSnapshot(QuerySnapshot snapshots) {
        List<WaterOrder> orderList = new ArrayList<>();
        int pendingCount = 0;
        int activeDeliveriesCount = 0;
        int emptyCollectedCount = 0;

        int todayDeliveredCount = 0;
        double todayEarnings = 0.0;
        int lifetimeDeliveredCount = 0;
        double lifetimeRevenue = 0.0;

        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            WaterOrder order = WaterOrder.fromSnapshot(doc);
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

        this.cachedAllOrders = new ArrayList<>(orderList);

        // Sound chime / vibration when a new pending order arrives online
        if (lastKnownPendingCount != -1 && pendingCount > lastKnownPendingCount) {
            notifyNewOrderArrived();
        }
        lastKnownPendingCount = pendingCount;

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
        tvOrderQueueCount.setText(String.format(Locale.getDefault(), "%d Orders", orderList.size()));

        // Sorting Strategy: Active before Delivered, Urgent (isUrgent == true) at the top, newest first
        Collections.sort(orderList, new Comparator<WaterOrder>() {
            @Override
            public int compare(WaterOrder o1, WaterOrder o2) {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return 1;
                if (o2 == null) return -1;

                String s1 = o1.getStatus() != null ? o1.getStatus() : "";
                String s2 = o2.getStatus() != null ? o2.getStatus() : "";

                boolean o1Delivered = WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(s1);
                boolean o2Delivered = WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(s2);

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
        final String sellerUid = FirebaseManager.getInstance().getCurrentUserId();
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", WaterOrder.STATUS_OUT_FOR_DELIVERY);
        if (!TextUtils.isEmpty(sellerUid)) {
            updates.put("sellerId", sellerUid);
        }

        FirebaseManager.getInstance().getOrdersRef().document(order.getOrderId())
                .update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        // Start Background Location Broadcast Service
                        Intent serviceIntent = new Intent(SellerDashboardActivity.this, LocationBroadcastService.class);
                        serviceIntent.putExtra("orderId", order.getOrderId());
                        serviceIntent.putExtra("buyerLat", order.getBuyerLat());
                        serviceIntent.putExtra("buyerLng", order.getBuyerLng());
                        serviceIntent.putExtra("floorNumber", order.getFloorNumber());
                        serviceIntent.putExtra("locationName", order.getLocationName());
                        ContextCompat.startForegroundService(SellerDashboardActivity.this, serviceIntent);

                        Toast.makeText(SellerDashboardActivity.this,
                                "Order #" + order.getOrderId() + " accepted! Driver live GPS tracking broadcast started.", Toast.LENGTH_SHORT).show();

                        // Launch live tracking map for seller
                        launchLiveMap(order);
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
        // Redundant alias for onAcceptOrder (both confirm order and launch driver telemetry)
        onAcceptOrder(order);
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

    private void startScanAnyOrderQr() {
        this.pendingScanOrder = null;

        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Buyer Handover QR to Confirm Delivery");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setBarcodeImageEnabled(false);
        qrScanLauncher.launch(options);
    }

    @Override
    public void onEditOrder(final WaterOrder order) {
        showEditOrderDialog(order);
    }

    @Override
    public void onViewGpsOnMap(final WaterOrder order) {
        launchLiveMap(order);
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

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("jarRefillCount", newRefill);
                    updates.put("emptyJarsToReturn", newEmpty);
                    updates.put("totalPrice", newPrice);

                    FirebaseManager.getInstance().getOrdersRef().document(order.getOrderId())
                            .update(updates)
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    Toast.makeText(SellerDashboardActivity.this,
                                            "Order #" + order.getOrderId() + " updated! Synced to buyer live.",
                                            Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(Exception e) {
                                    Toast.makeText(SellerDashboardActivity.this,
                                            "Failed to update order: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
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

                Map<String, Object> orderUpdates = new HashMap<>();
                orderUpdates.put("status", WaterOrder.STATUS_DELIVERED);
                orderUpdates.put("deliveredTimestamp", System.currentTimeMillis());
                orderUpdates.put("deliveredDate", todayDate);
                if (!TextUtils.isEmpty(sellerUid)) {
                    orderUpdates.put("sellerId", sellerUid);
                }

                transaction.update(orderRef, orderUpdates);
                transaction.update(buyerRef, "emptyJarsHeld", newHeld);
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // Stop location broadcast service and remove RTDB telemetry
                try {
                    Intent stopIntent = new Intent(SellerDashboardActivity.this, LocationBroadcastService.class);
                    stopService(stopIntent);
                } catch (Exception ignored) {}

                FirebaseManager.getInstance().getTelemetryRef(order.getOrderId()).removeValue();

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
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                Toast.makeText(SellerDashboardActivity.this,
                        "Atomic update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
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

        for (WaterOrder order : cachedAllOrders) {
            if (WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(order.getStatus())) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ordersListenerRegistration != null) {
            ordersListenerRegistration.remove();
        }
    }
}
