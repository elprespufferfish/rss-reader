package net.elprespufferfish.rssreader;

import android.app.Application;
import android.os.Build;
import android.os.StrictMode;
import android.webkit.WebView;

public class RssReaderApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Feeds.initialize();

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
    }
}
