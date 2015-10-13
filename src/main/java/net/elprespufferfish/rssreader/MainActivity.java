package net.elprespufferfish.rssreader;

import android.app.ProgressDialog;
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
import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);
    private static final String GLOBAL_PREFS = "global";
    private static final String IS_HIDING_READ_ARTICLES_PREF = "isHidingReadArticles";

    private BroadcastReceiver refreshCompletionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDialog.dismiss();

            boolean didRefreshComplete = intent.getBooleanExtra(RefreshService.DID_REFRESH_COMPLETE, Boolean.FALSE);
            if (!didRefreshComplete) {
                Snackbar.make(
                        MainActivity.this.findViewById(R.id.pager),
                        MainActivity.this.getString(R.string.refresh_failed),
                        Snackbar.LENGTH_LONG
                ).show();
                return;
            }

            boolean wasRefreshStarted = intent.getBooleanExtra(RefreshService.WAS_REFRESH_STARTED, Boolean.FALSE);
            if (!wasRefreshStarted) {
                Snackbar.make(
                        MainActivity.this.findViewById(R.id.pager),
                        R.string.refresh_already_started,
                        Snackbar.LENGTH_SHORT
                ).show();
            } else {
                Snackbar.make(
                        MainActivity.this.findViewById(R.id.pager),
                        R.string.refresh_complete,
                        Snackbar.LENGTH_SHORT
                ).show();
                MainActivity.this.reloadPager(nullFeed);
            }
        }
    };

    private EventBus eventBus;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private ViewPager viewPager;
    private ArticlePagerAdapter articlePagerAdapter;
    private ProgressDialog refreshDialog;
    private ShareActionProvider shareActionProvider;
    private Feed nullFeed; // sentinel Feed to select 'all' feeds
    private Feed currentFeed;
    private boolean isHidingReadArticles = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        eventBus = RssReaderApplication.fromContext(this).getEventBus();
        eventBus.register(this);

        // set up left drawer
        NavigationView navigationView = (NavigationView) findViewById(R.id.left_drawer);
        navigationView.setNavigationItemSelectedListener(new NavigationClickListener());
        isHidingReadArticles = getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE).getBoolean(IS_HIDING_READ_ARTICLES_PREF, false);
        navigationView.getMenu().findItem(R.id.drawer_toggle_unread).setTitle(isHidingReadArticles ? R.string.show_unread_articles : R.string.hide_unread_articles);
        navigationView.getMenu().findItem(R.id.drawer_toggle_unread).setIcon(isHidingReadArticles ? R.drawable.ic_visibility_black_24dp : R.drawable.ic_visibility_off_black_24dp);

        // tie drawer to action bar
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                R.string.drawer_open,
                R.string.drawer_close);
        drawerLayout.setDrawerListener(drawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        this.refreshDialog = new ProgressDialog(this);
        refreshDialog.setMessage(getString(R.string.loading_articles));
        refreshDialog.setIndeterminate(true);
        refreshDialog.setCancelable(false);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            if ("text/plain".equals(intent.getType())) {
                String address = intent.getStringExtra(Intent.EXTRA_TEXT);
                new AddFeedTask(this).execute(address);
            }
        }

        this.shareActionProvider = new ShareActionProvider(this);

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

        MenuItem shareItem = menu.findItem(R.id.action_share);
        MenuItemCompat.setActionProvider(shareItem, shareActionProvider);

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

    /**
     * Don't finish() when we hit back
     */
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.articlePagerAdapter.close();
        eventBus.unregister(this);
    }

    private void reloadPager(Feed feed) {
        // clean up previous feed
        Feeds.getInstance().finalizeGreyArticles();

        // switch to new feed
        currentFeed = feed;

        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.clearOnPageChangeListeners();
        if (articlePagerAdapter != null) articlePagerAdapter.close();

        articlePagerAdapter = new ArticlePagerAdapter(getSupportFragmentManager(), MainActivity.this, feed.getUrl(), isHidingReadArticles);
        final ViewPager.OnPageChangeListener pageChangeListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // no-op
            }

            @Override
            public void onPageSelected(int position) {
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
    }

    @Subscribe
    public void onArticleLoaded(ArticleLoadedEvent e) {
        if (e.getArticleIndex() != viewPager.getCurrentItem()) {
            return;
        }

        final Article article = e.getArticle();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Feeds.getInstance().markArticleGrey(article);
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

    private class NavigationClickListener implements NavigationView.OnNavigationItemSelectedListener {

        @Override
        public boolean onNavigationItemSelected(MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.drawer_refresh: {
                    refreshDialog.show();
                    Intent refreshIntent = new Intent(MainActivity.this, RefreshService.class);
                    refreshIntent.putExtra(RefreshService.FORCE_REFRESH, Boolean.TRUE);
                    MainActivity.this.startService(refreshIntent);
                    drawerLayout.closeDrawers();
                    break;
                }
                case R.id.drawer_add_feed: {
                    final View addFeedDialogView = getLayoutInflater().inflate(R.layout.add_feed_dialog, null);
                    AlertDialog addFeedDialog = new AlertDialog.Builder(MainActivity.this)
                            .setView(addFeedDialogView)
                            .setTitle(R.string.add_feed_title)
                            .setPositiveButton(R.string.add_feed_ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();;
                                    EditText feedUrlInput = (EditText) addFeedDialogView.findViewById(R.id.feed_url);
                                    String feedUrl = feedUrlInput.getText().toString();
                                    new AddFeedTask(MainActivity.this).execute(feedUrl);
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
                    Map<Feed, Integer> feeds = Feeds.getInstance().getFeedsWithContent();

                    final Map<Feed, Integer> allFeeds = new LinkedHashMap<>();
                    int totalUnread = 0;
                    for (Integer numUnread : feeds.values()) {
                        totalUnread += numUnread;
                    }
                    allFeeds.put(nullFeed, totalUnread);
                    allFeeds.putAll(feeds);

                    int currentFeedIndex = 0;
                    int i = 0;
                    List<String> feedNames = new ArrayList<>(allFeeds.size());
                    for (Map.Entry<Feed, Integer> entry : allFeeds.entrySet()) {
                        Feed feed = entry.getKey();
                        feedNames.add(feed.getName() + " (" + entry.getValue() + ")");

                        if (feed.equals(currentFeed)) {
                            currentFeedIndex = i;
                        }
                        i++;
                    }

                    AlertDialog viewFeedDialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.view_feed_title)
                            .setSingleChoiceItems(feedNames.toArray(new String[0]), currentFeedIndex, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

                                    Feed selectedFeed = null;
                                    int i = 0;
                                    for (Feed feed : allFeeds.keySet()) {
                                        if (i++ == selectedPosition) {
                                            selectedFeed = feed;
                                            break;
                                        }
                                    }
                                    if (selectedFeed == null) throw new AssertionError("Could not determine selected feed at position " + selectedPosition + " from " + allFeeds);

                                    drawerLayout.closeDrawers();
                                    reloadPager(selectedFeed);
                                }
                            })
                            .create();
                    viewFeedDialog.show();
                    break;
                }
                case R.id.drawer_remove_feed: {
                    final List<Feed> feeds = Feeds.getInstance().getFeeds();

                    List<String> feedNames = new ArrayList<>(feeds.size());
                    for (Feed feed : feeds) feedNames.add(feed.getName());
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
                                    Feeds.getInstance().removeFeed(feedToRemove);
                                    Snackbar.make(
                                            findViewById(R.id.pager),
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
                    Feeds.getInstance().markAllAsRead(currentFeed);
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
                default:
                    throw new IllegalArgumentException("Unexpected menu item: " + menuItem);
            }
            return true;
        }

    }

}
