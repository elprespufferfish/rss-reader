package net.elprespufferfish.rssreader;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);

    private BroadcastReceiver refreshCompletionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDialog.dismiss();

            boolean didRefreshComplete = intent.getBooleanExtra(RefreshService.DID_REFRESH_COMPLETE, Boolean.FALSE);
            if (!didRefreshComplete) {
                Toast.makeText(
                        MainActivity.this,
                        MainActivity.this.getString(R.string.refresh_failed),
                        LENGTH_LONG)
                        .show();
                return;
            }

            boolean wasRefreshStarted = intent.getBooleanExtra(RefreshService.WAS_REFRESH_STARTED, Boolean.FALSE);
            if (!wasRefreshStarted) {
                Toast.makeText(
                        MainActivity.this,
                        MainActivity.this.getString(R.string.refresh_already_started),
                        LENGTH_SHORT)
                        .show();
            } else {
                Toast.makeText(
                        MainActivity.this,
                        MainActivity.this.getString(R.string.refresh_complete),
                        LENGTH_SHORT)
                        .show();
                MainActivity.this.reloadPager(nullFeed);
            }
        }
    };

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private ViewPager viewPager;
    private ArticlePagerAdapter articlePagerAdapter;
    private ProgressDialog refreshDialog;
    private ShareActionProvider shareActionProvider;
    private Feed nullFeed; // sentinel Feed to select 'all' feeds
    private Feed currentFeed;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // set up left drawer
        ListView drawerList = (ListView) findViewById(R.id.left_drawer);
        drawerList.setAdapter(new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.drawer_items)));
        drawerList.setOnItemClickListener(new DrawerClickListener());

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
                new AddFeedTask().execute(address);
            }
        }

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
        getMenuInflater().inflate(R.layout.action_bar_menu, menu);

        MenuItem shareItem = menu.findItem(R.id.action_share);
        shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);
        if (articlePagerAdapter.getCount() != 0) {
            ArticleFragment articleFragment = (ArticleFragment) articlePagerAdapter.getItem(viewPager.getCurrentItem());
            updateShareAction(articleFragment);
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
        this.articlePagerAdapter.close();
    }

    private void reloadPager(Feed feed) {
        currentFeed = feed;
        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.clearOnPageChangeListeners();
        if (articlePagerAdapter != null) articlePagerAdapter.close();
        articlePagerAdapter = new ArticlePagerAdapter(getSupportFragmentManager(), MainActivity.this, feed.getUrl());
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // no-op
            }

            @Override
            public void onPageSelected(int position) {
                ArticleFragment articleFragment = (ArticleFragment) articlePagerAdapter.getItem(position);
                setTitle(articleFragment);
                updateShareAction(articleFragment);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // no-op
            }
        });
        viewPager.addOnPageChangeListener(new ArticleReadListener(articlePagerAdapter));
        viewPager.setAdapter(articlePagerAdapter);
        if (articlePagerAdapter.getCount() != 0) {
            ArticleFragment articleFragment = (ArticleFragment) articlePagerAdapter.getItem(0);
            setTitle(articleFragment);
        }
    }

    private void setTitle(ArticleFragment articleFragment) {
        Article article = articleFragment.getArguments().getParcelable(ArticleFragment.ARTICLE_KEY);
        MainActivity.this.getSupportActionBar().setTitle(article.getFeed());
    }

    private void updateShareAction(ArticleFragment articleFragment) {
        Bundle arguments = articleFragment.getArguments();
        Article article = arguments.getParcelable(ArticleFragment.ARTICLE_KEY);

        String textToShare = article.getTitle() + "\n\n" + article.getLink();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, article.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, textToShare);
        intent.setType("text/plain");

        shareActionProvider.setShareIntent(intent);
    }

    private class DrawerClickListener implements ListView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            switch (position) {
                case 0: { // TODO
                    // Refresh
                    refreshDialog.show();
                    Intent refreshIntent = new Intent(MainActivity.this, RefreshService.class);
                    refreshIntent.putExtra(RefreshService.FORCE_REFRESH, Boolean.TRUE);
                    MainActivity.this.startService(refreshIntent);
                    drawerLayout.closeDrawers();
                    break;
                }
                case 1: {
                    // Add Feed
                    final View addFeedDialogView = getLayoutInflater().inflate(R.layout.add_feed_dialog, null);
                    AlertDialog addFeedDialog = new AlertDialog.Builder(MainActivity.this)
                            .setView(addFeedDialogView)
                            .setTitle(R.string.add_feed_title)
                            .setPositiveButton(R.string.add_feed_ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();;
                                    EditText feedUrlInput = (EditText) addFeedDialogView.findViewById(R.id.feed_url);
                                    String feedUrl = feedUrlInput.getText().toString();
                                    new AddFeedTask().execute(feedUrl);
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
                case 2: {
                    // View Feed
                    Map<Feed, Integer> feeds = Feeds.getInstance().getFeedsWithContent();

                    int totalUnread = 0;
                    final Map<Feed, Integer> allFeeds = new LinkedHashMap<>();
                    for (Integer numUnread : allFeeds.values()) {
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
                case 3: {
                    // Remove Feed
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
                                    Toast.makeText(
                                            MainActivity.this,
                                            MainActivity.this.getString(R.string.remove_feed_complete, feedToRemove.getName()),
                                            Toast.LENGTH_LONG)
                                            .show();
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
                default:
                    throw new IllegalArgumentException("Unexpected menu item at position " + position);
            }
        }

    }

    private class AddFeedTask extends AsyncTask<String, Void, List<Feed>> {

        private ProgressDialog progressDialog;
        private Exception exception;

        @Override
        protected void onPreExecute() {
            this.progressDialog = ProgressDialog.show(MainActivity.this, null, MainActivity.this.getString(R.string.searching_for_feeds), true);
        }

        @Override
        protected List<Feed> doInBackground(String... params) {
            if (params.length != 1) {
                throw new IllegalArgumentException("Expected single feedUrl parameter.  Received: " + Arrays.toString(params));
            }
            String feedUrl = params[0];
            try {
                List<Feed> feeds = Feeds.getInstance().getFeeds(feedUrl);
                if (feeds.size() == 1) {
                    // only one feed, just add it
                    Feed feed = feeds.get(0);
                    Feeds.getInstance().addFeed(feed);
                }
                return feeds;
            } catch (Exception e) {
                this.exception = e;
                return null;
            }
        }
        @Override
        protected void onPostExecute(final List<Feed> feeds) {
            progressDialog.dismiss();

            if (exception != null) {
                // it dun broke
                if (exception instanceof FeedAlreadyAddedException) {
                    Toast.makeText(
                            MainActivity.this,
                            MainActivity.this.getString(R.string.feed_already_present, exception.getMessage()),
                            Toast.LENGTH_SHORT)
                            .show();
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            MainActivity.this.getString(R.string.add_feed_failure, exception.getMessage()),
                            Toast.LENGTH_LONG)
                            .show();
                }
                return;
            }

            if (feeds.size() == 0) {
                // nothing autodiscovered
                Toast.makeText(
                        MainActivity.this,
                        R.string.no_feed_to_add,
                        Toast.LENGTH_LONG)
                        .show();;
            } else if (feeds.size() == 1) {
                // single feed was added
                Feed feed = feeds.get(0);
                Toast.makeText(
                        MainActivity.this,
                        MainActivity.this.getString(R.string.feed_added, feed.getName()),
                        Toast.LENGTH_SHORT)
                        .show();
            } else {
                List<CharSequence> feedTitles = new ArrayList<>(feeds.size());
                for (Feed feed : feeds) {
                    feedTitles.add(feed.getName());
                }

                final Set<Feed> feedsToAdd = new HashSet<>();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.add_feeds_title)
                        .setMultiChoiceItems(feedTitles.toArray(new CharSequence[0]), null, new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                if (isChecked) {
                                    feedsToAdd.add(feeds.get(which));
                                } else {
                                    feedsToAdd.remove(feeds.get(which));
                                }
                            }
                        })
                        .setPositiveButton(R.string.add_feeds_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                new AddFeedsTask().execute(feeds.toArray(new Feed[0]));
                            }
                        })
                        .setNegativeButton(R.string.add_feeds_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                        .show();
            }
        }
    }

    private class AddFeedsTask extends AsyncTask<Feed, Void, Void> {

        private Exception exception;

        @Override
        protected Void doInBackground(Feed... feeds) {
            if (feeds.length == 0) throw new IllegalArgumentException("Must be called with Feeds to add");

            for (Feed feed : feeds) {
                try {
                    Feeds.getInstance().addFeed(feed);
                } catch (FeedAlreadyAddedException ignored) {
                    // ignore
                } catch (Exception e) {
                    this.exception = e;
                    return null;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (exception != null) {
                Toast.makeText(
                        MainActivity.this,
                        MainActivity.this.getString(R.string.add_feed_failure, exception.getMessage()),
                        Toast.LENGTH_LONG)
                        .show();
            } else {
                Toast.makeText(
                        MainActivity.this,
                        MainActivity.this.getString(R.string.feeds_added),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }

    }

}
