package com.kuet.aquadrop.models;

import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;

/**
 * Model representing a drinking water jar order in AquaDrop.
 */
@IgnoreExtraProperties
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
    private String sellerId = "";
    private String assignedSellerId = "";
    private String assignedSellerName = "";
    private long deliveredTimestamp;
    private String deliveredDate;
    private String locationNotes; // Optional exact location directions or landmark explanation
    private String optionalDirections;

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

            String sellerIdVal = safeString(doc, "assignedSellerId", "");
            if (TextUtils.isEmpty(sellerIdVal)) {
                sellerIdVal = safeString(doc, "sellerId", "");
            }
            order.setSellerId(sellerIdVal);
            order.setAssignedSellerId(sellerIdVal);
            order.setAssignedSellerName(safeString(doc, "assignedSellerName", ""));

            order.setDeliveredDate(safeString(doc, "deliveredDate", ""));
            order.setDeliveredTimestamp(safeLong(doc, "deliveredTimestamp", 0L));

            String directionsVal = safeString(doc, "optionalDirections", "");
            if (TextUtils.isEmpty(directionsVal)) {
                directionsVal = safeString(doc, "locationNotes", "");
            }
            order.setLocationNotes(directionsVal);
            order.setOptionalDirections(directionsVal);

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

    /**
     * Resilient deserializer for Firebase Realtime Database DataSnapshot.
     */
    public static WaterOrder fromDataSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }

        try {
            WaterOrder order = snapshot.getValue(WaterOrder.class);
            if (order != null) {
                if (TextUtils.isEmpty(order.getOrderId())) {
                    order.setOrderId(snapshot.getKey());
                }
                return order;
            }
        } catch (Throwable t) {
            Log.w("WaterOrder", "RTDB POJO conversion failed for key " + snapshot.getKey() + ": " + t.getMessage());
        }

        // Resilient fallback manual mapping
        try {
            WaterOrder order = new WaterOrder();
            order.setOrderId(safeRtdbString(snapshot, "orderId", snapshot.getKey()));
            order.setBuyerId(safeRtdbString(snapshot, "buyerId", ""));
            order.setBuyerName(safeRtdbString(snapshot, "buyerName", "AquaDrop Customer"));
            order.setBuyerPhone(safeRtdbString(snapshot, "buyerPhone", ""));
            order.setLocationName(safeRtdbString(snapshot, "locationName", "Campus Location"));
            order.setRoomOrFlatNo(safeRtdbString(snapshot, "roomOrFlatNo", ""));
            order.setFloorNumber(safeRtdbInt(snapshot, "floorNumber", 1));

            order.setBuyerLat(safeRtdbDouble(snapshot, "buyerLat", 0.0));
            order.setBuyerLng(safeRtdbDouble(snapshot, "buyerLng", 0.0));

            order.setJarRefillCount(safeRtdbInt(snapshot, "jarRefillCount", 1));
            order.setNewJarWithDepositCount(safeRtdbInt(snapshot, "newJarWithDepositCount", 0));
            order.setEmptyJarsToReturn(safeRtdbInt(snapshot, "emptyJarsToReturn", 0));
            order.setTotalPrice(safeRtdbDouble(snapshot, "totalPrice", 0.0));

            order.setPaymentMethod(safeRtdbString(snapshot, "paymentMethod", PAYMENT_CASH));
            order.setBkashTrxId(safeRtdbString(snapshot, "bkashTrxId", ""));

            boolean urgent = safeRtdbBoolean(snapshot, "urgent", false);
            if (!urgent) urgent = safeRtdbBoolean(snapshot, "isUrgent", false);
            order.setUrgent(urgent);

            order.setStatus(safeRtdbString(snapshot, "status", STATUS_PENDING));
            order.setTimestamp(safeRtdbLong(snapshot, "timestamp", System.currentTimeMillis()));
            order.setQrToken(safeRtdbString(snapshot, "qrToken", ""));

            String sellerIdVal = safeRtdbString(snapshot, "assignedSellerId", "");
            if (TextUtils.isEmpty(sellerIdVal)) {
                sellerIdVal = safeRtdbString(snapshot, "sellerId", "");
            }
            order.setSellerId(sellerIdVal);
            order.setAssignedSellerId(sellerIdVal);
            order.setAssignedSellerName(safeRtdbString(snapshot, "assignedSellerName", ""));

            order.setDeliveredDate(safeRtdbString(snapshot, "deliveredDate", ""));
            order.setDeliveredTimestamp(safeRtdbLong(snapshot, "deliveredTimestamp", 0L));

            String directionsVal = safeRtdbString(snapshot, "optionalDirections", "");
            if (TextUtils.isEmpty(directionsVal)) {
                directionsVal = safeRtdbString(snapshot, "locationNotes", "");
            }
            order.setLocationNotes(directionsVal);
            order.setOptionalDirections(directionsVal);

            return order;
        } catch (Throwable t) {
            Log.e("WaterOrder", "Manual RTDB deserialization error for key " + snapshot.getKey(), t);
            WaterOrder minimal = new WaterOrder();
            minimal.setOrderId(snapshot.getKey());
            minimal.setStatus(STATUS_PENDING);
            return minimal;
        }
    }

    private static String safeRtdbString(DataSnapshot snapshot, String field, String fallback) {
        if (snapshot == null || !snapshot.hasChild(field) || snapshot.child(field).getValue() == null) return fallback;
        return String.valueOf(snapshot.child(field).getValue());
    }

    private static int safeRtdbInt(DataSnapshot snapshot, String field, int fallback) {
        if (snapshot == null || !snapshot.hasChild(field) || snapshot.child(field).getValue() == null) return fallback;
        Object val = snapshot.child(field).getValue();
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt(((String) val).trim()); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static double safeRtdbDouble(DataSnapshot snapshot, String field, double fallback) {
        if (snapshot == null || !snapshot.hasChild(field) || snapshot.child(field).getValue() == null) return fallback;
        Object val = snapshot.child(field).getValue();
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble(((String) val).trim()); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static long safeRtdbLong(DataSnapshot snapshot, String field, long fallback) {
        if (snapshot == null || !snapshot.hasChild(field) || snapshot.child(field).getValue() == null) return fallback;
        Object val = snapshot.child(field).getValue();
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong(((String) val).trim()); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static boolean safeRtdbBoolean(DataSnapshot snapshot, String field, boolean fallback) {
        if (snapshot == null || !snapshot.hasChild(field) || snapshot.child(field).getValue() == null) return fallback;
        Object val = snapshot.child(field).getValue();
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean(((String) val).trim());
        return fallback;
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

    @PropertyName("isUrgent")
    public void setIsUrgent(boolean isUrgent) {
        this.isUrgent = isUrgent;
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
        if (!TextUtils.isEmpty(assignedSellerId)) return assignedSellerId;
        return sellerId != null ? sellerId : "";
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId != null ? sellerId : "";
        this.assignedSellerId = this.sellerId;
    }

    public String getAssignedSellerId() {
        if (!TextUtils.isEmpty(assignedSellerId)) return assignedSellerId;
        return sellerId != null ? sellerId : "";
    }

    public void setAssignedSellerId(String assignedSellerId) {
        this.assignedSellerId = assignedSellerId != null ? assignedSellerId : "";
        this.sellerId = this.assignedSellerId;
    }

    public String getAssignedSellerName() {
        return assignedSellerName != null ? assignedSellerName : "";
    }

    public void setAssignedSellerName(String assignedSellerName) {
        this.assignedSellerName = assignedSellerName != null ? assignedSellerName : "";
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

    public String getLocationNotes() {
        if (!TextUtils.isEmpty(locationNotes)) return locationNotes;
        return optionalDirections;
    }

    public void setLocationNotes(String locationNotes) {
        this.locationNotes = locationNotes;
        this.optionalDirections = locationNotes;
    }

    public String getOptionalDirections() {
        if (!TextUtils.isEmpty(optionalDirections)) return optionalDirections;
        return locationNotes;
    }

    public void setOptionalDirections(String optionalDirections) {
        this.optionalDirections = optionalDirections;
        this.locationNotes = optionalDirections;
    }
}
