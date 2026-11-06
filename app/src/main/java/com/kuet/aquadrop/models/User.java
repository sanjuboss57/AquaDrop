package com.kuet.aquadrop.models;

import java.io.Serializable;
import com.google.firebase.database.DataSnapshot;

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
    private String sellerAccessKey;
    private boolean sellerAuthorized;

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

    public String getSellerAccessKey() {
        return sellerAccessKey;
    }

    public void setSellerAccessKey(String sellerAccessKey) {
        this.sellerAccessKey = sellerAccessKey;
    }

    public boolean isSellerAuthorized() {
        return sellerAuthorized;
    }

    public void setSellerAuthorized(boolean sellerAuthorized) {
        this.sellerAuthorized = sellerAuthorized;
    }

    public static User fromDataSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return null;
        try {
            User u = snapshot.getValue(User.class);
            if (u != null) {
                if (u.getUid() == null || u.getUid().isEmpty()) {
                    u.setUid(snapshot.getKey());
                }
                return u;
            }
        } catch (Exception ignored) {}

        try {
            User u = new User();
            u.setUid(snapshot.getKey());
            if (snapshot.hasChild("name") && snapshot.child("name").getValue() != null)
                u.setName(String.valueOf(snapshot.child("name").getValue()));
            if (snapshot.hasChild("email") && snapshot.child("email").getValue() != null)
                u.setEmail(String.valueOf(snapshot.child("email").getValue()));
            if (snapshot.hasChild("phone") && snapshot.child("phone").getValue() != null)
                u.setPhone(String.valueOf(snapshot.child("phone").getValue()));
            if (snapshot.hasChild("role") && snapshot.child("role").getValue() != null)
                u.setRole(String.valueOf(snapshot.child("role").getValue()));
            if (snapshot.hasChild("locationName") && snapshot.child("locationName").getValue() != null)
                u.setLocationName(String.valueOf(snapshot.child("locationName").getValue()));
            if (snapshot.hasChild("roomOrFlatNo") && snapshot.child("roomOrFlatNo").getValue() != null)
                u.setRoomOrFlatNo(String.valueOf(snapshot.child("roomOrFlatNo").getValue()));

            if (snapshot.hasChild("floorNumber") && snapshot.child("floorNumber").getValue() != null) {
                Object v = snapshot.child("floorNumber").getValue();
                if (v instanceof Number) u.setFloorNumber(((Number) v).intValue());
                else {
                    try { u.setFloorNumber(Integer.parseInt(String.valueOf(v))); } catch (Exception ignored) {}
                }
            }

            if (snapshot.hasChild("latitude") && snapshot.child("latitude").getValue() != null) {
                Object v = snapshot.child("latitude").getValue();
                if (v instanceof Number) u.setLatitude(((Number) v).doubleValue());
                else {
                    try { u.setLatitude(Double.parseDouble(String.valueOf(v))); } catch (Exception ignored) {}
                }
            }

            if (snapshot.hasChild("longitude") && snapshot.child("longitude").getValue() != null) {
                Object v = snapshot.child("longitude").getValue();
                if (v instanceof Number) u.setLongitude(((Number) v).doubleValue());
                else {
                    try { u.setLongitude(Double.parseDouble(String.valueOf(v))); } catch (Exception ignored) {}
                }
            }

            if (snapshot.hasChild("emptyJarsHeld") && snapshot.child("emptyJarsHeld").getValue() != null) {
                Object v = snapshot.child("emptyJarsHeld").getValue();
                if (v instanceof Number) u.setEmptyJarsHeld(((Number) v).intValue());
                else {
                    try { u.setEmptyJarsHeld(Integer.parseInt(String.valueOf(v))); } catch (Exception ignored) {}
                }
            }

            if (snapshot.hasChild("depositBalance") && snapshot.child("depositBalance").getValue() != null) {
                Object v = snapshot.child("depositBalance").getValue();
                if (v instanceof Number) u.setDepositBalance(((Number) v).doubleValue());
                else {
                    try { u.setDepositBalance(Double.parseDouble(String.valueOf(v))); } catch (Exception ignored) {}
                }
            }

            if (snapshot.hasChild("sellerAccessKey") && snapshot.child("sellerAccessKey").getValue() != null)
                u.setSellerAccessKey(String.valueOf(snapshot.child("sellerAccessKey").getValue()));

            if (snapshot.hasChild("sellerAuthorized") && snapshot.child("sellerAuthorized").getValue() != null) {
                Object v = snapshot.child("sellerAuthorized").getValue();
                if (v instanceof Boolean) u.setSellerAuthorized((Boolean) v);
                else u.setSellerAuthorized(Boolean.parseBoolean(String.valueOf(v)));
            }

            return u;
        } catch (Exception ignored) {
            return null;
        }
    }
}
