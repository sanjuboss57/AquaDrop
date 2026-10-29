package com.kuet.aquadrop.activities;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.adapters.BuyerOrderAdapter;
import com.kuet.aquadrop.models.User;
import com.kuet.aquadrop.models.WaterOrder;
import com.kuet.aquadrop.models.WaterQuality;
import com.kuet.aquadrop.utils.FirebaseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BuyerDashboardActivity extends AppCompatActivity implements BuyerOrderAdapter.OnOrderActionListener {

    private static final int PRICE_REFILL_JAR = 45;
    private static final int SECURITY_DEPOSIT_PER_JAR = 200;
    private static final int EXPRESS_DELIVERY_SURCHARGE = 15;

    // Header Views
    private TextView tvBuyerGreeting;
    private TextView tvBuyerLocationHeader;
    private ImageView ivLogout;

    // Water Quality Views
    private TextView tvTdsLevel;
    private TextView tvPhLevel;
    private TextView tvBatchStatus;

    // Bottle Ledger Views
    private TextView tvEmptyJarsHeld;
    private TextView tvDepositBalance;
    private MaterialButton btnOpenSplitter;

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

    // Firestore Registrations
    private ListenerRegistration userListenerRegistration;
    private ListenerRegistration ordersListenerRegistration;
    private ListenerRegistration qualityListenerRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_dashboard);

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
        ivLogout = findViewById(R.id.ivLogout);

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

        // Auto-deposit rule: If Refill Jars > Returning Jars, add 200 BDT security deposit per extra jar
        int extraJars = Math.max(0, refillJarCount - returnJarCount);
        int depositCost = extraJars * SECURITY_DEPOSIT_PER_JAR;

        if (depositCost > 0) {
            tvDepositNotice.setVisibility(View.VISIBLE);
            tvDepositNotice.setText(String.format(Locale.getDefault(),
                    "Notice: %d extra jar deposit added (+%d BDT refundable)", extraJars, depositCost));
        } else {
            tvDepositNotice.setVisibility(View.GONE);
        }

        int expressSurcharge = cbExpressDelivery.isChecked() ? EXPRESS_DELIVERY_SURCHARGE : 0;
        calculatedTotal = baseCost + depositCost + expressSurcharge;

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
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException error) {
                        if (error != null || queryDocumentSnapshots == null) {
                            return;
                        }

                        List<WaterOrder> orders = new ArrayList<>();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            WaterOrder order = doc.toObject(WaterOrder.class);
                            if (order != null) {
                                orders.add(order);
                            }
                        }

                        // In-memory timestamp sorting (avoids composite index requirements)
                        Collections.sort(orders, new Comparator<WaterOrder>() {
                            @Override
                            public int compare(WaterOrder o1, WaterOrder o2) {
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
        final int extraDepositJars = Math.max(0, refillJarCount - returnJarCount);

        final WaterOrder newOrder = new WaterOrder(
                orderId,
                currentUser.getUid(),
                currentUser.getName(),
                currentUser.getPhone(),
                currentUser.getLocationName(),
                currentUser.getRoomOrFlatNo(),
                currentUser.getFloorNumber(),
                currentUser.getLatitude(),
                currentUser.getLongitude(),
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

                        // Immediately present handover QR dialog to buyer
                        showHandoverQrDialog(newOrder);
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
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_qr_code);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView ivQr = dialog.findViewById(R.id.ivQrCode);
        TextView tvToken = dialog.findViewById(R.id.tvDialogTokenValue);
        MaterialButton btnClose = dialog.findViewById(R.id.btnDialogCloseQr);

        tvToken.setText(order.getQrToken());

        // ZXing 500x500 QR Code Generation
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(order.getQrToken(), BarcodeFormat.QR_CODE, 500, 500);
            ivQr.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not render QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
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
