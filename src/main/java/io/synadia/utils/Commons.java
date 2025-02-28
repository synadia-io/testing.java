package io.synadia.utils;

import io.nats.client.NUID;

import java.text.SimpleDateFormat;
import java.util.concurrent.ThreadLocalRandom;

import static io.nats.client.support.NatsObjectStoreUtil.OBJ_STREAM_PREFIX;

public class Commons {
    public static final SimpleDateFormat FULL_DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    public static final SimpleDateFormat FULL_DATE_FORMATTER_ALT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
    public static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss.SSSZ");

    public static final String TIME = "time";
    public static final String TIME_MS = "time_ms";

    public static final String FINAL = "final";
    public static final String OS_WIN = "win";
    public static final String OS_UNIX = "unix";

    public static final String WATCH = "Watch";
    public static final String SETUP = "Setup";
    public static final String GENERATOR = "Generator";

    public static final int NO_TIX = Integer.MIN_VALUE;
    public static final String DEFAULT_SEGMENT = "._";
    public static final String DOT = ".";

    public static String generateWorkId() {
        return Long.toHexString(System.currentTimeMillis()).toLowerCase() + NUID.nextGlobalSequence();
    }

    public static String generateName() {
        return NUID.nextGlobalSequence() + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt()).toLowerCase();
    }

    public static boolean isOsStream(String streamName) {
        return streamName.startsWith(OBJ_STREAM_PREFIX);
    }
}
