package net.elprespufferfish.rssreader;

import android.app.Activity;
import android.os.AsyncTask;
import android.support.design.widget.Snackbar;

/**
 * Add multiple feeds in the background.
 */
public class AddFeedsTask extends AsyncTask<Feed, Void, Void> {

    private final Activity activity;
    private Exception exception;

    public AddFeedsTask(Activity activity) {
        this.activity = activity;
    }

    @Override
    protected Void doInBackground(Feed... feeds) {
        if (feeds.length == 0) {
            throw new IllegalArgumentException("Must be called with Feeds to add");
        }

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
    protected void onPostExecute(Void unused) {
        if (exception != null) {
            Snackbar.make(
                    activity.findViewById(R.id.pager),
                    activity.getString(R.string.add_feed_failure, exception.getMessage()),
                    Snackbar.LENGTH_LONG
            ).show();
        } else {
            Snackbar.make(
                    activity.findViewById(R.id.pager),
                    R.string.feeds_added,
                    Snackbar.LENGTH_SHORT
            ).show();
        }
    }

}
