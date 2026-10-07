package dev.t1m3.qplayer.bili;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class BiliStreamTest {
    private String parse(String json) { return BiliClient.muxedUrl(JsonParser.parseString(json).getAsJsonObject()); }

    @Test public void progressiveStreamIncludesItsAudio() {
        assertEquals("https://cdn/video.mp4", parse("{\"data\":{\"durl\":[{\"url\":\"https://cdn/video.mp4\"}]}}"));
    }
    @Test public void dashOnlyOrEmptyResponseCannotBecomeSilentVideo() {
        assertNull(parse("{\"data\":{\"dash\":{\"video\":[{\"baseUrl\":\"https://cdn/video.m4s\"}]}}}"));
        assertNull(parse("{\"data\":{\"dash\":{\"video\":[]}}}"));
        assertNull(parse("{\"data\":null}"));
    }
    @Test public void backupUsedWhenPrimaryMissing() {
        assertEquals("https://cdn/backup.mp4", parse("{\"data\":{\"durl\":[{\"url\":\"\",\"backup_url\":[\"\",\"https://cdn/backup.mp4\"]}]}}"));
    }
    @Test public void segmentedVideoCannotBeTruncatedToFirstPart() {
        assertNull(parse("{\"data\":{\"durl\":[{\"url\":\"part1\"},{\"url\":\"part2\"}]}}"));
    }
}
