package net.elprespufferfish.rssreader;

import android.app.Application;

public class RssReaderApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Feeds.initialize();
    }
}
