package net.elprespufferfish.rssreader;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handle searching for a new feed to add
 */
class AddFeedTask extends AsyncTask<String, Void, List<Feed>> {

    private final MainActivity context;
    private ProgressDialog progressDialog;
    private Exception exception;

    public AddFeedTask(MainActivity context) {
        this.context = context;
    }

    @Override
    protected void onPreExecute() {
        this.progressDialog = ProgressDialog.show(context, null, context.getString(R.string.searching_for_feeds), true);
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
                        context,
                        context.getString(R.string.feed_already_present, exception.getMessage()),
                        Toast.LENGTH_SHORT)
                        .show();
            } else {
                Toast.makeText(
                        context,
                        context.getString(R.string.add_feed_failure, exception.getMessage()),
                        Toast.LENGTH_LONG)
                        .show();
            }
            return;
        }

        if (feeds.size() == 0) {
            // nothing autodiscovered
            Toast.makeText(
                    context,
                    R.string.no_feed_to_add,
                    Toast.LENGTH_LONG)
                    .show();
            ;
        } else if (feeds.size() == 1) {
            // single feed was added
            Feed feed = feeds.get(0);
            Toast.makeText(
                    context,
                    context.getString(R.string.feed_added, feed.getName()),
                    Toast.LENGTH_SHORT)
                    .show();
        } else {
            List<CharSequence> feedTitles = new ArrayList<>(feeds.size());
            for (Feed feed : feeds) {
                feedTitles.add(feed.getName());
            }

            final Set<Feed> feedsToAdd = new HashSet<>();
            new AlertDialog.Builder(context)
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
                            new AddFeedsTask(context).execute(feeds.toArray(new Feed[0]));
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
