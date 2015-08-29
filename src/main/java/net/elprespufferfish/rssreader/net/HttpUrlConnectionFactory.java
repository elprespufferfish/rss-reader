package net.elprespufferfish.rssreader.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Factory for {@link java.net.HttpURLConnection} objects to facilitate testing
 */
public class HttpUrlConnectionFactory {

    /**
     * @return HttpURLConnection to the provided url with default connect and read timeouts
     */
    public HttpURLConnection create(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(10000);
        connection.setConnectTimeout(10000);
        return connection;
    }

}
