package net.elprespufferfish.rssreader;

import static butterknife.ButterKnife.findById;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * WebView activity to show article content.
 *
 * @author elprespufferfish
 */
public class WebViewActivity extends AppCompatActivity {

    public static final String URL_ARGUMENT = "url";

    private static final Logger LOGGER = LoggerFactory.getLogger(WebViewActivity.class);

    private ShareActionProvider shareActionProvider;

    @Bind(R.id.webview)
    WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.article_content);

        ButterKnife.bind(this);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final ProgressBar progressBar = findById(this, R.id.progress_bar);

        webView.getSettings().setJavaScriptEnabled(true);

        // allow zooming
        webView.getSettings().setBuiltInZoomControls(true);
        // but don't display the controls
        webView.getSettings().setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // TODO - handle errors
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                if (progress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

        });

        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);

        shareActionProvider = new ShareActionProvider(this);

        handleIntent(getIntent());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_actionbar, menu);

        MenuItem shareItem = menu.findItem(R.id.action_share);
        MenuItemCompat.setActionProvider(shareItem, shareActionProvider);

        MenuItem openInBrowserItem = menu.findItem(R.id.action_open_in_browser);
        openInBrowserItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
                startActivity(intent);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void handleIntent(Intent intent) {
        String url = intent.getStringExtra(URL_ARGUMENT);
        LOGGER.info("Loading {}", url);
        webView.loadUrl(url);
    }

    @Override
    public void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        webView = null;
        super.onDestroy();
    }

}
