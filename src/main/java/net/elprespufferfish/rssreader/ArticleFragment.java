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

    public static final String FEED_KEY = "feed";
    public static final String TITLE_KEY = "title";
    public static final String LINK_KEY = "link";
    public static final String DESCRIPTION_KEY = "description";
    public static final String IMAGE_KEY = "imageUrl";

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleFragment.class);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.article_fragment, container, false);
        Bundle arguments = getArguments();

        final String title = arguments.getString(TITLE_KEY);
        final String linkAddress = arguments.getString(LINK_KEY);
        TextView titleView = ((TextView) view.findViewById(R.id.title));
        titleView.setText(title);
        titleView.setClickable(true);
        titleView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkAddress));
                ArticleFragment.this.startActivity(intent);
            }
        });

        String imageUrl = arguments.getString(IMAGE_KEY);
        ImageView imageView = (ImageView) view.findViewById(R.id.image);
        WebView webView = (WebView) view.findViewById(R.id.description);
        if (imageUrl != null) {
            LOGGER.debug("Displaying ImageView");
            imageView.setVisibility(ImageView.VISIBLE);
            webView.setVisibility(WebView.GONE);

            Picasso.with(this.getActivity()).load(imageUrl).into(imageView);
        } else {
            LOGGER.debug("Displaying WebView");
            imageView.setVisibility(ImageView.GONE);
            webView.setVisibility(WebView.VISIBLE);

            final String description = arguments.getString(DESCRIPTION_KEY);
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
