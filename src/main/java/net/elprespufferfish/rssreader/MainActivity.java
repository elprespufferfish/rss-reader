package net.elprespufferfish.rssreader;

import static android.widget.Toast.LENGTH_SHORT;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends FragmentActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        reloadPager();

        ListView drawer = (ListView) findViewById(R.id.left_drawer);
        drawer.setAdapter(new ArrayAdapter<String>(this, R.layout.drawer_item, new String[] { "Refresh" }));
        drawer.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                new RefreshTask(MainActivity.this).execute();
            }
        });
    }

    private void reloadPager() {
        ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawers();
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(new ArticlePagerAdapter(getSupportFragmentManager(), MainActivity.this));
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
