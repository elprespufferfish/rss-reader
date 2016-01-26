package net.elprespufferfish.rssreader.dagger;

import com.google.common.eventbus.EventBus;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import net.elprespufferfish.rssreader.RssReaderApplication;
import net.elprespufferfish.rssreader.db.DatabaseHelper;
import net.elprespufferfish.rssreader.db.FeedManager;
import net.elprespufferfish.rssreader.net.FeedFetcher;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * @author elprespufferfish
 */
@Module
public class ApplicationModule {

    private final RssReaderApplication application;

    public ApplicationModule(RssReaderApplication application) {
        this.application = application;
    }

    @Provides
    @Singleton
    EventBus eventBus() {
        return new EventBus();
    }

    @Provides
    @Singleton
    SharedPreferences sharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    @Provides
    @Singleton
    FeedManager feedManager() {
        return new FeedManager(application, databaseHelper(), sharedPreferences());
    }

    @Provides
    @Singleton
    FeedFetcher feedFetcher() {
        return new FeedFetcher(feedManager(), sharedPreferences());
    }

    @Provides
    @Singleton
    DatabaseHelper databaseHelper() {
        return new DatabaseHelper(application);
    }

}
