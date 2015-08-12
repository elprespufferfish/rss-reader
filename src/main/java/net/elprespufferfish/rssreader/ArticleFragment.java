package net.elprespufferfish.rssreader;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
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

import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArticleFragment extends Fragment {

    public static final String ARTICLE_KEY = "article";

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleFragment.class);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.article_fragment, container, false);
        final Article article = getArguments().getParcelable(ARTICLE_KEY);

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

        return view;
    }

    /* JavaScript is required to play youtube videos */
    @SuppressLint("SetJavaScriptEnabled")
    private void enableJavaScript(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
    }

}
