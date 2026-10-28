package com.kuet.aquadrop.utils;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of KUET campus residence halls, academic zones, and supply points.
 */
public final class KuetLocations {

    public static class CampusLocation {
        private final String name;
        private final double latitude;
        private final double longitude;

        public CampusLocation(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getName() {
            return name;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public LatLng toLatLng() {
            return new LatLng(latitude, longitude);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // Static locations map
    private static final Map<String, CampusLocation> LOCATIONS_MAP;
    private static final List<CampusLocation> LOCATIONS_LIST;

    // Campus geographic center
    public static final LatLng KUET_CAMPUS_CENTER = new LatLng(22.8995, 89.5020);
    public static final float DEFAULT_MAP_ZOOM = 16.5f;

    static {
        Map<String, CampusLocation> map = new LinkedHashMap<>();

        addLocation(map, "Fazlul Huq Hall", 22.8992, 89.5012);
        addLocation(map, "Lalan Shah Hall", 22.9008, 89.5005);
        addLocation(map, "Khan Jahan Ali Hall", 22.8985, 89.5021);
        addLocation(map, "Amar Ekushey Hall", 22.9015, 89.5018);
        addLocation(map, "Dr. M.A. Rashid Hall", 22.8979, 89.5009);
        addLocation(map, "Bangabandhu Hall", 22.9022, 89.5010);
        addLocation(map, "Rokeya Hall", 22.8995, 89.5035);
        addLocation(map, "KUET Teacher & Staff Quarters", 22.8980, 89.5042);
        addLocation(map, "Fulbarigate Bazar Area", 22.8965, 89.5080);
        addLocation(map, "Teligati Student Mess Zone", 22.9035, 89.4985);
        addLocation(map, "Water Supply Depot Center", 22.8970, 89.5060);

        LOCATIONS_MAP = Collections.unmodifiableMap(map);
        LOCATIONS_LIST = Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    private static void addLocation(Map<String, CampusLocation> map, String name, double lat, double lng) {
        map.put(name, new CampusLocation(name, lat, lng));
    }

    private KuetLocations() {
        // Utility class
    }

    public static List<CampusLocation> getAllLocations() {
        return LOCATIONS_LIST;
    }

    public static List<String> getLocationNames() {
        return new ArrayList<>(LOCATIONS_MAP.keySet());
    }

    public static CampusLocation getLocationByName(String name) {
        return LOCATIONS_MAP.get(name);
    }

    public static LatLng getLatLngByName(String name) {
        CampusLocation location = LOCATIONS_MAP.get(name);
        return (location != null) ? location.toLatLng() : KUET_CAMPUS_CENTER;
    }

    public static CampusLocation getNearestLocation(double lat, double lng) {
        CampusLocation nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (CampusLocation loc : LOCATIONS_LIST) {
            double dist = EtaCalculator.distanceMeters(lat, lng, loc.getLatitude(), loc.getLongitude());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = loc;
            }
        }
        return nearest;
    }
}
