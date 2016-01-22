package net.elprespufferfish.rssreader.util;

import android.app.Activity;

import net.elprespufferfish.rssreader.util.DefaultActivityLifecycleCallbacks;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keep track of whether or not the application is in the foreground.
 */
public class ForegroundStatus extends DefaultActivityLifecycleCallbacks {

    private static final AtomicInteger FOREGROUND_COUNT = new AtomicInteger(0);

    public static boolean isForeground() {
        return FOREGROUND_COUNT.get() > 0;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        FOREGROUND_COUNT.incrementAndGet();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        FOREGROUND_COUNT.decrementAndGet();
    }

}
