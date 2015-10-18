package net.elprespufferfish.rssreader.settings;

import android.util.Pair;

/**
 * List of settings and default values.
 */
public class Settings {

    public static final Pair<String, Integer> RETENTION_PERIOD = Pair.create("retention_period", 14);

    public static final Pair<String, String> REFRESH_FREQUENCY = Pair.create("refresh_frequency", "86400000");

    private Settings() {
        // prevent instantiation
    }

}
