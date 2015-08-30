package net.elprespufferfish.rssreader;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.StrictMode;
import android.webkit.WebView;

import com.google.common.eventbus.EventBus;

import java.util.Calendar;

public class RssReaderApplication extends Application {

    public static RssReaderApplication fromContext(Context context) {
        return (RssReaderApplication) context.getApplicationContext();
    }

    private EventBus eventBus;

    @Override
    public void onCreate() {
        super.onCreate();

        eventBus = new EventBus();

        Feeds.initialize(this);

        scheduleRefresh();

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    //.detectLeakedClosableObjects() // TODO - bug in okhttp exposes a leak in HttpResponseCachehttps://github.com/square/okhttp/issues/215
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public void scheduleRefresh() {
        Calendar startTime = Calendar.getInstance();
        startTime.setTimeInMillis(System.currentTimeMillis());
        startTime.set(Calendar.HOUR_OF_DAY, 8);

        Intent refreshIntent = new Intent(this, RefreshService.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, refreshIntent, 0);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                startTime.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent);
    }
}
