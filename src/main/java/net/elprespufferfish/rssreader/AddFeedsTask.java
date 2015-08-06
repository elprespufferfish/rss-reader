package net.elprespufferfish.rssreader;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

/**
 * Add multiple feeds in the background
 */
public class AddFeedsTask extends AsyncTask<Feed, Void, Void> {

    private final Context context;
    private Exception exception;

    public AddFeedsTask(Context context) {
        this.context = context;
    }

    @Override
    protected Void doInBackground(Feed... feeds) {
        if (feeds.length == 0)
            throw new IllegalArgumentException("Must be called with Feeds to add");

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
                    context,
                    context.getString(R.string.add_feed_failure, exception.getMessage()),
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(
                    context,
                    context.getString(R.string.feeds_added),
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

}
