package com.kuet.aquadrop.models;

import java.io.Serializable;

/**
 * Model representing live GPS telemetry broadcast to Firebase Realtime Database
 * under active_deliveries/{orderId} for low-latency driver tracking.
 */
public class LiveTelemetry implements Serializable {

    private String orderId;
    private double driverLat;
    private double driverLng;
    private int etaMinutes;
    private double distanceMeters;
    private long lastUpdated;

    // Required empty constructor for Realtime Database deserialization
    public LiveTelemetry() {
    }

    public LiveTelemetry(String orderId, double driverLat, double driverLng,
                         int etaMinutes, double distanceMeters, long lastUpdated) {
        this.orderId = orderId;
        this.driverLat = driverLat;
        this.driverLng = driverLng;
        this.etaMinutes = etaMinutes;
        this.distanceMeters = distanceMeters;
        this.lastUpdated = lastUpdated;
    }

    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public double getDriverLat() {
        return driverLat;
    }

    public void setDriverLat(double driverLat) {
        this.driverLat = driverLat;
    }

    public double getDriverLng() {
        return driverLng;
    }

    public void setDriverLng(double driverLng) {
        this.driverLng = driverLng;
    }

    public int getEtaMinutes() {
        return etaMinutes;
    }

    public void setEtaMinutes(int etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
