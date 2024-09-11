package io.synadia;

import io.nats.client.support.JsonValue;
import io.synadia.support.Debug;

import static io.nats.client.support.JsonValueUtils.readLong;
import static io.nats.client.support.JsonValueUtils.readString;

public class ConfigBase {
    public final String label;
    public final JsonValue jv;
    public final String testingStreamName;
    public final String testingStreamSubject;
    public final String statsBucket;
    public final String profileBucket;
    public final String profileStreamName;
    public final String profileStreamSubject;
    public final long watchWaitTime;
    public final String saveServer;
    public final String saveStreamName;
    public final String saveStreamSubject;

    public ConfigBase(String label, JsonValue jv) {
        this.label = label;
        this.jv = jv;
        testingStreamName = readString(jv, "testing_stream_name");
        testingStreamSubject = readString(jv, "testing_stream_subject");
        statsBucket = readString(jv, "stats_bucket");
        profileBucket = readString(jv, "profile_bucket");
        profileStreamName = readString(jv, "profile_stream_name");
        profileStreamSubject = readString(jv, "profile_stream_subject");
        watchWaitTime = readLong(jv, "watch_wait_time", 5000);
        saveServer = readString(jv, "save_server");
        saveStreamName = readString(jv, "save_stream_subject");
        saveStreamSubject = readString(jv, "save_stream_subject");
    }

    public void debug() {
        Debug.info(label,  "testingStreamName", testingStreamName);
        Debug.info(label,  "testingStreamSubject", testingStreamSubject);
        Debug.info(label,  "statsBucket", statsBucket);
        Debug.info(label,  "profileBucket", profileBucket);
        Debug.info(label,  "profileStreamName", profileStreamName);
        Debug.info(label,  "profileStreamSubject", profileStreamSubject);
        Debug.info(label,  "watchWaitTime", "" + watchWaitTime);
        Debug.info(label,  "saveServer", saveServer);
        Debug.info(label,  "saveStreamName", watchWaitTime);
        Debug.info(label,  "saveStreamSubject", saveStreamSubject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + jv.toJson();
    }
}

