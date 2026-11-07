package com.kuet.aquadrop.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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

public class DeliveredOrderAdapter extends RecyclerView.Adapter<DeliveredOrderAdapter.DeliveredViewHolder> {

    private List<WaterOrder> deliveredOrders = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    public void setDeliveredOrders(List<WaterOrder> orders) {
        this.deliveredOrders = orders != null ? orders : new ArrayList<WaterOrder>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeliveredViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_delivered_order, parent, false);
        return new DeliveredViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeliveredViewHolder holder, int position) {
        holder.bind(deliveredOrders.get(position));
    }

    @Override
    public int getItemCount() {
        return deliveredOrders.size();
    }

    class DeliveredViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvItemOrderId;
        private final TextView tvItemDeliveredDate;
        private final TextView tvItemDeliveredBadge;
        private final TextView tvItemBuyerName;
        private final TextView tvItemBuyerPhone;
        private final MaterialButton btnItemCallBuyer;
        private final TextView tvItemDeliveryAddress;
        private final TextView tvItemLocationNotes;
        private final TextView tvItemJarDetails;
        private final TextView tvItemPaymentSummary;

        public DeliveredViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemOrderId = itemView.findViewById(R.id.tvItemOrderId);
            tvItemDeliveredDate = itemView.findViewById(R.id.tvItemDeliveredDate);
            tvItemDeliveredBadge = itemView.findViewById(R.id.tvItemDeliveredBadge);
            tvItemBuyerName = itemView.findViewById(R.id.tvItemBuyerName);
            tvItemBuyerPhone = itemView.findViewById(R.id.tvItemBuyerPhone);
            btnItemCallBuyer = itemView.findViewById(R.id.btnItemCallBuyer);
            tvItemDeliveryAddress = itemView.findViewById(R.id.tvItemDeliveryAddress);
            tvItemLocationNotes = itemView.findViewById(R.id.tvItemLocationNotes);
            tvItemJarDetails = itemView.findViewById(R.id.tvItemJarDetails);
            tvItemPaymentSummary = itemView.findViewById(R.id.tvItemPaymentSummary);
        }

        public void bind(final WaterOrder order) {
            tvItemOrderId.setText(order.getOrderId());

            long ts = order.getDeliveredTimestamp() > 0 ? order.getDeliveredTimestamp() : order.getTimestamp();
            tvItemDeliveredDate.setText("Delivered: " + dateFormat.format(new Date(ts)));

            tvItemBuyerName.setText(String.format("Buyer: %s", !TextUtils.isEmpty(order.getBuyerName()) ? order.getBuyerName() : "Student"));
            tvItemBuyerPhone.setText(!TextUtils.isEmpty(order.getBuyerPhone()) ? order.getBuyerPhone() : "N/A");

            btnItemCallBuyer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!TextUtils.isEmpty(order.getBuyerPhone())) {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + order.getBuyerPhone()));
                        v.getContext().startActivity(callIntent);
                    } else {
                        Toast.makeText(v.getContext(), "Buyer phone number not available", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            tvItemDeliveryAddress.setText(String.format(Locale.getDefault(),
                    "%s • Room %s (Fl %d)",
                    !TextUtils.isEmpty(order.getLocationName()) ? order.getLocationName() : "KUET",
                    !TextUtils.isEmpty(order.getRoomOrFlatNo()) ? order.getRoomOrFlatNo() : "Room",
                    order.getFloorNumber()));

            if (!TextUtils.isEmpty(order.getLocationNotes())) {
                tvItemLocationNotes.setVisibility(View.VISIBLE);
                tvItemLocationNotes.setText("📍 Directions: " + order.getLocationNotes());
            } else {
                tvItemLocationNotes.setVisibility(View.GONE);
            }

            int deliveredJars = order.getJarRefillCount() + order.getNewJarWithDepositCount();
            int emptyCollected = order.getEmptyJarsToReturn();
            tvItemJarDetails.setText(String.format(Locale.getDefault(),
                    "%d x 20L Jars (%d Empty Collected)", deliveredJars, emptyCollected));

            boolean isBkash = WaterOrder.PAYMENT_BKASH.equalsIgnoreCase(order.getPaymentMethod());
            tvItemPaymentSummary.setText(String.format(Locale.getDefault(),
                    "%.0f BDT (%s Paid)", order.getTotalPrice(), isBkash ? "bKash" : "Cash"));
        }
    }
}
