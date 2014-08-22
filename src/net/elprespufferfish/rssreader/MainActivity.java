package net.elprespufferfish.rssreader;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ProgressDialog;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;

public class MainActivity extends FragmentActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final ProgressDialog dialog = ProgressDialog.show(this, "", "Loading new articles...", true);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                LOGGER.info("Parsing feeds in the background");

                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

                DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
                final SQLiteDatabase database = databaseHelper.getWritableDatabase();

                Set<AsyncTask<String, Void, Void>> tasks = new HashSet<AsyncTask<String, Void, Void>>();
                for (final String feed : Feeds.getFeeds(MainActivity.this)) {
                    AsyncTask<String, Void, Void> articleFetchingTask = new AsyncTask<String, Void, Void>() {
                        @Override
                        protected Void doInBackground(String... feeds) {
                            String feedAddress = feeds[0];
                            try {
                                Feeds.parseFeed(MainActivity.this, database, feedAddress);
                            } catch (Exception e) {
                                LOGGER.error("Could not parse feed " + feedAddress, e);
                            }
                            return null;
                        }
                    }.executeOnExecutor(executor, feed);
                    tasks.add(articleFetchingTask);
                }

                for (AsyncTask<String, Void, Void> task : tasks) {
                    try {
                        task.get();
                    } catch (Exception e) {
                        LOGGER.error("Could not get articles", e);
                    }
                }

                database.close();
                LOGGER.info("Done parsing feeds in the background");

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
