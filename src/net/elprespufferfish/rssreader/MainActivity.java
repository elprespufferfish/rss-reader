package net.elprespufferfish.rssreader;

import static android.widget.Toast.LENGTH_SHORT;
import android.app.ProgressDialog;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
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

        final ProgressDialog dialog = ProgressDialog.show(this, "", getString(R.string.loading_articles), true);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
                SQLiteDatabase database = databaseHelper.getWritableDatabase();
                try {
                    Feeds.getInstance().refresh(database);
                } finally {
                    database.close();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
                viewPager.setAdapter(new ArticlePagerAdapter(MainActivity.this.getSupportFragmentManager(), MainActivity.this));

                dialog.dismiss();
            }

        }.execute();

        ListView drawer = (ListView) findViewById(R.id.left_drawer);
        drawer.setAdapter(new ArrayAdapter<String>(this, R.layout.drawer_item, new String[] { "Refresh" }));
        drawer.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... params) {
                        DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
                        SQLiteDatabase database = databaseHelper.getWritableDatabase();
                        return Feeds.getInstance().refresh(database);
                    }

                    @Override
                    protected void onPostExecute(Boolean wasRefreshStarted) {
                        if (!wasRefreshStarted) {
                            Toast.makeText(
                                    MainActivity.this,
                                    MainActivity.this.getString(R.string.refresh_already_started),
                                    LENGTH_SHORT)
                                    .show();
                        }
                    }
                }.execute();
            }
        });
    }
}
