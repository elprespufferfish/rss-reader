package net.elprespufferfish.rssreader;

import android.app.ProgressDialog;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;

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
                    Feeds.refresh(database);
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
    }
}
