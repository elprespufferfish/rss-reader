package net.elprespufferfish.rssreader.settings;

import android.util.Pair;

/**
 * List of settings and default values.
 */
public class Settings {

    public static final Pair<String, Integer> RETENTION_PERIOD = Pair.create("retention_period", 14);

    private Settings() {
        // prevent instantiation
    }

}
