package net.elprespufferfish.rssreader.backup;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;

import net.elprespufferfish.rssreader.Feed;
import net.elprespufferfish.rssreader.FeedAlreadyAddedException;
import net.elprespufferfish.rssreader.Feeds;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Integrates with the Android Backup Service.
 *
 * @see <a href="http://developer.android.com/guide/topics/data/backup.html">http://developer.android.com/guide/topics/data/backup.html</a>
 *
 * @author elprespufferfish
 */
public class RssReaderBackupAgent extends BackupAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(RssReaderBackupAgent.class);
    private static final String FEEDS_KEY = "feeds";

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
        // Check old state
        List<Feed> oldFeeds = readOldState(oldState);
        List<Feed> currentFeeds = Feeds.getInstance().getAllFeeds();
        if (new HashSet<>(oldFeeds).equals(new HashSet<>(currentFeeds))) {
            LOGGER.info("Backup up to date");
            return;
        }

        // update backup
        byte[] output = serializeFeeds(currentFeeds);
        data.writeEntityHeader(FEEDS_KEY, output.length);
        data.writeEntityData(output, output.length);

        // record current state
        ByteArrayInputStream bais = new ByteArrayInputStream(output);
        FileOutputStream newStateStream = new FileOutputStream(newState.getFileDescriptor());
        ByteStreams.copy(bais, newStateStream);
        bais.close();
        newStateStream.close();
        LOGGER.info("Backed up {} feeds", currentFeeds.size());
    }

    private List<Feed> readOldState(ParcelFileDescriptor oldState) throws IOException {
        FileInputStream oldStateStream = new FileInputStream(oldState.getFileDescriptor());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteStreams.copy(oldStateStream, baos);
        oldStateStream.close();
        baos.close();

        if (baos.toByteArray().length == 0) {
            LOGGER.warn("No existing backup");
            return Collections.emptyList();
        }

        try {
            return deserializeFeeds(baos.toByteArray());
        } catch (IOException e) {
            LOGGER.warn("Could not deserialize current backup.  Assuming it's out of date", e);
            return Collections.emptyList();
        }
    }

    private List<Feed> deserializeFeeds(byte[] bytes) throws IOException {
        try {
            String jsonString = new String(bytes, Charsets.UTF_8);
            JSONArray feedsArray = new JSONArray(jsonString);
            List<Feed> feeds = new ArrayList<>(feedsArray.length());
            for (int i = 0; i < feedsArray.length(); i++) {
                JSONObject feedObject = feedsArray.getJSONObject(i);
                Feed feed = new Feed.Builder()
                        .withName(feedObject.getString("name"))
                        .withUrl(feedObject.getString("url"))
                        .build();
                feeds.add(feed);
            }
            return feeds;
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private byte[] serializeFeeds(List<Feed> feeds) throws IOException {
        try {
            JSONArray feedsArray = new JSONArray();
            for (Feed feed : feeds) {
                JSONObject feedObject = new JSONObject();
                feedObject.put("name", feed.getName());
                feedObject.put("url", feed.getUrl());
                feedsArray.put(feedObject);
            }
            return feedsArray.toString().getBytes(Charsets.UTF_8);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
        // TODO - check version code

        while (data.readNextHeader()) {
            String key = data.getKey();
            switch (key) {
                case FEEDS_KEY: {
                    // TODO - clear existing feeds?

                    // read backed up feeds
                    byte[] serializedFeeds = new byte[data.getDataSize()];
                    data.readEntityData(serializedFeeds, 0, data.getDataSize());
                    List<Feed> feeds = deserializeFeeds(serializedFeeds);
                    try {
                        for (Feed feed : feeds) {
                            Feeds.getInstance().addFeed(feed);
                        }
                    } catch (FeedAlreadyAddedException e) {
                        // ignore
                    }

                    // record current state
                    ByteArrayInputStream bais = new ByteArrayInputStream(serializedFeeds);
                    FileOutputStream newStateStream = new FileOutputStream(newState.getFileDescriptor());
                    ByteStreams.copy(bais, newStateStream);
                    bais.close();
                    newStateStream.close();

                    break;
                }
                default: {
                    LOGGER.warn("Unexpected key '{}' found in backup data", key);
                    data.skipEntityData();
                }
            }
        }

        LOGGER.info("Restored complete");
    }

}
