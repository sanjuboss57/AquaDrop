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
            order.setOrderId(doc.contains("orderId") && doc.getString("orderId") != null ? doc.getString("orderId") : doc.getId());
            order.setBuyerId(doc.getString("buyerId"));
            order.setBuyerName(doc.getString("buyerName"));
            order.setBuyerPhone(doc.getString("buyerPhone"));
            order.setLocationName(doc.getString("locationName"));
            order.setRoomOrFlatNo(doc.getString("roomOrFlatNo"));

            Object floorVal = doc.get("floorNumber");
            if (floorVal instanceof Number) {
                order.setFloorNumber(((Number) floorVal).intValue());
            } else if (floorVal instanceof String) {
                try { order.setFloorNumber(Integer.parseInt((String) floorVal)); } catch (Exception ignored) {}
            } else {
                order.setFloorNumber(1);
            }

            Object latVal = doc.get("buyerLat");
            if (latVal instanceof Number) {
                order.setBuyerLat(((Number) latVal).doubleValue());
            }

            Object lngVal = doc.get("buyerLng");
            if (lngVal instanceof Number) {
                order.setBuyerLng(((Number) lngVal).doubleValue());
            }

            Object refillVal = doc.get("jarRefillCount");
            if (refillVal instanceof Number) {
                order.setJarRefillCount(((Number) refillVal).intValue());
            } else if (refillVal instanceof String) {
                try { order.setJarRefillCount(Integer.parseInt((String) refillVal)); } catch (Exception ignored) {}
            } else {
                order.setJarRefillCount(1);
            }

            Object depositVal = doc.get("newJarWithDepositCount");
            if (depositVal instanceof Number) {
                order.setNewJarWithDepositCount(((Number) depositVal).intValue());
            }

            Object emptyVal = doc.get("emptyJarsToReturn");
            if (emptyVal instanceof Number) {
                order.setEmptyJarsToReturn(((Number) emptyVal).intValue());
            } else if (emptyVal instanceof String) {
                try { order.setEmptyJarsToReturn(Integer.parseInt((String) emptyVal)); } catch (Exception ignored) {}
            }

            Object priceVal = doc.get("totalPrice");
            if (priceVal instanceof Number) {
                order.setTotalPrice(((Number) priceVal).doubleValue());
            } else if (priceVal instanceof String) {
                try { order.setTotalPrice(Double.parseDouble((String) priceVal)); } catch (Exception ignored) {}
            }

            order.setPaymentMethod(doc.getString("paymentMethod"));
            order.setBkashTrxId(doc.getString("bkashTrxId"));

            Boolean urgent = doc.getBoolean("urgent");
            if (urgent == null) urgent = doc.getBoolean("isUrgent");
            order.setUrgent(Boolean.TRUE.equals(urgent));

            order.setStatus(doc.getString("status"));

            Object tsVal = doc.get("timestamp");
            if (tsVal instanceof Number) {
                order.setTimestamp(((Number) tsVal).longValue());
            } else {
                order.setTimestamp(System.currentTimeMillis());
            }

            order.setQrToken(doc.getString("qrToken"));
            order.setSellerId(doc.getString("sellerId"));
            order.setDeliveredDate(doc.getString("deliveredDate"));

            Object dtVal = doc.get("deliveredTimestamp");
            if (dtVal instanceof Number) {
                order.setDeliveredTimestamp(((Number) dtVal).longValue());
            }

            return order;
        } catch (Throwable t) {
            Log.e("WaterOrder", "Manual deserialization error for doc " + doc.getId(), t);
            return null;
        }
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
