package net.elprespufferfish.rssreader.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import org.slf4j.LoggerFactory;

/**
 * Logs activity lifecycle events.
 *
 * TODO - reduce to debug - https://github.com/elprespufferfish/rss-reader/issues/29
 *
 * @author elprespufferfish
 */
public class LoggingActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        LoggerFactory.getLogger(activity.getClass()).info("onActivityCreated");
    }

    @Override
    public void onActivityStarted(Activity activity) {
        LoggerFactory.getLogger(activity.getClass()).info("onActivityStarted");

    }

    @Override
    public void onActivityResumed(Activity activity) {
        LoggerFactory.getLogger(activity.getClass()).info("onActivityResumed");
    }

    @Override
    public void onActivityPaused(Activity activity) {
        LoggerFactory.getLogger(activity.getClass()).info("onActivityPaused");
    }

    @Override
    public void onActivityStopped(Activity activity) {
        LoggerFactory.getLogger(activity.getClass()).info("onActivityStopped");
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        LoggerFactory.getLogger(activity.getClass()).info("onActivitySaveInstanceState");
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        LoggerFactory.getLogger(activity.getClass()).info("onActivityDestroyed");
    }

}
