package com.kuet.aquadrop.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ContentProvider demonstrating Android Components (Week 10 Syllabus).
 * Exposes an inter-component and cross-application content interface for
 * AquaDrop drinking water orders and water quality sensor metrics.
 *
 * Authority: com.kuet.aquadrop.provider
 * URIs:
 *   content://com.kuet.aquadrop.provider/orders
 *   content://com.kuet.aquadrop.provider/orders/#
 *   content://com.kuet.aquadrop.provider/water_quality
 */
public class AquaDropContentProvider extends ContentProvider {

    private static final String TAG = "AquaDropProvider";

    public static final String AUTHORITY = "com.kuet.aquadrop.provider";
    public static final Uri CONTENT_URI_ORDERS = Uri.parse("content://" + AUTHORITY + "/orders");
    public static final Uri CONTENT_URI_WATER_QUALITY = Uri.parse("content://" + AUTHORITY + "/water_quality");

    // URI Matcher Codes
    private static final int CODE_ORDERS = 1;
    private static final int CODE_ORDER_ID = 2;
    private static final int CODE_WATER_QUALITY = 3;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, "orders", CODE_ORDERS);
        uriMatcher.addURI(AUTHORITY, "orders/#", CODE_ORDER_ID);
        uriMatcher.addURI(AUTHORITY, "water_quality", CODE_WATER_QUALITY);
    }

    // Standard Column Names
    public static final String COL_ID = "_id";
    public static final String COL_ORDER_ID = "order_id";
    public static final String COL_BUYER_NAME = "buyer_name";
    public static final String COL_BUYER_PHONE = "buyer_phone";
    public static final String COL_LOCATION = "location";
    public static final String COL_STATUS = "status";
    public static final String COL_TOTAL_PRICE = "total_price";
    public static final String COL_TIMESTAMP = "timestamp";

    // In-memory data storage backing the ContentProvider
    private static final List<ContentValues> inMemoryOrders = Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean onCreate() {
        Log.d(TAG, "ContentProvider onCreate() initialized successfully.");

        // Seed initial reference sample for testing and component queries
        if (inMemoryOrders.isEmpty()) {
            ContentValues sample = new ContentValues();
            sample.put(COL_ID, 1);
            sample.put(COL_ORDER_ID, "AQ-100001");
            sample.put(COL_BUYER_NAME, "Amar Ekushey Resident");
            sample.put(COL_BUYER_PHONE, "01700000000");
            sample.put(COL_LOCATION, "Amar Ekushey Hall");
            sample.put(COL_STATUS, "PENDING");
            sample.put(COL_TOTAL_PRICE, 45.0);
            sample.put(COL_TIMESTAMP, System.currentTimeMillis());
            inMemoryOrders.add(sample);
        }

        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        Log.d(TAG, "ContentProvider query() received URI: " + uri);

        int match = uriMatcher.match(uri);
        String[] columns = new String[]{
                COL_ID, COL_ORDER_ID, COL_BUYER_NAME, COL_BUYER_PHONE,
                COL_LOCATION, COL_STATUS, COL_TOTAL_PRICE, COL_TIMESTAMP
        };

        MatrixCursor cursor = new MatrixCursor(columns);

        switch (match) {
            case CODE_ORDERS:
                synchronized (inMemoryOrders) {
                    for (ContentValues cv : inMemoryOrders) {
                        cursor.addRow(new Object[]{
                                cv.getAsLong(COL_ID),
                                cv.getAsString(COL_ORDER_ID),
                                cv.getAsString(COL_BUYER_NAME),
                                cv.getAsString(COL_BUYER_PHONE),
                                cv.getAsString(COL_LOCATION),
                                cv.getAsString(COL_STATUS),
                                cv.getAsDouble(COL_TOTAL_PRICE),
                                cv.getAsLong(COL_TIMESTAMP)
                        });
                    }
                }
                break;

            case CODE_ORDER_ID:
                long reqId = ContentUris.parseId(uri);
                synchronized (inMemoryOrders) {
                    for (ContentValues cv : inMemoryOrders) {
                        Long id = cv.getAsLong(COL_ID);
                        if (id != null && id == reqId) {
                            cursor.addRow(new Object[]{
                                    cv.getAsLong(COL_ID),
                                    cv.getAsString(COL_ORDER_ID),
                                    cv.getAsString(COL_BUYER_NAME),
                                    cv.getAsString(COL_BUYER_PHONE),
                                    cv.getAsString(COL_LOCATION),
                                    cv.getAsString(COL_STATUS),
                                    cv.getAsDouble(COL_TOTAL_PRICE),
                                    cv.getAsLong(COL_TIMESTAMP)
                            });
                            break;
                        }
                    }
                }
                break;

            case CODE_WATER_QUALITY:
                MatrixCursor qualityCursor = new MatrixCursor(new String[]{"tds", "ph", "batch_status", "safe"});
                qualityCursor.addRow(new Object[]{42.0, 7.2, "Certified RO + UV Sterilized", 1});
                return qualityCursor;

            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }

        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (uriMatcher.match(uri) != CODE_ORDERS) {
            throw new IllegalArgumentException("Invalid URI for insert: " + uri);
        }

        ContentValues cv = values != null ? new ContentValues(values) : new ContentValues();
        long newId = inMemoryOrders.size() + 1;
        cv.put(COL_ID, newId);
        if (!cv.containsKey(COL_TIMESTAMP)) {
            cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        }

        inMemoryOrders.add(cv);
        Log.d(TAG, "ContentProvider insert() added record ID: " + newId);

        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return ContentUris.withAppendedId(CONTENT_URI_ORDERS, newId);
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        int match = uriMatcher.match(uri);
        int updatedCount = 0;

        if (match == CODE_ORDER_ID) {
            long reqId = ContentUris.parseId(uri);
            synchronized (inMemoryOrders) {
                for (ContentValues cv : inMemoryOrders) {
                    Long id = cv.getAsLong(COL_ID);
                    if (id != null && id == reqId && values != null) {
                        cv.putAll(values);
                        updatedCount++;
                        break;
                    }
                }
            }
        } else if (match == CODE_ORDERS) {
            synchronized (inMemoryOrders) {
                for (ContentValues cv : inMemoryOrders) {
                    if (values != null) {
                        cv.putAll(values);
                        updatedCount++;
                    }
                }
            }
        }

        if (updatedCount > 0 && getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return updatedCount;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int match = uriMatcher.match(uri);
        int deleted = 0;

        if (match == CODE_ORDER_ID) {
            long reqId = ContentUris.parseId(uri);
            synchronized (inMemoryOrders) {
                for (int i = 0; i < inMemoryOrders.size(); i++) {
                    Long id = inMemoryOrders.get(i).getAsLong(COL_ID);
                    if (id != null && id == reqId) {
                        inMemoryOrders.remove(i);
                        deleted = 1;
                        break;
                    }
                }
            }
        }

        if (deleted > 0 && getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return deleted;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (uriMatcher.match(uri)) {
            case CODE_ORDERS:
                return "vnd.android.cursor.dir/vnd.com.kuet.aquadrop.order";
            case CODE_ORDER_ID:
                return "vnd.android.cursor.item/vnd.com.kuet.aquadrop.order";
            case CODE_WATER_QUALITY:
                return "vnd.android.cursor.dir/vnd.com.kuet.aquadrop.waterquality";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }
}

