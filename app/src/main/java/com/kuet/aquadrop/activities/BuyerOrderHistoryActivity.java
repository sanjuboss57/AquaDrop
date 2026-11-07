package com.kuet.aquadrop.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.adapters.BuyerOrderAdapter;
import com.kuet.aquadrop.models.WaterOrder;
import com.kuet.aquadrop.utils.FirebaseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated Past Orders & Delivery History Activity for Buyers.
 * Displays total completed deliveries, jars refilled, and total amount spent,
 * alongside a chronological archive of completed water orders.
 */
public class BuyerOrderHistoryActivity extends AppCompatActivity implements BuyerOrderAdapter.OnOrderActionListener {

    private ImageView ivBackHistory;
    private ImageView ivRefreshHistory;

    // Top Summary Views
    private TextView tvHistoryCompletedCount;
    private TextView tvHistoryTotalJars;
    private TextView tvHistoryTotalSpent;

    // List Views
    private TextView tvNoHistoryPlaceholder;
    private RecyclerView rvBuyerOrderHistory;
    private BuyerOrderAdapter historyAdapter;

    private final Map<String, WaterOrder> deliveredOrdersMap = new HashMap<>();
    private ValueEventListener rtdbOrdersListener;
    private ListenerRegistration firestoreOrdersListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buyer_order_history);

        initViews();
        setupRecyclerView();
        setupListeners();
        subscribeToDeliveredOrders();
    }

    private void initViews() {
        ivBackHistory = findViewById(R.id.ivBackHistory);
        ivRefreshHistory = findViewById(R.id.ivRefreshHistory);

        tvHistoryCompletedCount = findViewById(R.id.tvHistoryCompletedCount);
        tvHistoryTotalJars = findViewById(R.id.tvHistoryTotalJars);
        tvHistoryTotalSpent = findViewById(R.id.tvHistoryTotalSpent);

        tvNoHistoryPlaceholder = findViewById(R.id.tvNoHistoryPlaceholder);
        rvBuyerOrderHistory = findViewById(R.id.rvBuyerOrderHistory);
    }

    private void setupRecyclerView() {
        rvBuyerOrderHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new BuyerOrderAdapter(this);
        rvBuyerOrderHistory.setAdapter(historyAdapter);
    }

    private void setupListeners() {
        ivBackHistory.setOnClickListener(v -> finish());

        ivRefreshHistory.setOnClickListener(v -> {
            Toast.makeText(BuyerOrderHistoryActivity.this, "Refreshing delivery history...", Toast.LENGTH_SHORT).show();
            fetchDeliveredOrdersDirectly();
        });
    }

    private void subscribeToDeliveredOrders() {
        FirebaseUser user = FirebaseManager.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        final String currentBuyerUid = user.getUid();

        // 1. Realtime Database Orders Listener (Primary)
        rtdbOrdersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        WaterOrder order = WaterOrder.fromDataSnapshot(child);
                        if (order != null && currentBuyerUid.equals(order.getBuyerId())
                                && WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(order.getStatus())) {
                            deliveredOrdersMap.put(order.getOrderId(), order);
                        }
                    }
                    updateDeliveredHistoryUi();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w("BuyerHistory", "RTDB orders notice: " + error.getMessage());
            }
        };
        FirebaseManager.getInstance().getRtdbOrdersRef().addValueEventListener(rtdbOrdersListener);

        // 2. Cloud Firestore Listener (Background Mirror)
        try {
            firestoreOrdersListener = FirebaseManager.getInstance().getOrdersRef()
                    .whereEqualTo("buyerId", currentBuyerUid)
                    .whereEqualTo("status", WaterOrder.STATUS_DELIVERED)
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                            if (error != null || snapshots == null) return;
                            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                                WaterOrder order = WaterOrder.fromSnapshot(doc);
                                if (order != null && WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(order.getStatus())) {
                                    deliveredOrdersMap.put(order.getOrderId(), order);
                                }
                            }
                            updateDeliveredHistoryUi();
                        }
                    });
        } catch (Exception ignored) {}
    }

    private void fetchDeliveredOrdersDirectly() {
        FirebaseUser user = FirebaseManager.getInstance().getCurrentUser();
        if (user == null) return;
        final String currentBuyerUid = user.getUid();

        FirebaseManager.getInstance().getRtdbOrdersRef().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        WaterOrder order = WaterOrder.fromDataSnapshot(child);
                        if (order != null && currentBuyerUid.equals(order.getBuyerId())
                                && WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(order.getStatus())) {
                            deliveredOrdersMap.put(order.getOrderId(), order);
                        }
                    }
                    updateDeliveredHistoryUi();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateDeliveredHistoryUi() {
        List<WaterOrder> list = new ArrayList<>(deliveredOrdersMap.values());

        // Sort newest-delivered first
        Collections.sort(list, new Comparator<WaterOrder>() {
            @Override
            public int compare(WaterOrder o1, WaterOrder o2) {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return 1;
                if (o2 == null) return -1;
                long t1 = o1.getDeliveredTimestamp() > 0 ? o1.getDeliveredTimestamp() : o1.getTimestamp();
                long t2 = o2.getDeliveredTimestamp() > 0 ? o2.getDeliveredTimestamp() : o2.getTimestamp();
                return Long.compare(t2, t1);
            }
        });

        int totalDeliveries = list.size();
        int totalJars = 0;
        double totalSpent = 0.0;

        for (WaterOrder order : list) {
            totalJars += order.getJarRefillCount();
            totalSpent += order.getTotalPrice();
        }

        tvHistoryCompletedCount.setText(String.format(Locale.getDefault(), "%d Order%s",
                totalDeliveries, totalDeliveries != 1 ? "s" : ""));
        tvHistoryTotalJars.setText(String.format(Locale.getDefault(), "%d Jars", totalJars));
        tvHistoryTotalSpent.setText(String.format(Locale.getDefault(), "%.0f BDT", totalSpent));

        if (list.isEmpty()) {
            tvNoHistoryPlaceholder.setVisibility(View.VISIBLE);
            rvBuyerOrderHistory.setVisibility(View.GONE);
        } else {
            tvNoHistoryPlaceholder.setVisibility(View.GONE);
            rvBuyerOrderHistory.setVisibility(View.VISIBLE);
        }

        if (historyAdapter != null) {
            historyAdapter.setOrders(list);
        }
    }

    @Override
    public void onTrackLiveClicked(WaterOrder order) {
        // Not active for past delivered orders
    }

    @Override
    public void onShowQrClicked(WaterOrder order) {
        // Handover completed
    }

    @Override
    public void onCancelOrderClicked(WaterOrder order) {
        // Cannot cancel delivered orders
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rtdbOrdersListener != null) {
            FirebaseManager.getInstance().getRtdbOrdersRef().removeEventListener(rtdbOrdersListener);
        }
        if (firestoreOrdersListener != null) {
            firestoreOrdersListener.remove();
        }
    }
}

