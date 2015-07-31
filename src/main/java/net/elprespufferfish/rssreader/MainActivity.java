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
import android.support.v4.app.FragmentStatePagerAdapter;
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

public class MainActivity extends AppCompatActivity {

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
                MainActivity.this.reloadPager();
            }
        }
    };

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private ProgressDialog refreshDialog;
    private ShareActionProvider shareActionProvider;

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

        reloadPager();
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
        Intent defaultShareIntent = new Intent(Intent.ACTION_SEND);
        defaultShareIntent.setType("text/plain");
        shareActionProvider.setShareIntent(defaultShareIntent);

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
                    reloadPager();
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

    private void reloadPager() {
        final ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        final FragmentStatePagerAdapter articlePagerAdapter = new ArticlePagerAdapter(getSupportFragmentManager(), MainActivity.this, shareActionProvider);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // no-op
            }

            @Override
            public void onPageSelected(int position) {
                setTitle(articlePagerAdapter, position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // no-op
            }
        });
        viewPager.setAdapter(articlePagerAdapter);
        if (articlePagerAdapter.getCount() != 0) {
            setTitle(articlePagerAdapter, 0);
        }
    }

    private void setTitle(FragmentStatePagerAdapter articlePagerAdapter, int position) {
        ArticleFragment articleFragment = (ArticleFragment) articlePagerAdapter.getItem(position);
        String feedTitle = articleFragment.getArguments().getString(ArticleFragment.FEED_KEY);
        MainActivity.this.getSupportActionBar().setTitle(feedTitle);
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
                default:
                    throw new IllegalArgumentException("Unexpected menu item at position " + position);
            }
        }

    }

    private class AddFeedTask extends AsyncTask<String, Void, String> {

        private final AlertDialog progressDialog;
        private Exception exception;

        public AddFeedTask() {
            this.progressDialog = new AlertDialog.Builder(MainActivity.this).create();
        }

        @Override
        protected void onPreExecute() {
            progressDialog.show();;
        }
        @Override
        protected String doInBackground(String... params) {
            if (params.length != 1) {
                throw new IllegalArgumentException("Expected single feedUrl parameter.  Received: " + params);
            }
            String feedUrl = params[0];
            try {
                String feedTitle = Feeds.getInstance().getFeedTitle(feedUrl);
                Feeds.getInstance().addFeed(feedTitle, feedUrl);
                return feedTitle;
            } catch (Exception e) {
                this.exception = e;
                return feedUrl;
            }
        }
        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();
            if (exception == null) {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
            } else if (exception instanceof FeedAlreadyAddedException) {
                Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.feed_already_present, result, exception.getMessage()), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, MainActivity.this.getString(R.string.add_feed_failure, result, exception.getMessage()), Toast.LENGTH_LONG).show();
            }
        }
    }
}
