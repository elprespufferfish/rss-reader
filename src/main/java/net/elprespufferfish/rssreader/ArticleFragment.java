package net.elprespufferfish.rssreader;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.squareup.picasso.Picasso;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArticleFragment extends Fragment {

    public static final String ARTICLE_FEED_URL_KEY = "article_feed_url";
    public static final String ARTICLE_INDEX_KEY = "article_index";

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleFragment.class);
    private static final String ARTICLE_KEY = "article";

    private EventBus eventBus;
    private View view;
    private int articleIndex;
    private int lastSelected = -1;
    private Article article;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        eventBus = RssReaderApplication.fromContext(getActivity()).getEventBus();
        eventBus.register(this);

        articleIndex = getArguments().getInt(ARTICLE_INDEX_KEY);

        article = (savedInstanceState != null) ? (Article) savedInstanceState.getParcelable(ARTICLE_KEY) : null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.article_fragment, container, false);
        if (article != null) {
            onArticleLoad(article);
        } else {
            String feedUrl = getArguments().getString(ARTICLE_FEED_URL_KEY);
            new FetchArticleTask(feedUrl, articleIndex).execute();

        }
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(ARTICLE_KEY, article);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        eventBus.unregister(this);
    }

    @Subscribe
    public void onSelected(ArticleSelectedEvent e) {
        lastSelected = e.getIndex();
        if (e.getIndex() == articleIndex && article != null) {
            eventBus.post(new ArticleLoadedEvent(articleIndex, article));
        }
    }

    /**
     * Article loaded from disk.  If this is the currently visible article, post update so
     * the UI can update.
     */
    private void onArticleLoad(final Article article) {
        this.article = article;

        if (lastSelected == articleIndex) {
            eventBus.post(new ArticleLoadedEvent(articleIndex, article));
        }

        TextView titleView = ((TextView) view.findViewById(R.id.title));
        titleView.setText(article.getTitle());
        titleView.setClickable(true);
        titleView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(article.getLink()));
                ArticleFragment.this.startActivity(intent);
            }
        });

        String imageUrl = article.getImageUrl();
        ImageView imageView = (ImageView) view.findViewById(R.id.image);
        WebView webView = (WebView) view.findViewById(R.id.description);
        if (imageUrl != null) {
            LOGGER.debug("Displaying ImageView");
            imageView.setVisibility(ImageView.VISIBLE);
            webView.setVisibility(WebView.GONE);

            Picasso.with(this.getActivity())
                    .load(imageUrl)
                    .fit()
                    .centerInside()
                    .into(imageView);
        } else {
            LOGGER.debug("Displaying WebView");
            imageView.setVisibility(ImageView.GONE);
            webView.setVisibility(WebView.VISIBLE);

            final String description = article.getDescription();
            webView.setWebChromeClient(new WebChromeClient()); // HTML5 video requires a WebChromeClient
            enableJavaScript(webView);
            webView.loadData(description, "text/html", null);
        }
    }

    /* JavaScript is required to play youtube videos */
    @SuppressLint("SetJavaScriptEnabled")
    private void enableJavaScript(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
    }

    private class FetchArticleTask extends AsyncTask<Void, Void, Article> {

        private final SQLiteDatabase database;
        private final String feedUrl;
        private final int i;

        public FetchArticleTask(String feedUrl, int i) {
            DatabaseHelper databaseHelper = new DatabaseHelper(getActivity());
            this.database = databaseHelper.getReadableDatabase();
            this.feedUrl = feedUrl;
            this.i = i;
        }

        @Override
        protected Article doInBackground(Void... params) {
            String[] selectionArgs = new String[0];
            String query = "SELECT " +
                    DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable._ID + ", " +
                    DatabaseSchema.FeedTable.TABLE_NAME + "." + DatabaseSchema.FeedTable.FEED_NAME + ", " +
                    DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_NAME + ", " +
                    DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_URL + ", " +
                    DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_PUBLICATION_DATE + ", " +
                    DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_DESCRIPTION + ", " +
                    DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_IMAGE_URL + ", " +
                    DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_GUID + " " +
                    "FROM " + DatabaseSchema.ArticleTable.TABLE_NAME + " " +
                    "JOIN " + DatabaseSchema.FeedTable.TABLE_NAME + " " +
                    "ON " + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_FEED + "=" + DatabaseSchema.FeedTable.TABLE_NAME + "." + DatabaseSchema.FeedTable._ID + " ";
            if (feedUrl != null) {
                query += "WHERE " + DatabaseSchema.FeedTable.FEED_URL + "=? ";
                selectionArgs = new String[] { feedUrl };
            }
            query += "ORDER BY " + DatabaseSchema.ArticleTable.ARTICLE_PUBLICATION_DATE + " DESC " +
                    "LIMIT 1 " +
                    "OFFSET " + i;
            Cursor articleCursor = database.rawQuery(query, selectionArgs);
            try {
                articleCursor.moveToNext();

                Article.Builder articleBuilder = new Article.Builder();
                articleBuilder.setId(articleCursor.getInt(0));
                articleBuilder.setFeed(articleCursor.getString(1));
                articleBuilder.setTitle(articleCursor.getString(2));
                articleBuilder.setLink(articleCursor.getString(3));
                articleBuilder.setPublicationDate(new DateTime(articleCursor.getLong(4)));
                articleBuilder.setDescription(articleCursor.getString(5));
                articleBuilder.setImageUrl(articleCursor.getString(6));
                articleBuilder.setGuid(articleCursor.getString(7));
                return articleBuilder.build();
            } finally {
                articleCursor.close();
            }
        }

        @Override
        protected void onPostExecute(Article article) {
            database.close();
            ArticleFragment.this.onArticleLoad(article);
        }
    }
}
