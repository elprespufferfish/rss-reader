package net.elprespufferfish.rssreader;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Handle refreshing feeds.
 */
public class RefreshService extends IntentService {

    public static final String COMPLETION_NOTIFICATION = "net.elprespufferfish.rssreader.RefreshService.COMPLETION";
    public static final String FORCE_REFRESH = "force_refresh";
    public static final String WAS_REFRESH_STARTED = "was_refresh_started";
    public static final String DID_REFRESH_COMPLETE = "did_refresh_complete";

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshService.class);
    private static final int ONGOING_REFRESH_NOTIFICATION_ID = 1;

    public class RefreshServiceBinder extends Binder {
        public boolean isRefreshInProgress() {
            return RefreshService.this.isRefreshInProgress.get();
        }
    }

    private final IBinder binder = new RefreshServiceBinder();
    private final AtomicBoolean isRefreshInProgress = new AtomicBoolean(false);

    public RefreshService() {
        super("RefreshService");
    }

    // TODO - set restart behavior

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        boolean forceRefresh = intent.getBooleanExtra(FORCE_REFRESH, Boolean.FALSE);
        if (!isOnWifi() && !forceRefresh) {
            LOGGER.info("Skipping refresh due to lack of WiFi");
            return;
        }

        isRefreshInProgress.set(true);

        // start in foreground to avoid getting killed by the platform
        Notification refreshNotification = new NotificationCompat
                .Builder(this)
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentTitle(getText(R.string.refresh_notification_title))
                .setContentText(getText(R.string.refresh_notification_text))
                .build();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent.getActivity(this, 0, notificationIntent, 0);
        startForeground(ONGOING_REFRESH_NOTIFICATION_ID, refreshNotification);

        DatabaseHelper databaseHelper = new DatabaseHelper(this);
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        boolean didRefreshComplete = false;
        boolean wasRefreshStarted = false;
        try {
            wasRefreshStarted = Feeds.getInstance().refresh();
            didRefreshComplete = true;
        } finally {
            database.close();

            isRefreshInProgress.set(false);

            // tear down ongoing notification
            stopForeground(true);

            // notify the UI that the refresh is complete
            Intent completionNotification = new Intent(COMPLETION_NOTIFICATION);
            completionNotification.putExtra(WAS_REFRESH_STARTED, wasRefreshStarted);
            completionNotification.putExtra(DID_REFRESH_COMPLETE, didRefreshComplete);
            LocalBroadcastManager.getInstance(this).sendBroadcast(completionNotification);
        }
    }

    private boolean isOnWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return isOnWifiLollipop(connectivityManager);
        } else {
            return isOnWifiPreLollipop(connectivityManager);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean isOnWifiLollipop(ConnectivityManager connectivityManager) {
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
            if (networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
                continue;
            }
            return NetworkInfo.State.CONNECTED.equals(networkInfo.getState());
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private boolean isOnWifiPreLollipop(ConnectivityManager connectivityManager) {
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo.isConnected();
    }

}
