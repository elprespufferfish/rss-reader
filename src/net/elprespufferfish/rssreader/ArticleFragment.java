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
import android.widget.ImageButton;
import android.widget.TextView;

public class ArticleFragment extends Fragment {

    public static String TITLE_KEY = "title";
    public static String LINK_KEY = "link";
    public static String DESCRIPTION_KEY = "description";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.article_fragment, container, false);
        Bundle arguments = getArguments();

        final String title = arguments.getString(TITLE_KEY);
        ((TextView) view.findViewById(R.id.title)).setText(title);

        final String linkAddress = arguments.getString(LINK_KEY);
        TextView link = (TextView) view.findViewById(R.id.link);
        link.setText(linkAddress);
        link.setClickable(true);
        link.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkAddress));
                ArticleFragment.this.startActivity(intent);
            }
        });

        final String description = arguments.getString(DESCRIPTION_KEY);
        WebView webView = (WebView) view.findViewById(R.id.description);
        webView.setWebChromeClient(new WebChromeClient()); // HTML5 video requires a WebChromeClient
        enableJavaScript(webView);
        webView.loadData(description, "text/html", null);

        ImageButton shareButton = (ImageButton) view.findViewById(R.id.share);
        shareButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String textToShare = title + "\n\n" + linkAddress;
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, title);
                intent.putExtra(Intent.EXTRA_TEXT, textToShare);
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, getResources().getText(R.string.share_title)));
            }
        });

        return view;
    }

    /* JavaScript is required to play youtube videos */
    @SuppressLint("SetJavaScriptEnabled")
    private void enableJavaScript(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
    }

}
