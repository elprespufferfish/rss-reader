package net.elprespufferfish.rssreader;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.webkit.WebView;

import com.google.common.eventbus.EventBus;

import net.elprespufferfish.rssreader.settings.Settings;
import net.elprespufferfish.rssreader.util.LoggingActivityLifecycleCallbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

public class RssReaderApplication extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(RssReaderApplication.class);

    public static RssReaderApplication fromContext(Context context) {
        return (RssReaderApplication) context.getApplicationContext();
    }

    private EventBus eventBus;

    @Override
    public void onCreate() {
        super.onCreate();

        eventBus = new EventBus();

        registerActivityLifecycleCallbacks(new ForegroundStatus());

        Feeds.initialize(this);

        scheduleRefresh();
        monitorRefreshPreferenceChange();

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    //.detectActivityLeaks() // TODO - this keeps causing issues with WebViewActivity
                    //.detectLeakedClosableObjects() // TODO - bug in okhttp exposes a leak in HttpResponseCachehttps://github.com/square/okhttp/issues/215
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // NOTE: webkit initialization requires disk reads
                StrictMode.ThreadPolicy oldThreadPolicy = StrictMode.allowThreadDiskReads();
                WebView.setWebContentsDebuggingEnabled(true);
                StrictMode.setThreadPolicy(oldThreadPolicy);
            }

            registerActivityLifecycleCallbacks(new LoggingActivityLifecycleCallbacks());
        }
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * (Re)schedule an automated refresh of all feeds.
     */
    public void scheduleRefresh() {
        scheduleRefresh(false);
    }

    private void scheduleRefresh(boolean forceUpdate) {
        Intent refreshIntent = new Intent(this, RefreshService.class);
        boolean isRefreshScheduled = PendingIntent.getService(this, 0, refreshIntent, PendingIntent.FLAG_NO_CREATE) != null;
        if (isRefreshScheduled && !forceUpdate) {
            LOGGER.debug("Not scheduling refresh since one is already scheduled");
            return;
        }

        Calendar startTime = Calendar.getInstance();
        startTime.setTimeInMillis(System.currentTimeMillis());
        startTime.set(Calendar.HOUR_OF_DAY, 8);
        startTime.set(Calendar.MINUTE, 0);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        long interval = Long.parseLong(preferences.getString(Settings.REFRESH_FREQUENCY.first, Settings.REFRESH_FREQUENCY.second));

        LOGGER.info("Refresh scheduled for " + startTime + " with an interval of " + interval);

        PendingIntent pendingIntent = PendingIntent.getService(this, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                startTime.getTimeInMillis(),
                interval,
                pendingIntent);
    }

    private void monitorRefreshPreferenceChange() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (Settings.REFRESH_FREQUENCY.first.equals(key)) {
                    scheduleRefresh(false);
                }
            }
        });
    }

}
