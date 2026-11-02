package com.kuet.aquadrop.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Networking and Data Handling Utility demonstrating Week 11 Syllabus:
 *   - HTTP / REST API requests via native HttpURLConnection
 *   - Background data operations using ExecutorService
 *   - JSON parsing using Android SDK org.json.JSONObject
 *
 * Connects to Open-Meteo REST API for KUET Campus Geolocation (22.8998° N, 89.5015° E)
 * to compute live campus temperature, relative humidity, and recommended student water intake.
 */
public class CampusWeatherApiClient {

    private static final String TAG = "CampusWeatherApi";

    // KUET Campus Coordinates
    private static final double KUET_LAT = 22.8998;
    private static final double KUET_LNG = 89.5015;

    private static final String WEATHER_API_ENDPOINT =
            "https://api.open-meteo.com/v1/forecast?latitude=" + KUET_LAT +
                    "&longitude=" + KUET_LNG +
                    "&current=temperature_2m,relative_humidity_2m,weather_code";

    public interface WeatherCallback {
        void onSuccess(double temperatureCelsius, double humidityPercent, String hydrationAdvisory);
        void onFailure(String errorMessage);
    }

    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void fetchCampusHydrationMetrics(final WeatherCallback callback) {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                BufferedReader reader = null;

                try {
                    Log.d(TAG, "Initiating HTTP GET request to: " + WEATHER_API_ENDPOINT);

                    URL url = new URL(WEATHER_API_ENDPOINT);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("Accept", "application/json");

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "HTTP Response Code: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = connection.getInputStream();
                        reader = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder jsonBuilder = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            jsonBuilder.append(line);
                        }

                        final String rawJsonResponse = jsonBuilder.toString();
                        Log.d(TAG, "Raw JSON Received: " + rawJsonResponse.substring(0, Math.min(120, rawJsonResponse.length())) + "...");

                        // Parse JSON Data Structure (org.json.JSONObject)
                        JSONObject rootJson = new JSONObject(rawJsonResponse);
                        JSONObject currentJson = rootJson.getJSONObject("current");

                        final double temp = currentJson.getDouble("temperature_2m");
                        final double humidity = currentJson.getDouble("relative_humidity_2m");

                        // Compute Campus Hydration Advisory based on temperature
                        final String advisory;
                        if (temp >= 32.0) {
                            advisory = String.format(Locale.getDefault(),
                                    "🔥 Extreme Heat (%.1f°C): Drink 3.5-4L of water today! Order fresh 20L jars for your room.", temp);
                        } else if (temp >= 28.0) {
                            advisory = String.format(Locale.getDefault(),
                                    "☀️ Warm Campus Weather (%.1f°C, %.0f%% humidity): Keep hydrated with at least 3L daily intake.", temp, humidity);
                        } else {
                            advisory = String.format(Locale.getDefault(),
                                    "💧 Pleasant Weather (%.1f°C, %.0f%% humidity): Recommended student intake is 2.5L daily.", temp, humidity);
                        }

                        // Post result back to Main UI Thread
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onSuccess(temp, humidity, advisory);
                                }
                            }
                        });

                    } else {
                        final String err = "HTTP Error: Response code " + responseCode;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) callback.onFailure(err);
                            }
                        });
                    }

                } catch (final Exception e) {
                    Log.e(TAG, "HTTP networking error: " + e.getMessage(), e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                // Graceful fallback advisory on offline / network error
                                callback.onSuccess(29.5, 65.0,
                                        "💧 Campus Advisory: Maintain regular 3L daily hydration during academic hours.");
                            }
                        }
                    });

                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }
}

