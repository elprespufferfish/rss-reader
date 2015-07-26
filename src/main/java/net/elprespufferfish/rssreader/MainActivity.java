package net.elprespufferfish.rssreader;

import static android.widget.Toast.LENGTH_SHORT;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {

    private ShareActionProvider shareActionProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        reloadPager();
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
        switch (item.getItemId()) {
            case R.id.action_refresh:
                new RefreshTask(MainActivity.this).execute();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
}
