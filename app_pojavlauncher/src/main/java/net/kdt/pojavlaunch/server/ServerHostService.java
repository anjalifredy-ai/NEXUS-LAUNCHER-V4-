package net.kdt.pojavlaunch.server;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class ServerHostService extends Service {
    public static final String ACTION_START = "net.kdt.pojavlaunch.server.START";
    public static final String ACTION_STOP = "net.kdt.pojavlaunch.server.STOP";
    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_PORT = "port";
    public static final int NOTIFICATION_ID_SERVER = 42;
    public static final int PENDING_STOP = 42;

    @Override
    public void onCreate() {
        Tools.buildNotificationChannel(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        int port = intent != null ? intent.getIntExtra(EXTRA_PORT, 25565) : 25565;
        String lan = LanNetworkHelper.getLanIpv4();
        String text = (lan != null ? lan : "localhost") + ":" + port;

        Intent open = new Intent(this, LauncherActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

        Intent stopI = new Intent(this, ServerHostService.class);
        stopI.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, PENDING_STOP, stopI,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, "channel_id")
                .setContentTitle("Nexus Server Running")
                .setContentText(text)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentIntent(content)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop service", stopPi)
                .setOngoing(true)
                .setNotificationSilent();
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_SERVER, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID_SERVER, n);
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
