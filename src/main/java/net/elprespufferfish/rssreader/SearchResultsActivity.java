package net.elprespufferfish.rssreader;

import static butterknife.ButterKnife.findById;

import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Displays articles matching search query.
 *
 * @author elprespufferfish
 */
public class SearchResultsActivity extends AppCompatActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchResultsActivity.class);

    @Bind(R.id.search_results)
    RecyclerView recyclerView;
    private SQLiteDatabase database;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_results);

        ButterKnife.bind(this);

        getSupportActionBar().setTitle(getString(R.string.search_results_title));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        DatabaseHelper databaseHelper = new DatabaseHelper(this);
        this.database = databaseHelper.getReadableDatabase();

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    public void onDestroy() {
        database.close();
        super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        if (!Intent.ACTION_SEARCH.equals(intent.getAction())) {
            LOGGER.warn("Recieved unexpected intent {}", intent);
            return;
        }

        String query = intent.getStringExtra(SearchManager.QUERY);
        LOGGER.info("Received query: {}", query);
        new SearchTask(database).execute(query);
    }

    private class SearchTask extends AsyncTask<String, Void, ArrayList<Article>> {

        private final SQLiteDatabase database;

        public SearchTask(SQLiteDatabase database) {
            this.database = database;
        }

        @Override
        protected ArrayList<Article> doInBackground(String... strings) {
            String term = strings[0];
            // TODO - split query
            // TODO - OR or AND?

            String query = "SELECT "
                    + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable._ID + ", "
                    + DatabaseSchema.FeedTable.TABLE_NAME + "." + DatabaseSchema.FeedTable.FEED_NAME + ", "
                    + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_NAME + ", "
                    + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_URL + ", "
                    + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_PUBLICATION_DATE + ", "
                    + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_DESCRIPTION + ", "
                    + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_IMAGE_URL + ", "
                    + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_GUID + " "
                    + "FROM " + DatabaseSchema.ArticleTable.TABLE_NAME + " "
                    + "JOIN " + DatabaseSchema.FeedTable.TABLE_NAME + " "
                    + "ON " + DatabaseSchema.ArticleTable.TABLE_NAME + "." + DatabaseSchema.ArticleTable.ARTICLE_FEED + "=" + DatabaseSchema.FeedTable.TABLE_NAME + "." + DatabaseSchema.FeedTable._ID + " "
                    + "WHERE " + DatabaseSchema.ArticleTable.ARTICLE_NAME + " LIKE ? "
                    + "OR " + DatabaseSchema.ArticleTable.ARTICLE_DESCRIPTION + " LIKE ? "
                    + "ORDER BY " + DatabaseSchema.ArticleTable.ARTICLE_PUBLICATION_DATE + " DESC ";

            Cursor articleCursor = database.rawQuery(query, new String[] { "% "+term+" %", "% "+term+" %" });
            try {
                ArrayList<Article> articles = new ArrayList<>(articleCursor.getCount());
                articleCursor.moveToNext();
                while (!articleCursor.isAfterLast()) {
                    Article.Builder articleBuilder = new Article.Builder();
                    articleBuilder.setId(articleCursor.getInt(0));
                    articleBuilder.setFeed(articleCursor.getString(1));
                    articleBuilder.setTitle(articleCursor.getString(2));
                    articleBuilder.setLink(articleCursor.getString(3));
                    articleBuilder.setPublicationDate(new DateTime(articleCursor.getLong(4)));
                    articleBuilder.setDescription(articleCursor.getString(5));
                    articleBuilder.setImageUrl(articleCursor.getString(6));
                    articleBuilder.setGuid(articleCursor.getString(7));

                    articles.add(articleBuilder.build());

                    articleCursor.moveToNext();
                }
                return articles;
            } finally {
                articleCursor.close();
            }
        }

        @Override
        protected void onPostExecute(ArrayList<Article> articles) {
            if (articles.isEmpty()) {
                SearchResultsActivity.this.setContentView(R.layout.no_search_results);
            } else {
                recyclerView.setAdapter(new SearchResultsAdapter(articles));
            }
        }

    }

    private class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsViewHolder> {

        private final ArrayList<Article> articles;

        public SearchResultsAdapter(ArrayList<Article> articles) {
            this.articles = articles;
        }

        @Override
        public SearchResultsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.search_result, parent, false);

            return new SearchResultsViewHolder(view);
        }

        @Override
        public void onBindViewHolder(SearchResultsViewHolder viewHolder, int position) {
            final Article article = articles.get(position);
            viewHolder.title.setText(article.getTitle());
            viewHolder.feed.setText(article.getFeed());

            viewHolder.card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SearchResultsActivity.this, WebViewActivity.class);
                    intent.putExtra(WebViewActivity.URL_ARGUMENT, article.getLink());
                    SearchResultsActivity.this.startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return articles.size();
        }

    }

    private static class SearchResultsViewHolder extends RecyclerView.ViewHolder {

        public final CardView card;
        public final TextView title;
        public final TextView feed;

        public SearchResultsViewHolder(View itemView) {
            super(itemView);

            card = findById(itemView, R.id.card_view);
            title = findById(itemView, R.id.search_result_title);
            feed = findById(itemView, R.id.search_result_feed);
        }

    }

}
