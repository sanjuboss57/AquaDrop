package com.kuet.aquadrop.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.models.WaterOrder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BuyerOrderAdapter extends RecyclerView.Adapter<BuyerOrderAdapter.OrderViewHolder> {

    public interface OnOrderActionListener {
        void onTrackLiveClicked(WaterOrder order);
        void onShowQrClicked(WaterOrder order);
    }

    private List<WaterOrder> orderList = new ArrayList<>();
    private final OnOrderActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    public BuyerOrderAdapter(OnOrderActionListener listener) {
        this.listener = listener;
    }

    public void setOrders(List<WaterOrder> orders) {
        this.orderList = orders != null ? orders : new ArrayList<WaterOrder>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_buyer_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        final WaterOrder order = orderList.get(position);
        holder.bind(order);
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    class OrderViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvItemOrderId;
        private final TextView tvItemOrderDate;
        private final TextView tvItemOrderStatus;
        private final TextView tvItemJarDetails;
        private final TextView tvItemEta;
        private final TextView tvItemTotalPrice;
        private final MaterialButton btnTrackOrder;
        private final MaterialButton btnShowQr;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemOrderId = itemView.findViewById(R.id.tvItemOrderId);
            tvItemOrderDate = itemView.findViewById(R.id.tvItemOrderDate);
            tvItemOrderStatus = itemView.findViewById(R.id.tvItemOrderStatus);
            tvItemJarDetails = itemView.findViewById(R.id.tvItemJarDetails);
            tvItemEta = itemView.findViewById(R.id.tvItemEta);
            tvItemTotalPrice = itemView.findViewById(R.id.tvItemTotalPrice);
            btnTrackOrder = itemView.findViewById(R.id.btnTrackOrder);
            btnShowQr = itemView.findViewById(R.id.btnShowQr);
        }

        public void bind(final WaterOrder order) {
            tvItemOrderId.setText(order.getOrderId());
            tvItemOrderDate.setText(dateFormat.format(new Date(order.getTimestamp())));

            String jarInfo = String.format(Locale.getDefault(), "%d x 20L Refill (%d Returning)",
                    order.getJarRefillCount(), order.getEmptyJarsToReturn());
            tvItemJarDetails.setText(jarInfo);

            tvItemTotalPrice.setText(String.format(Locale.getDefault(), "%.0f BDT", order.getTotalPrice()));

            // Status Badge & ETA Styling
            String status = order.getStatus() != null ? order.getStatus() : WaterOrder.STATUS_PENDING;
            tvItemOrderStatus.setText(status.replace('_', ' '));

            applyStatusStyle(status, order);

            // "Track Live on Map" only visible when OUT_FOR_DELIVERY
            boolean isOutForDelivery = WaterOrder.STATUS_OUT_FOR_DELIVERY.equalsIgnoreCase(status);
            btnTrackOrder.setVisibility(isOutForDelivery ? View.VISIBLE : View.GONE);

            btnTrackOrder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onTrackLiveClicked(order);
                    }
                }
            });

            btnShowQr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onShowQrClicked(order);
                    }
                }
            });
        }

        private void applyStatusStyle(String status, WaterOrder order) {
            int bgColor;
            int textColor;
            String etaText;

            switch (status) {
                case WaterOrder.STATUS_OUT_FOR_DELIVERY:
                    bgColor = Color.parseColor("#E0F7FA");
                    textColor = Color.parseColor("#00838F");
                    etaText = "Driver en route to " + order.getLocationName();
                    break;
                case WaterOrder.STATUS_ACCEPTED:
                    bgColor = Color.parseColor("#E3F2FD");
                    textColor = Color.parseColor("#1565C0");
                    etaText = "Depot accepted order — Packing jars";
                    break;
                case WaterOrder.STATUS_DELIVERED:
                    bgColor = Color.parseColor("#E8F5E9");
                    textColor = Color.parseColor("#2E7D32");
                    etaText = "Delivered & verified at doorstep";
                    break;
                case WaterOrder.STATUS_PENDING:
                default:
                    bgColor = Color.parseColor("#FFF3E0");
                    textColor = Color.parseColor("#E65100");
                    etaText = "Order placed — Waiting for depot confirmation";
                    break;
            }

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(24f);
            drawable.setColor(bgColor);
            tvItemOrderStatus.setBackground(drawable);
            tvItemOrderStatus.setTextColor(textColor);

            tvItemEta.setText(etaText);
            tvItemEta.setTextColor(textColor);
        }
    }
}

