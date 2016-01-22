package net.elprespufferfish.rssreader.dagger;

import net.elprespufferfish.rssreader.ArticleFragment;
import net.elprespufferfish.rssreader.MainActivity;
import net.elprespufferfish.rssreader.RefreshService;
import net.elprespufferfish.rssreader.SearchResultsActivity;
import net.elprespufferfish.rssreader.backup.RssReaderBackupAgent;

import javax.inject.Singleton;

import dagger.Component;

/**
 * @author elprespufferfish
 */
@Singleton
@Component(modules = ApplicationModule.class)
public interface ApplicationComponent {

    void inject(ArticleFragment fragment);
    void inject(MainActivity activity);
    void inject(RefreshService service);
    void inject(RssReaderBackupAgent backupAgent);
    void inject(SearchResultsActivity activity);

}
