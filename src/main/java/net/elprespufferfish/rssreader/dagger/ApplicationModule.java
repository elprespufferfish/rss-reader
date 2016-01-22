package net.elprespufferfish.rssreader.dagger;

import android.database.sqlite.SQLiteDatabase;

import com.google.common.eventbus.EventBus;

import net.elprespufferfish.rssreader.DatabaseHelper;
import net.elprespufferfish.rssreader.FeedManager;
import net.elprespufferfish.rssreader.RssReaderApplication;

import javax.inject.Named;
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
    FeedManager feedManager() {
        return new FeedManager(application, databaseHelper());
    }

    @Provides
    @Singleton
    DatabaseHelper databaseHelper() {
        return new DatabaseHelper(application);
    }

}
