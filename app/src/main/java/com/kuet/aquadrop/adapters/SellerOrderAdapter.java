package com.kuet.aquadrop.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.WaterOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SellerOrderAdapter extends RecyclerView.Adapter<SellerOrderAdapter.SellerOrderViewHolder> {

    public interface OnSellerOrderActionListener {
        void onCallBuyer(WaterOrder order);
        void onAcceptOrder(WaterOrder order);
        void onStartDelivery(WaterOrder order);
        void onOpenLiveMap(WaterOrder order);
        void onScanQr(WaterOrder order);
        void onEditOrder(WaterOrder order);
        void onViewGpsOnMap(WaterOrder order);
    }

    private List<WaterOrder> orders = new ArrayList<>();
    private final OnSellerOrderActionListener listener;

    public SellerOrderAdapter(OnSellerOrderActionListener listener) {
        this.listener = listener;
    }

    public void setOrders(List<WaterOrder> orders) {
        this.orders = orders != null ? orders : new ArrayList<WaterOrder>();
        notifyDataSetChanged();
    }

    public List<WaterOrder> getOrders() {
        return orders;
    }

    @NonNull
    @Override
    public SellerOrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_seller_order, parent, false);
        return new SellerOrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SellerOrderViewHolder holder, int position) {
        holder.bind(orders.get(position));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    class SellerOrderViewHolder extends RecyclerView.ViewHolder {

        private final LinearLayout llEmergencyAlertHeader;
        private final TextView tvSellerOrderId;
        private final TextView tvUrgentBadge;
        private final TextView tvSellerOrderStatus;
        private final TextView tvBuyerName;
        private final TextView tvBuyerPhone;
        private final MaterialButton btnCallBuyer;
        private final TextView tvDeliveryAddress;
        private final LinearLayout llLocationNotes;
        private final TextView tvLocationNotes;
        private final TextView tvBuyerGpsCoordinates;
        private final MaterialButton btnViewGpsOnMap;
        private final LinearLayout llSellerGpsRow;
        private final TextView tvJarsToDeliver;
        private final TextView tvJarsToCollect;
        private final TextView tvPaymentSummary;
        private final TextView tvBkashTrxDetails;
        private final MaterialButton btnEditOrder;

        private final MaterialButton btnAcceptOrder;
        private final MaterialButton btnStartDelivery;
        private final LinearLayout llOutForDeliveryActions;
        private final MaterialButton btnOpenLiveMap;
        private final MaterialButton btnScanBuyerQr;
        private final TextView tvDeliveredConfirmation;

        public SellerOrderViewHolder(@NonNull View itemView) {
            super(itemView);
            llEmergencyAlertHeader = itemView.findViewById(R.id.llEmergencyAlertHeader);
            tvSellerOrderId = itemView.findViewById(R.id.tvSellerOrderId);
            tvUrgentBadge = itemView.findViewById(R.id.tvUrgentBadge);
            tvSellerOrderStatus = itemView.findViewById(R.id.tvSellerOrderStatus);
            tvBuyerName = itemView.findViewById(R.id.tvBuyerName);
            tvBuyerPhone = itemView.findViewById(R.id.tvBuyerPhone);
            btnCallBuyer = itemView.findViewById(R.id.btnCallBuyer);
            tvDeliveryAddress = itemView.findViewById(R.id.tvDeliveryAddress);
            llLocationNotes = itemView.findViewById(R.id.llLocationNotes);
            tvLocationNotes = itemView.findViewById(R.id.tvLocationNotes);
            tvBuyerGpsCoordinates = itemView.findViewById(R.id.tvBuyerGpsCoordinates);
            btnViewGpsOnMap = itemView.findViewById(R.id.btnViewGpsOnMap);
            llSellerGpsRow = itemView.findViewById(R.id.llSellerGpsRow);
            tvJarsToDeliver = itemView.findViewById(R.id.tvJarsToDeliver);
            tvJarsToCollect = itemView.findViewById(R.id.tvJarsToCollect);
            tvPaymentSummary = itemView.findViewById(R.id.tvPaymentSummary);
            tvBkashTrxDetails = itemView.findViewById(R.id.tvBkashTrxDetails);
            btnEditOrder = itemView.findViewById(R.id.btnEditOrder);

            btnAcceptOrder = itemView.findViewById(R.id.btnAcceptOrder);
            btnStartDelivery = itemView.findViewById(R.id.btnStartDelivery);
            llOutForDeliveryActions = itemView.findViewById(R.id.llOutForDeliveryActions);
            btnOpenLiveMap = itemView.findViewById(R.id.btnOpenLiveMap);
            btnScanBuyerQr = itemView.findViewById(R.id.btnScanBuyerQr);
            tvDeliveredConfirmation = itemView.findViewById(R.id.tvDeliveredConfirmation);
        }

        public void bind(final WaterOrder order) {
            tvSellerOrderId.setText(order.getOrderId());

            // High Priority Emergency Alert Header & Card Border Styling
            if (order.isUrgent()) {
                if (llEmergencyAlertHeader != null) llEmergencyAlertHeader.setVisibility(View.VISIBLE);
                tvUrgentBadge.setVisibility(View.VISIBLE);
                tvUrgentBadge.setText("🚨 EMERGENCY");
                tvUrgentBadge.setTextColor(Color.parseColor("#C62828"));
                tvUrgentBadge.setBackgroundColor(Color.parseColor("#FFCDD2"));
                if (itemView instanceof com.google.android.material.card.MaterialCardView) {
                    ((com.google.android.material.card.MaterialCardView) itemView).setStrokeColor(Color.parseColor("#D32F2F"));
                    ((com.google.android.material.card.MaterialCardView) itemView).setStrokeWidth(4);
                }
            } else {
                if (llEmergencyAlertHeader != null) llEmergencyAlertHeader.setVisibility(View.GONE);
                tvUrgentBadge.setVisibility(View.GONE);
                if (itemView instanceof com.google.android.material.card.MaterialCardView) {
                    ((com.google.android.material.card.MaterialCardView) itemView).setStrokeColor(Color.parseColor("#E0E0E0"));
                    ((com.google.android.material.card.MaterialCardView) itemView).setStrokeWidth(2);
                }
            }

            // Buyer Contact
            tvBuyerName.setText(String.format("Buyer: %s", order.getBuyerName()));
            tvBuyerPhone.setText(order.getBuyerPhone());

            // Delivery Location
            tvDeliveryAddress.setText(String.format(Locale.getDefault(),
                    "%s • Room %s (Floor %d)",
                    order.getLocationName(),
                    order.getRoomOrFlatNo(),
                    order.getFloorNumber()));

            // Optional Exact Location Notes / Directions
            if (llLocationNotes != null && tvLocationNotes != null) {
                if (!TextUtils.isEmpty(order.getLocationNotes())) {
                    llLocationNotes.setVisibility(View.VISIBLE);
                    tvLocationNotes.setText("📍 Exact Directions: " + order.getLocationNotes());
                } else {
                    llLocationNotes.setVisibility(View.GONE);
                }
            }

            // Buyer Live GPS Coordinates
            if (order.getBuyerLat() != 0.0 && order.getBuyerLng() != 0.0) {
                tvBuyerGpsCoordinates.setText(String.format(Locale.getDefault(),
                        "GPS: %.5f° N, %.5f° E", order.getBuyerLat(), order.getBuyerLng()));
            } else {
                tvBuyerGpsCoordinates.setText(String.format("Location: %s", order.getLocationName()));
            }

            View.OnClickListener gpsMapListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onViewGpsOnMap(order);
                }
            };
            btnViewGpsOnMap.setOnClickListener(gpsMapListener);
            llSellerGpsRow.setOnClickListener(gpsMapListener);

            // Jars & Amounts
            tvJarsToDeliver.setText(String.format(Locale.getDefault(), "%d x 20L", order.getJarRefillCount()));
            tvJarsToCollect.setText(String.format(Locale.getDefault(), "%d Jar(s)", order.getEmptyJarsToReturn()));

            boolean isBkash = WaterOrder.PAYMENT_BKASH.equalsIgnoreCase(order.getPaymentMethod());
            tvPaymentSummary.setText(String.format(Locale.getDefault(), "%.0f BDT (%s)",
                    order.getTotalPrice(), isBkash ? "bKash" : "Cash"));

            if (isBkash && !TextUtils.isEmpty(order.getBkashTrxId())) {
                tvBkashTrxDetails.setVisibility(View.VISIBLE);
                tvBkashTrxDetails.setText(String.format("bKash TrxID: %s", order.getBkashTrxId()));
            } else {
                tvBkashTrxDetails.setVisibility(View.GONE);
            }

            // Status Badge
            String status = order.getStatus() != null ? order.getStatus() : WaterOrder.STATUS_PENDING;
            tvSellerOrderStatus.setText(status.replace('_', ' '));
            applyStatusBadge(status);

            // Dynamic Action Button Visibility
            btnAcceptOrder.setVisibility(View.GONE);
            btnStartDelivery.setVisibility(View.GONE);
            llOutForDeliveryActions.setVisibility(View.GONE);
            tvDeliveredConfirmation.setVisibility(View.GONE);

            switch (status) {
                case WaterOrder.STATUS_CANCELLED:
                    tvDeliveredConfirmation.setVisibility(View.VISIBLE);
                    tvDeliveredConfirmation.setText("✕ Cancelled by Buyer (Void)");
                    tvDeliveredConfirmation.setTextColor(Color.parseColor("#C62828"));
                    break;
                case WaterOrder.STATUS_PENDING:
                    btnAcceptOrder.setVisibility(View.VISIBLE);
                    break;
                case WaterOrder.STATUS_ACCEPTED:
                    btnStartDelivery.setVisibility(View.VISIBLE);
                    break;
                case WaterOrder.STATUS_OUT_FOR_DELIVERY:
                    llOutForDeliveryActions.setVisibility(View.VISIBLE);
                    break;
                case WaterOrder.STATUS_DELIVERED:
                    tvDeliveredConfirmation.setVisibility(View.VISIBLE);
                    tvDeliveredConfirmation.setText(String.format(Locale.getDefault(),
                            "✓ Delivered & Cash Collected: %.0f BDT", order.getTotalPrice()));
                    tvDeliveredConfirmation.setTextColor(Color.parseColor("#2E7D32"));
                    break;
            }

            // Allow adjusting jar quantities & total price for any active orders
            boolean canEdit = !WaterOrder.STATUS_DELIVERED.equalsIgnoreCase(status)
                    && !WaterOrder.STATUS_CANCELLED.equalsIgnoreCase(status);
            btnEditOrder.setVisibility(canEdit ? View.VISIBLE : View.GONE);
            btnEditOrder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onEditOrder(order);
                }
            });

            // Click Handlers
            btnCallBuyer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onCallBuyer(order);
                }
            });

            btnAcceptOrder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onAcceptOrder(order);
                }
            });

            btnStartDelivery.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onStartDelivery(order);
                }
            });

            btnOpenLiveMap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onOpenLiveMap(order);
                }
            });

            btnScanBuyerQr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onScanQr(order);
                }
            });
        }

        private void applyStatusBadge(String status) {
            int bgColor;
            int textColor;

            switch (status) {
                case WaterOrder.STATUS_CANCELLED:
                    bgColor = Color.parseColor("#FFEBEE");
                    textColor = Color.parseColor("#C62828");
                    break;
                case WaterOrder.STATUS_OUT_FOR_DELIVERY:
                    bgColor = Color.parseColor("#E0F7FA");
                    textColor = Color.parseColor("#00838F");
                    break;
                case WaterOrder.STATUS_ACCEPTED:
                    bgColor = Color.parseColor("#E3F2FD");
                    textColor = Color.parseColor("#1565C0");
                    break;
                case WaterOrder.STATUS_DELIVERED:
                    bgColor = Color.parseColor("#E8F5E9");
                    textColor = Color.parseColor("#2E7D32");
                    break;
                case WaterOrder.STATUS_PENDING:
                default:
                    bgColor = Color.parseColor("#FFF3E0");
                    textColor = Color.parseColor("#E65100");
                    break;
            }

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(20f);
            drawable.setColor(bgColor);
            tvSellerOrderStatus.setBackground(drawable);
            tvSellerOrderStatus.setTextColor(textColor);
        }
    }
}

