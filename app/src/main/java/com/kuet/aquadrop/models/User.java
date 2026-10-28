package com.kuet.aquadrop.models;

import java.io.Serializable;

/**
 * Model representing a registered consumer (BUYER) or depot supplier (SELLER) in AquaDrop.
 */
public class User implements Serializable {

    public static final String ROLE_BUYER = "BUYER";
    public static final String ROLE_SELLER = "SELLER";

    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role; // "BUYER" or "SELLER"
    private String locationName; // Hall or quarters name from KuetLocations
    private String roomOrFlatNo;
    private int floorNumber;
    private double latitude;
    private double longitude;
    private int emptyJarsHeld;
    private double depositBalance;

    // Required empty constructor for Firestore deserialization
    public User() {
    }

    public User(String uid, String name, String email, String phone, String role,
                String locationName, String roomOrFlatNo, int floorNumber,
                double latitude, double longitude, int emptyJarsHeld, double depositBalance) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.locationName = locationName;
        this.roomOrFlatNo = roomOrFlatNo;
        this.floorNumber = floorNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.emptyJarsHeld = emptyJarsHeld;
        this.depositBalance = depositBalance;
    }

    // Role helpers
    public boolean isSeller() {
        return ROLE_SELLER.equalsIgnoreCase(role);
    }

    public boolean isBuyer() {
        return ROLE_BUYER.equalsIgnoreCase(role);
    }

    // Getters and Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getEmptyJarsHeld() {
        return emptyJarsHeld;
    }

    public void setEmptyJarsHeld(int emptyJarsHeld) {
        this.emptyJarsHeld = emptyJarsHeld;
    }

    public double getDepositBalance() {
        return depositBalance;
    }

    public void setDepositBalance(double depositBalance) {
        this.depositBalance = depositBalance;
    }
}
