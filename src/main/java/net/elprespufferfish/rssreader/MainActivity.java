package net.elprespufferfish.rssreader;

import static butterknife.ButterKnife.findById;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import net.elprespufferfish.rssreader.db.DatabaseHelper;
import net.elprespufferfish.rssreader.refresh.RefreshService;
import net.elprespufferfish.rssreader.search.SearchResultsActivity;
import net.elprespufferfish.rssreader.settings.SettingsActivity;
import net.elprespufferfish.rssreader.util.ToggleableShareActionProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);
    private static final String GLOBAL_PREFS = "global";
    private static final String IS_HIDING_READ_ARTICLES_PREF = "isHidingReadArticles";
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");

    private BroadcastReceiver refreshCompletionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDialog.dismiss();

            boolean didRefreshComplete = intent.getBooleanExtra(RefreshService.DID_REFRESH_COMPLETE, Boolean.FALSE);
            if (!didRefreshComplete) {
                Snackbar.make(
                        viewPager,
                        MainActivity.this.getString(R.string.refresh_failed),
                        Snackbar.LENGTH_LONG
                ).show();
                return;
            }

            boolean wasRefreshStarted = intent.getBooleanExtra(RefreshService.WAS_REFRESH_STARTED, Boolean.FALSE);
            if (!wasRefreshStarted) {
                Snackbar.make(
                        viewPager,
                        R.string.refresh_already_started,
                        Snackbar.LENGTH_SHORT
                ).show();
            } else {
                reloadPager(nullFeed);
            }
        }
    };

    @Inject
    EventBus eventBus;
    @Inject
    FeedManager feedManager;
    @Inject
    DatabaseHelper databaseHelper;
    @Bind(R.id.drawer_layout)
    DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    @Bind(R.id.pager)
    ViewPager viewPager;
    @Bind(R.id.next)
    Button nextButton;
    private ArticlePagerAdapter articlePagerAdapter;
    private ProgressDialog refreshDialog;
    private ToggleableShareActionProvider shareActionProvider;
    private Feed nullFeed; // sentinel Feed to select 'all' feeds
    private Feed currentFeed;
    private boolean isHidingReadArticles = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        ButterKnife.bind(this);
        RssReaderApplication.fromContext(this).getApplicationComponent().inject(this);

        eventBus.register(this);

        // set up left drawer
        NavigationView navigationView = findById(this, R.id.left_drawer);
        navigationView.setNavigationItemSelectedListener(new NavigationClickListener());
        isHidingReadArticles = getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE).getBoolean(IS_HIDING_READ_ARTICLES_PREF, false);
        navigationView.getMenu().findItem(R.id.drawer_toggle_unread).setTitle(isHidingReadArticles ? R.string.show_unread_articles : R.string.hide_unread_articles);
        navigationView.getMenu().findItem(R.id.drawer_toggle_unread).setIcon(isHidingReadArticles ? R.drawable.ic_visibility_black_24dp : R.drawable.ic_visibility_off_black_24dp);

        // tie drawer to action bar
        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                R.string.drawer_open,
                R.string.drawer_close);
        drawerLayout.setDrawerListener(drawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            }
        });

        this.refreshDialog = new ProgressDialog(this);
        refreshDialog.setMessage(getString(R.string.loading_articles));
        refreshDialog.setIndeterminate(true);
        refreshDialog.setCancelable(false);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            if ("text/plain".equals(intent.getType())) {
                String address = intent.getStringExtra(Intent.EXTRA_TEXT);
                new AddFeedTask(this, feedManager).execute(address);
            }
        }

        this.shareActionProvider = new ToggleableShareActionProvider(this);

        nullFeed = Feed.nullFeed(this);
        reloadPager(nullFeed);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.search));
        ComponentName searchResultsComponent = new ComponentName(this, SearchResultsActivity.class);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(searchResultsComponent));

        MenuItem shareItem = menu.findItem(R.id.action_share);
        MenuItemCompat.setActionProvider(shareItem, shareActionProvider);
        if (this.articlePagerAdapter.getCount() == 0) {
            shareItem.setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            // handled by action bar icon
            return true;
        }

        // NOTE: share action is handled by the ShareActionProvider

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // prevent interactions if a refresh is in progress
        // or reload if one just finished
        Intent refreshIntent = new Intent(MainActivity.this, RefreshService.class);
        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                RefreshService.RefreshServiceBinder binder = (RefreshService.RefreshServiceBinder) service;
                if (binder.isRefreshInProgress()) {
                    MainActivity.this.refreshDialog.show();
                } else if (MainActivity.this.refreshDialog.isShowing()) {
                    // refresh completed while UI was in the background
                    MainActivity.this.refreshDialog.dismiss();
                    reloadPager(nullFeed);
                }
                MainActivity.this.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // no-op
            }
        };
        bindService(refreshIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(refreshCompletionReceiver, new IntentFilter(RefreshService.COMPLETION_NOTIFICATION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshCompletionReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        eventBus.unregister(this);
    }

    private void reloadPager(Feed feed) {
        // clean up previous feed
        feedManager.finalizeGreyArticles();

        // switch to new feed
        currentFeed = feed;

        viewPager.clearOnPageChangeListeners();

        articlePagerAdapter = new ArticlePagerAdapter(getSupportFragmentManager(), databaseHelper, feed.getUrl(), isHidingReadArticles);
        final ViewPager.OnPageChangeListener pageChangeListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // no-op
            }

            @Override
            public void onPageSelected(int position) {
                int numArticles = articlePagerAdapter.getCount()
                        - 1; // subtract 1 for the 'no more articles' page
                if (position < numArticles) {
                    LOGGER.debug("Article " + (position+1) + "/" + numArticles + " loaded");
                    nextButton.setVisibility(View.VISIBLE);
                } else {
                    nextButton.setVisibility(View.GONE);
                }

                if (articlePagerAdapter.getItem(position) instanceof EndOfLineFragment) {
                    getSupportActionBar().setTitle(R.string.app_name);
                    shareActionProvider.hide();
                    return;
                }

                shareActionProvider.show();
                MainActivity.this.eventBus.post(new ArticleSelectedEvent(position));
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // no-op
            }
        };
        viewPager.addOnPageChangeListener(pageChangeListener);
        viewPager.setAdapter(articlePagerAdapter);
        // first page must be triggered manually
        viewPager.post(new Runnable() {
            @Override
            public void run() {
                pageChangeListener.onPageSelected(viewPager.getCurrentItem());
            }
        });

        if (articlePagerAdapter.getCount() == 0) {
            MainActivity.this.getSupportActionBar().setTitle(R.string.app_name);
        }

        // rebuild menu to enable/disable the share button
        invalidateOptionsMenu();
    }

    /**
     * Handle a new Article being displayed.
     */
    @Subscribe
    public void onArticleLoaded(ArticleLoadedEvent articleLoadedEvent) {
        if (articleLoadedEvent.getArticleIndex() != viewPager.getCurrentItem()) {
            return;
        }

        final Article article = articleLoadedEvent.getArticle();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                feedManager.markArticleGrey(article);
            }
        });


        MainActivity.this.getSupportActionBar().setTitle(article.getFeed());

        String textToShare = article.getTitle() + "\n\n" + article.getLink();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, article.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, textToShare);
        intent.setType("text/plain");

        shareActionProvider.setShareIntent(intent);
    }

    public void refresh() {
        refreshDialog.show();
        Intent refreshIntent = new Intent(this, RefreshService.class);
        refreshIntent.putExtra(RefreshService.FORCE_REFRESH, Boolean.TRUE);
        startService(refreshIntent);
    }

    private class NavigationClickListener implements NavigationView.OnNavigationItemSelectedListener {

        @SuppressLint("InflateParams")
        @Override
        public boolean onNavigationItemSelected(MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.drawer_refresh: {
                    refresh();
                    drawerLayout.closeDrawers();
                    break;
                }
                case R.id.drawer_add_feed: {
                    final View addFeedDialogView = getLayoutInflater().inflate(R.layout.add_feed_dialog, null);
                    final AlertDialog addFeedDialog = new AlertDialog.Builder(MainActivity.this)
                            .setView(addFeedDialogView)
                            .setTitle(R.string.add_feed_title)
                            .setPositiveButton(R.string.add_feed_ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();;
                                    EditText feedUrlInput = findById(addFeedDialogView, R.id.feed_url);
                                    String feedUrl = feedUrlInput.getText().toString();

                                    // default protocol to https
                                    if (!URL_PATTERN.matcher(feedUrl).matches() ) {
                                        feedUrl = "https://" + feedUrl;
                                    }

                                    new AddFeedTask(MainActivity.this, feedManager).execute(feedUrl);
                                }
                            })
                            .setNegativeButton(R.string.add_feed_cancel, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            })
                            .create();
                    addFeedDialog.show();
                    break;
                }
                case R.id.drawer_view_feed: {
                    Map<Feed, Integer> feeds = feedManager.getUnreadArticleCounts();

                    final Map<Feed, Integer> allFeeds = new LinkedHashMap<>();
                    int totalUnread = 0;
                    for (Integer numUnread : feeds.values()) {
                        totalUnread += numUnread;
                    }
                    allFeeds.put(nullFeed, totalUnread);
                    allFeeds.putAll(feeds);

                    int currentFeedIndex = 0;
                    int index = 0;
                    List<String> feedNames = new ArrayList<>(allFeeds.size());
                    for (Map.Entry<Feed, Integer> entry : allFeeds.entrySet()) {
                        Feed feed = entry.getKey();
                        feedNames.add(feed.getName() + " (" + entry.getValue() + ")");

                        if (feed.equals(currentFeed)) {
                            currentFeedIndex = index;
                        }
                        index++;
                    }

                    AlertDialog viewFeedDialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.view_feed_title)
                            .setSingleChoiceItems(feedNames.toArray(new String[0]), currentFeedIndex, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

                                    Feed selectedFeed = null;
                                    int index = 0;
                                    for (Feed feed : allFeeds.keySet()) {
                                        if (index++ == selectedPosition) {
                                            selectedFeed = feed;
                                            break;
                                        }
                                    }
                                    if (selectedFeed == null) {
                                        throw new AssertionError("Could not determine selected feed at position " + selectedPosition + " from " + allFeeds);
                                    }

                                    drawerLayout.closeDrawers();
                                    reloadPager(selectedFeed);
                                }
                            })
                            .create();
                    viewFeedDialog.show();
                    break;
                }
                case R.id.drawer_remove_feed: {
                    final List<Feed> feeds = feedManager.getAllFeeds();

                    List<String> feedNames = new ArrayList<>(feeds.size());
                    for (Feed feed : feeds) {
                        feedNames.add(feed.getName());
                    }
                    AlertDialog viewFeedDialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.remove_feed_title)
                            .setSingleChoiceItems(feedNames.toArray(new String[0]), -1, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // no-op
                                }
                            })
                            .setPositiveButton(R.string.remove_feed_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                                    Feed feedToRemove = feeds.get(selectedPosition);
                                    feedManager.removeFeed(feedToRemove);
                                    Snackbar.make(
                                            viewPager,
                                            getString(R.string.remove_feed_complete, feedToRemove.getName()),
                                            Snackbar.LENGTH_LONG
                                    ).show();
                                    drawerLayout.closeDrawers();

                                    if (currentFeed.equals(feedToRemove)) {
                                        reloadPager(nullFeed);
                                    } else {
                                        reloadPager(currentFeed);
                                    }
                                }
                            })
                            .setNegativeButton(R.string.remove_feed_cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            })
                            .create();
                    viewFeedDialog.show();
                    break;
                }
                case R.id.drawer_mark_all_read: {
                    feedManager.markAllAsRead(currentFeed);
                    drawerLayout.closeDrawers();
                    break;
                }
                case R.id.drawer_toggle_unread: {
                    isHidingReadArticles = !isHidingReadArticles;

                    SharedPreferences.Editor editor = getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE).edit();
                    editor.putBoolean(IS_HIDING_READ_ARTICLES_PREF, isHidingReadArticles);
                    editor.apply();

                    menuItem.setTitle(isHidingReadArticles ? R.string.show_unread_articles : R.string.hide_unread_articles);
                    menuItem.setIcon(isHidingReadArticles ? R.drawable.ic_visibility_black_24dp : R.drawable.ic_visibility_off_black_24dp);
                    reloadPager(currentFeed);

                    drawerLayout.closeDrawers();
                    break;
                }
                case R.id.drawer_open_settings: {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    drawerLayout.closeDrawers();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unexpected menu item: " + menuItem);
            }
            return true;
        }

    }

}
