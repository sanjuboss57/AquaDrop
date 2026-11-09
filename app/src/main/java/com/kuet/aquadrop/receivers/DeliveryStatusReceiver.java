package com.kuet.aquadrop.receivers;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import com.kuet.aquadrop.R;
import com.kuet.aquadrop.activities.BuyerDashboardActivity;
import com.kuet.aquadrop.activities.LiveTrackingActivity;

/**
 * BroadcastReceiver demonstrating Android Components (Week 10 Syllabus).
 * Handles application-level broadcast events for delivery status updates,
 * driver dispatch alerts, and order completion notifications.
 */
public class DeliveryStatusReceiver extends BroadcastReceiver {

    private static final String TAG = "DeliveryStatusReceiver";
    private static final String CHANNEL_ID = "aquadrop_delivery_channel";
    private static final String CHANNEL_NAME = "AquaDrop Delivery Notifications";

    // Broadcast Action Constants
    public static final String ACTION_DELIVERY_UPDATE = "com.kuet.aquadrop.ACTION_DELIVERY_UPDATE";
    public static final String ACTION_ORDER_ALERT = "com.kuet.aquadrop.ACTION_ORDER_ALERT";

    // Intent Extras
    public static final String EXTRA_ORDER_ID = "extra_order_id";
    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_MESSAGE = "extra_message";
    public static final String EXTRA_BUYER_LAT = "extra_buyer_lat";
    public static final String EXTRA_BUYER_LNG = "extra_buyer_lng";
    public static final String EXTRA_LOCATION_NAME = "extra_location_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "BroadcastReceiver onReceive() invoked with action: " + action);

        String orderId = intent.getStringExtra(EXTRA_ORDER_ID);
        String status = intent.getStringExtra(EXTRA_STATUS);
        String message = intent.getStringExtra(EXTRA_MESSAGE);

        if (orderId == null) orderId = "AquaDrop";
        if (message == null) message = "Order status updated: " + status;

        // Display brief toast to notify user immediately in the UI
        Toast.makeText(context, "📢 " + message, Toast.LENGTH_SHORT).show();

        // Build and display rich system notification with PendingIntent
        showSystemNotification(context, orderId, status, message, intent);
    }

    private void showSystemNotification(Context context, String orderId, String status, String message, Intent srcIntent) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        // Create NotificationChannel on API 26+ (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for live van delivery and order updates");
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        // Target intent when user taps the notification
        Intent targetIntent;
        double buyerLat = srcIntent.getDoubleExtra(EXTRA_BUYER_LAT, 0.0);
        double buyerLng = srcIntent.getDoubleExtra(EXTRA_BUYER_LNG, 0.0);
        String locName = srcIntent.getStringExtra(EXTRA_LOCATION_NAME);

        if (buyerLat != 0.0 && buyerLng != 0.0) {
            targetIntent = new Intent(context, LiveTrackingActivity.class);
            targetIntent.putExtra("orderId", orderId);
            targetIntent.putExtra("buyerLat", buyerLat);
            targetIntent.putExtra("buyerLng", buyerLng);
            targetIntent.putExtra("locationName", locName != null ? locName : "KUET Campus");
        } else {
            targetIntent = new Intent(context, BuyerDashboardActivity.class);
        }

        targetIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                targetIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        boolean isUrgent = srcIntent.getBooleanExtra("isUrgent", false);
        String title = isUrgent ? ("🚨 EMERGENCY ORDER • " + orderId) : ("AquaDrop • " + orderId);
        String locationNotes = srcIntent.getStringExtra("locationNotes");
        if (locationNotes != null && !locationNotes.isEmpty()) {
            message += "\nDirections: " + locationNotes;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(isUrgent ? R.drawable.ic_warning : R.drawable.ic_water_drop)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(isUrgent ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        int notificationId = (int) (System.currentTimeMillis() % 100000);
        notificationManager.notify(notificationId, builder.build());
    }
}

