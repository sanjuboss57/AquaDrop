package com.kuet.aquadrop.activities;

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

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.adapters.DeliveredOrderAdapter;
import com.kuet.aquadrop.models.WaterOrder;
import com.kuet.aquadrop.utils.FirebaseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DeliveredHistoryActivity extends AppCompatActivity {

    private ImageView ivBackDeliveredHistory;
    private ImageView ivRefreshDeliveredHistory;
    private TextView tvDeliveredTotalJars;
    private TextView tvDeliveredTotalRevenue;
    private TextView tvDeliveredTotalOrdersCount;
    private TextView tvNoDeliveredPlaceholder;
    private RecyclerView rvDeliveredOrdersHistory;

    private DeliveredOrderAdapter deliveredOrderAdapter;
    private final Map<String, WaterOrder> deliveredOrdersMap = new HashMap<>();
    private ListenerRegistration firestoreListener;
    private ValueEventListener rtdbListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivered_history);

        initViews();
        setupRecyclerView();
        setupListeners();
        fetchDeliveredOrders();
    }

    private void initViews() {
        ivBackDeliveredHistory = findViewById(R.id.ivBackDeliveredHistory);
        ivRefreshDeliveredHistory = findViewById(R.id.ivRefreshDeliveredHistory);
        tvDeliveredTotalJars = findViewById(R.id.tvDeliveredTotalJars);
        tvDeliveredTotalRevenue = findViewById(R.id.tvDeliveredTotalRevenue);
        tvDeliveredTotalOrdersCount = findViewById(R.id.tvDeliveredTotalOrdersCount);
        tvNoDeliveredPlaceholder = findViewById(R.id.tvNoDeliveredPlaceholder);
        rvDeliveredOrdersHistory = findViewById(R.id.rvDeliveredOrdersHistory);
    }

    private void setupRecyclerView() {
        rvDeliveredOrdersHistory.setLayoutManager(new LinearLayoutManager(this));
        deliveredOrderAdapter = new DeliveredOrderAdapter();
        rvDeliveredOrdersHistory.setAdapter(deliveredOrderAdapter);
    }

    private void setupListeners() {
        ivBackDeliveredHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ivRefreshDeliveredHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchDeliveredOrders();
                Toast.makeText(DeliveredHistoryActivity.this, "Syncing delivered orders archive...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isMyDeliveredOrder(WaterOrder order, String currentSellerUid) {
        if (order == null || !WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(order.getStatus())) {
            return false;
        }
        if (android.text.TextUtils.isEmpty(currentSellerUid)) return true;
        String assigned = order.getAssignedSellerId();
        if (android.text.TextUtils.isEmpty(assigned)) assigned = order.getSellerId();
        return currentSellerUid.equals(assigned);
    }

    private void fetchDeliveredOrders() {
        final String currentSellerUid = FirebaseManager.getInstance().getCurrentUserId();

        // 1. Fetch from Cloud Firestore
        try {
            if (firestoreListener != null) {
                firestoreListener.remove();
            }
            firestoreListener = FirebaseFirestore.getInstance().collection("orders")
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                            if (error != null) {
                                Log.e("DeliveredHistory", "Firestore error: " + error.getMessage());
                                return;
                            }
                            if (snapshots != null) {
                                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                                    WaterOrder order = WaterOrder.fromSnapshot(doc);
                                    if (isMyDeliveredOrder(order, currentSellerUid)) {
                                        deliveredOrdersMap.put(order.getOrderId(), order);
                                    }
                                }
                                updateDeliveredUi();
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e("DeliveredHistory", "Firestore attach error: " + e.getMessage());
        }

        // 2. Fetch from Firebase Realtime Database
        try {
            if (rtdbListener != null) {
                FirebaseManager.getInstance().getRtdbOrdersRef().removeEventListener(rtdbListener);
            }
            rtdbListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            WaterOrder order = WaterOrder.fromDataSnapshot(child);
                            if (isMyDeliveredOrder(order, currentSellerUid)) {
                                deliveredOrdersMap.put(order.getOrderId(), order);
                            }
                        }
                        updateDeliveredUi();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            FirebaseManager.getInstance().getRtdbOrdersRef().addValueEventListener(rtdbListener);
        } catch (Exception ignored) {}

        // 3. Fetch from Dedicated Seller Delivery Storage: seller_storage/{currentSellerUid}/delivered_orders
        if (!android.text.TextUtils.isEmpty(currentSellerUid)) {
            try {
                FirebaseFirestore.getInstance().collection("seller_storage")
                        .document(currentSellerUid)
                        .collection("delivered_orders")
                        .addSnapshotListener(new EventListener<QuerySnapshot>() {
                            @Override
                            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException error) {
                                if (error != null || snapshots == null) return;
                                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                                    WaterOrder order = WaterOrder.fromSnapshot(doc);
                                    if (order != null) {
                                        deliveredOrdersMap.put(order.getOrderId(), order);
                                    }
                                }
                                updateDeliveredUi();
                            }
                        });
            } catch (Exception ignored) {}

            try {
                FirebaseManager.getInstance().getDatabase().getReference("seller_storage")
                        .child(currentSellerUid)
                        .child("delivered_orders")
                        .addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    for (DataSnapshot child : snapshot.getChildren()) {
                                        WaterOrder order = WaterOrder.fromDataSnapshot(child);
                                        if (order != null) {
                                            deliveredOrdersMap.put(order.getOrderId(), order);
                                        }
                                    }
                                    updateDeliveredUi();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
            } catch (Exception ignored) {}
        }
    }

    private void updateDeliveredUi() {
        final String currentSellerUid = FirebaseManager.getInstance().getCurrentUserId();
        List<WaterOrder> list = new ArrayList<>();
        for (WaterOrder o : deliveredOrdersMap.values()) {
            if (isMyDeliveredOrder(o, currentSellerUid)) {
                list.add(o);
            }
        }

        // Sort newest-first (timestamp descending)
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

        int totalJars = 0;
        double totalRevenue = 0.0;
        int totalEmpty = 0;

        for (WaterOrder order : list) {
            totalJars += (order.getJarRefillCount() + order.getNewJarWithDepositCount());
            totalRevenue += order.getTotalPrice();
            totalEmpty += order.getEmptyJarsToReturn();
        }

        tvDeliveredTotalJars.setText(String.format(Locale.getDefault(), "%d Jars", totalJars));
        tvDeliveredTotalRevenue.setText(String.format(Locale.getDefault(), "%.0f BDT", totalRevenue));
        tvDeliveredTotalOrdersCount.setText(String.format(Locale.getDefault(),
                "Total Completed Deliveries: %d Orders • %d Empty Jars Collected",
                list.size(), totalEmpty));

        deliveredOrderAdapter.setDeliveredOrders(list);

        if (list.isEmpty()) {
            tvNoDeliveredPlaceholder.setVisibility(View.VISIBLE);
            rvDeliveredOrdersHistory.setVisibility(View.GONE);
        } else {
            tvNoDeliveredPlaceholder.setVisibility(View.GONE);
            rvDeliveredOrdersHistory.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
        if (rtdbListener != null) {
            FirebaseManager.getInstance().getRtdbOrdersRef().removeEventListener(rtdbListener);
        }
    }
}

