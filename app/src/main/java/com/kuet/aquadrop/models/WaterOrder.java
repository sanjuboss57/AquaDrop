package com.kuet.aquadrop.models;

import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;

/**
 * Model representing a drinking water jar order in AquaDrop.
 */
public class WaterOrder implements Serializable {

    // Status Constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // Payment Methods
    public static final String PAYMENT_CASH = "CASH";
    public static final String PAYMENT_BKASH = "BKASH";

    private String orderId;
    private String buyerId;
    private String buyerName;
    private String buyerPhone;
    private String locationName;
    private String roomOrFlatNo;
    private int floorNumber;
    private double buyerLat;
    private double buyerLng;
    private int jarRefillCount;
    private int newJarWithDepositCount;
    private int emptyJarsToReturn;
    private double totalPrice;
    private String paymentMethod; // "CASH" or "BKASH"
    private String bkashTrxId;
    private boolean isUrgent;
    private String status; // PENDING, ACCEPTED, OUT_FOR_DELIVERY, DELIVERED
    private long timestamp;
    private String qrToken;
    private String sellerId;
    private long deliveredTimestamp;
    private String deliveredDate;

    // Required empty constructor for Firestore serialization
    public WaterOrder() {
    }

    public WaterOrder(String orderId, String buyerId, String buyerName, String buyerPhone,
                      String locationName, String roomOrFlatNo, int floorNumber,
                      double buyerLat, double buyerLng, int jarRefillCount,
                      int newJarWithDepositCount, int emptyJarsToReturn, double totalPrice,
                      String paymentMethod, String bkashTrxId, boolean isUrgent,
                      String status, long timestamp, String qrToken) {
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.buyerPhone = buyerPhone;
        this.locationName = locationName;
        this.roomOrFlatNo = roomOrFlatNo;
        this.floorNumber = floorNumber;
        this.buyerLat = buyerLat;
        this.buyerLng = buyerLng;
        this.jarRefillCount = jarRefillCount;
        this.newJarWithDepositCount = newJarWithDepositCount;
        this.emptyJarsToReturn = emptyJarsToReturn;
        this.totalPrice = totalPrice;
        this.paymentMethod = paymentMethod;
        this.bkashTrxId = bkashTrxId;
        this.isUrgent = isUrgent;
        this.status = status;
        this.timestamp = timestamp;
        this.qrToken = qrToken;
    }

    /**
     * Resilient deserializer that handles standard POJO conversion as well as
     * manual fallback to prevent runtime crashes caused by Firestore type variations.
     */
    public static WaterOrder fromSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        try {
            WaterOrder order = doc.toObject(WaterOrder.class);
            if (order != null) {
                if (TextUtils.isEmpty(order.getOrderId())) {
                    order.setOrderId(doc.getId());
                }
                return order;
            }
        } catch (Throwable t) {
            Log.w("WaterOrder", "Standard POJO conversion failed for doc " + doc.getId() + ": " + t.getMessage());
        }

        // Resilient fallback manual mapping
        try {
            WaterOrder order = new WaterOrder();
            order.setOrderId(safeString(doc, "orderId", doc.getId()));
            order.setBuyerId(safeString(doc, "buyerId", ""));
            order.setBuyerName(safeString(doc, "buyerName", "AquaDrop Customer"));
            order.setBuyerPhone(safeString(doc, "buyerPhone", ""));
            order.setLocationName(safeString(doc, "locationName", "Campus Location"));
            order.setRoomOrFlatNo(safeString(doc, "roomOrFlatNo", ""));
            order.setFloorNumber(safeInt(doc, "floorNumber", 1));

            order.setBuyerLat(safeDouble(doc, "buyerLat", 0.0));
            order.setBuyerLng(safeDouble(doc, "buyerLng", 0.0));

            order.setJarRefillCount(safeInt(doc, "jarRefillCount", 1));
            order.setNewJarWithDepositCount(safeInt(doc, "newJarWithDepositCount", 0));
            order.setEmptyJarsToReturn(safeInt(doc, "emptyJarsToReturn", 0));
            order.setTotalPrice(safeDouble(doc, "totalPrice", 0.0));

            order.setPaymentMethod(safeString(doc, "paymentMethod", PAYMENT_CASH));
            order.setBkashTrxId(safeString(doc, "bkashTrxId", ""));

            boolean urgent = safeBoolean(doc, "urgent", false);
            if (!urgent) urgent = safeBoolean(doc, "isUrgent", false);
            order.setUrgent(urgent);

            order.setStatus(safeString(doc, "status", STATUS_PENDING));
            order.setTimestamp(safeLong(doc, "timestamp", System.currentTimeMillis()));
            order.setQrToken(safeString(doc, "qrToken", ""));
            order.setSellerId(safeString(doc, "sellerId", ""));
            order.setDeliveredDate(safeString(doc, "deliveredDate", ""));
            order.setDeliveredTimestamp(safeLong(doc, "deliveredTimestamp", 0L));

            return order;
        } catch (Throwable t) {
            Log.e("WaterOrder", "Manual deserialization error for doc " + doc.getId(), t);
            // Ultra-fallback minimal order rather than null
            WaterOrder minimal = new WaterOrder();
            minimal.setOrderId(doc.getId());
            minimal.setStatus(STATUS_PENDING);
            return minimal;
        }
    }

    private static String safeString(DocumentSnapshot doc, String field, String fallback) {
        if (doc == null || !doc.contains(field)) return fallback;
        Object val = doc.get(field);
        return val != null ? String.valueOf(val) : fallback;
    }

    private static int safeInt(DocumentSnapshot doc, String field, int fallback) {
        if (doc == null || !doc.contains(field)) return fallback;
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt(((String) val).trim()); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static double safeDouble(DocumentSnapshot doc, String field, double fallback) {
        if (doc == null || !doc.contains(field)) return fallback;
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble(((String) val).trim()); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static long safeLong(DocumentSnapshot doc, String field, long fallback) {
        if (doc == null || !doc.contains(field)) return fallback;
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong(((String) val).trim()); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static boolean safeBoolean(DocumentSnapshot doc, String field, boolean fallback) {
        if (doc == null || !doc.contains(field)) return fallback;
        Object val = doc.get(field);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return fallback;
    }

    // Getters and Setters - Clean standard single types (prevents Firebase conflicting setter errors)
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerPhone() {
        return buyerPhone;
    }

    public void setBuyerPhone(String buyerPhone) {
        this.buyerPhone = buyerPhone;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getRoomOrFlatNo() {
        return roomOrFlatNo;
    }

    public void setRoomOrFlatNo(String roomOrFlatNo) {
        this.roomOrFlatNo = roomOrFlatNo;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(int floorNumber) {
        this.floorNumber = floorNumber;
    }

    public double getBuyerLat() {
        return buyerLat;
    }

    public void setBuyerLat(double buyerLat) {
        this.buyerLat = buyerLat;
    }

    public double getBuyerLng() {
        return buyerLng;
    }

    public void setBuyerLng(double buyerLng) {
        this.buyerLng = buyerLng;
    }

    public int getJarRefillCount() {
        return jarRefillCount;
    }

    public void setJarRefillCount(int jarRefillCount) {
        this.jarRefillCount = jarRefillCount;
    }

    public int getNewJarWithDepositCount() {
        return newJarWithDepositCount;
    }

    public void setNewJarWithDepositCount(int newJarWithDepositCount) {
        this.newJarWithDepositCount = newJarWithDepositCount;
    }

    public int getEmptyJarsToReturn() {
        return emptyJarsToReturn;
    }

    public void setEmptyJarsToReturn(int emptyJarsToReturn) {
        this.emptyJarsToReturn = emptyJarsToReturn;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getBkashTrxId() {
        return bkashTrxId;
    }

    public void setBkashTrxId(String bkashTrxId) {
        this.bkashTrxId = bkashTrxId;
    }

    @PropertyName("urgent")
    public boolean isUrgent() {
        return isUrgent;
    }

    @PropertyName("urgent")
    public void setUrgent(boolean urgent) {
        this.isUrgent = urgent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public long getDeliveredTimestamp() {
        return deliveredTimestamp;
    }

    public void setDeliveredTimestamp(long deliveredTimestamp) {
        this.deliveredTimestamp = deliveredTimestamp;
    }

    public String getDeliveredDate() {
        return deliveredDate;
    }

    public void setDeliveredDate(String deliveredDate) {
        this.deliveredDate = deliveredDate;
    }
}
