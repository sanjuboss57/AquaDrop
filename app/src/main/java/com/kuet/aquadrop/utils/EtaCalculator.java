package com.kuet.aquadrop.utils;

import java.util.Locale;

/**
 * Utility for Haversine distance calculations and campus delivery ETA predictions.
 */
public final class EtaCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double CAMPUS_SPEED_KMH = 15.0; // Realistic depot van / rickshaw speed on campus
    private static final double MINUTES_PER_FLOOR = 1.5; // Stair-climb overhead with heavy 20L jars
    private static final double MINUTES_PER_QUEUE_ORDER = 4.0; // Delivery drop-off buffer per prior order

    private EtaCalculator() {
        // Utility class
    }

    /**
     * Calculates distance between two GPS coordinates using the Haversine formula.
     *
     * @return Distance in kilometers.
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculates distance in meters.
     */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        return distanceKm(lat1, lon1, lat2, lon2) * 1000.0;
    }

    /**
     * Computes estimated arrival time in minutes based on distance, floor level, and depot queue.
     *
     * @param distanceKm          Distance between driver and delivery location in kilometers.
     * @param floorNumber         Floor number of delivery destination (1 = ground).
     * @param activeOrdersInQueue Number of prior orders driver must fulfill before this one.
     * @return Estimated arrival time in minutes (minimum 2 minutes).
     */
    public static int calculateEtaMinutes(double distanceKm, int floorNumber, int activeOrdersInQueue) {
        if (distanceKm < 0) {
            distanceKm = 0;
        }

        // Travel time based on 15 km/h campus speed: (distanceKm / 15 km/h) * 60 min/h = distanceKm * 4.0
        double transitMinutes = (distanceKm / CAMPUS_SPEED_KMH) * 60.0;

        // Stair climbing penalty for halls without elevators
        int floorsToClimb = Math.max(0, floorNumber - 1);
        double verticalMinutes = floorsToClimb * MINUTES_PER_FLOOR;

        // Queue dispatch overhead
        int priorOrders = Math.max(0, activeOrdersInQueue);
        double queueMinutes = priorOrders * MINUTES_PER_QUEUE_ORDER;

        double totalMinutes = transitMinutes + verticalMinutes + queueMinutes;

        // Return rounded integer with 2-minute realistic minimum
        return Math.max(2, (int) Math.ceil(totalMinutes));
    }

    /**
     * Formats distance in readable text (e.g., "350 m" or "1.2 km").
     */
    public static String formatDistance(double distanceMeters) {
        if (distanceMeters < 1000) {
            return String.format(Locale.getDefault(), "%d m", Math.round(distanceMeters));
        } else {
            return String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000.0);
        }
    }

    /**
     * Formats ETA in readable text (e.g., "5 mins" or "1 hr 10 mins").
     */
    public static String formatEta(int minutes) {
        if (minutes <= 1) {
            return "Arriving shortly";
        } else if (minutes < 60) {
            return String.format(Locale.getDefault(), "%d mins", minutes);
        } else {
            int hours = minutes / 60;
            int remainingMins = minutes % 60;
            if (remainingMins == 0) {
                return String.format(Locale.getDefault(), "%d hr", hours);
            }
            return String.format(Locale.getDefault(), "%d hr %d mins", hours, remainingMins);
        }
    }
}
