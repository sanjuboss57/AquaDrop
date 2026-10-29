package com.kuet.aquadrop.models;

import java.io.Serializable;

/**
 * Model representing daily drinking water quality metrics (TDS, pH, Purification Status).
 */
public class WaterQuality implements Serializable {

    private double tds;
    private double ph;
    private String batchStatus;
    private long timestamp;

    public WaterQuality() {
        // Default safe baseline values
        this.tds = 42.0;
        this.ph = 7.3;
        this.batchStatus = "Certified Pure (Reverse Osmosis + UV)";
        this.timestamp = System.currentTimeMillis();
    }

    public WaterQuality(double tds, double ph, String batchStatus, long timestamp) {
        this.tds = tds;
        this.ph = ph;
        this.batchStatus = batchStatus;
        this.timestamp = timestamp;
    }

    public double getTds() {
        return tds;
    }

    public void setTds(double tds) {
        this.tds = tds;
    }

    public double getPh() {
        return ph;
    }

    public void setPh(double ph) {
        this.ph = ph;
    }

    public String getBatchStatus() {
        return batchStatus;
    }

    public void setBatchStatus(String batchStatus) {
        this.batchStatus = batchStatus;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

