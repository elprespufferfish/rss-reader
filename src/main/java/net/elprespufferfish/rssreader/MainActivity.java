package net.elprespufferfish.rssreader;

import static android.widget.Toast.LENGTH_SHORT;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Arrays;

public class MainActivity extends ActionBarActivity {

    private ActionBarDrawerToggle drawerToggle;
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
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                R.string.drawer_open,
                R.string.drawer_close);
        drawerLayout.setDrawerListener(drawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

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

    private void reloadPager() {
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(new ArticlePagerAdapter(getSupportFragmentManager(), MainActivity.this, shareActionProvider));
    }

    private class RefreshTask extends AsyncTask<Void, Void, Boolean> {

        private final ProgressDialog progressDialog;

        public RefreshTask(Context context) {
            this.progressDialog = new ProgressDialog(context);
            progressDialog.setMessage(context.getString(R.string.loading_articles));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
        }

        @Override
        protected void onPreExecute() {
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
            SQLiteDatabase database = databaseHelper.getWritableDatabase();
            try {
                return Feeds.getInstance().refresh(database);
            } finally {
                database.close();
            }
        }

        @Override
        protected void onPostExecute(Boolean wasRefreshStarted) {
            progressDialog.dismiss();
            if (!wasRefreshStarted) {
                Toast.makeText(
                        MainActivity.this,
                        MainActivity.this.getString(R.string.refresh_already_started),
                        LENGTH_SHORT)
                        .show();
            } else {
                MainActivity.this.reloadPager();
            }
        }
    }

    private class DrawerClickListener implements ListView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            switch (position) {
                case 0: { // TODO
                    // Refresh
                    new RefreshTask(MainActivity.this).execute();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unexpected menu item at position " + position);
            }
        }

    }
}
