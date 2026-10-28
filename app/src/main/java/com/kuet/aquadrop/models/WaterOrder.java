package com.kuet.aquadrop.models;

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

    // Getters and Setters
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

    public boolean isUrgent() {
        return isUrgent;
    }

    public void setUrgent(boolean urgent) {
        isUrgent = urgent;
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
}
