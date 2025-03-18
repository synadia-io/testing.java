package io.synadia;

import io.nats.client.Options;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.support.JsonParseException;
import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonSerializable;
import io.nats.client.support.JsonValue;
import io.synadia.utils.Debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static io.nats.client.support.JsonValueUtils.*;

public class Params implements JsonSerializable {
    public final JsonValue jv;

    public final boolean optionsVirtualThreads;
    public final String adminServer;
    public final String bootstrap;
    public final List<String> servers;

    public final StreamConfiguration streamConfig;
    public final boolean createStream;

    public final JsonValue jvMultiConfig;

    // TESTING APP SPECIFIC
    public final String testingStreamName;
    public final String testingStreamSubject;
    public final String multiBucket;
    public final String statsBucket;
    public final String profileBucket;
    public final String profileStreamName;
    public final String profileStreamSubject;
    public final String saveServer;
    public final String saveStreamName;
    public final String saveStreamSubject;
    public final long watchWaitTime;
    public final boolean trackProfile;

    public Params(List<String> paramsFiles) {
        this(readParamsFiles(paramsFiles));
    }

    public Params(JsonValue jv) {
        this.jv = jv;
        optionsVirtualThreads = readBoolean(jv, "options_virtual_threads", false);
        adminServer = readString(jv, "admin_server", Options.DEFAULT_URL);
        bootstrap = readString(jv, "bootstrap", Options.DEFAULT_URL);
        servers = new ArrayList<>();
        int supplied = 0;
        int replace = -1;
        for (int x = 0; x < 5; x++) {
            String temp = readString(jv, "server" + x);
            if (temp != null && temp.startsWith("<Server")) {
                if (++replace == supplied) {
                    replace = 0;
                }
                temp = servers.get(replace);
            }
            else {
                supplied++;
            }
            servers.add(temp);
        }

        streamConfig = loadStreamConfig("stream_config");
        createStream = streamConfig != null && readBoolean(jv, "create_stream", false);

        jvMultiConfig = readObject(jv, "multi_config");

        JsonValue jvta = readObject(jv, "testing_app");
        testingStreamName = readString(jvta, "testing_stream_name");
        testingStreamSubject = readString(jvta, "testing_stream_subject");
        multiBucket = readString(jvta, "multi_bucket");
        statsBucket = readString(jvta, "stats_bucket");
        profileBucket = readString(jvta, "profile_bucket");
        profileStreamName = readString(jvta, "profile_stream_name");
        profileStreamSubject = readString(jvta, "profile_stream_subject");
        saveServer = readString(jvta, "save_server");
        saveStreamName = readString(jvta, "save_stream_name");
        saveStreamSubject = readString(jvta, "save_stream_subject");
        watchWaitTime = readLong(jvta, "watch_wait_time", 5000);
        trackProfile = readBoolean(jvta, "track_profile", false);
    }

    @Override
    public String toJson() {
        return jv.toJson();
    }

    private static final String PARAMS_LABEL = "params";
    private static final String TESTING_APP_LABEL = "testing app";

    private static JsonValue readParamsFiles(List<String> paramsFiles) {
        JsonValue jv = mapBuilder().jv;
        for (String paramsFile : paramsFiles) {
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(paramsFile));
                merge(jv, JsonParser.parse(bytes));
            }
            catch (IOException e) {
                Debug.info(PARAMS_LABEL, "Unable to load params file '" + paramsFile + "', " + e);
                throw new RuntimeException(e);
            }
        }
        return jv;
    }

    private static void merge(JsonValue jvTarget, JsonValue jvNew) {
        for (String newKey : jvNew.map.keySet()) {
            JsonValue newValue = jvNew.map.get(newKey);
            if (newValue.map == null) {
                // just an ordinary key, put might override, fine last in wins
                jvTarget.map.put(newKey, newValue);
            }
            else {
                // a key to a map
                JsonValue targetValue = jvTarget.map.get(newKey);
                if (targetValue == null) {
                    // key didn't exist in old map
                    jvTarget.map.put(newKey, newValue);
                }
                else {
                    merge(targetValue, newValue);
                }
            }
        }

    }

    public void debug() {
        _debug(PARAMS_LABEL, "optionsVirtualThreads", optionsVirtualThreads);
        _debug(PARAMS_LABEL, "streamConfig", streamConfig);
        _debug(PARAMS_LABEL, "createStream", createStream);
        _debug(PARAMS_LABEL, "jvMultiConfig", jvMultiConfig);
        _debug(PARAMS_LABEL, "adminServer", adminServer);
        _debug(PARAMS_LABEL, "servers", servers);

        // TESTING _APP SPECIFIC
        _debug(TESTING_APP_LABEL, "testingStreamName", testingStreamName);
        _debug(TESTING_APP_LABEL, "testingStreamSubject", testingStreamSubject);
        _debug(TESTING_APP_LABEL, "multiBucket", multiBucket);
        _debug(TESTING_APP_LABEL, "statsBucket", statsBucket);
        _debug(TESTING_APP_LABEL, "profileBucket", profileBucket);
        _debug(TESTING_APP_LABEL, "profileStreamName", profileStreamName);
        _debug(TESTING_APP_LABEL, "profileStreamSubject", profileStreamSubject);
        _debug(TESTING_APP_LABEL, "saveServer", saveServer);
        _debug(TESTING_APP_LABEL, "saveStreamName", saveStreamName);
        _debug(TESTING_APP_LABEL, "saveStreamSubject", saveStreamSubject);
        _debug(TESTING_APP_LABEL, "watchWaitTime", watchWaitTime);
        _debug(TESTING_APP_LABEL, "trackProfile", trackProfile);
    }

    private void _debug(String label, String name, Object value) {
        if (value != null) {
            Debug.info(label, name, value);
        }
    }

    public StreamConfiguration loadStreamConfig(String fieldName) {
        JsonValue streamConfigJv = readValue(jv, fieldName);
        StreamConfiguration streamConfig;
        if (streamConfigJv == null) {
            streamConfig = null;
        }
        else {
            try {
                streamConfig = StreamConfiguration.instance(streamConfigJv.toJson());
            }
            catch (JsonParseException e) {
                Debug.info(PARAMS_LABEL, "Unable to parse stream config, " + e);
                throw new RuntimeException(e);
            }
        }
        return streamConfig;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + jv.toJson();
    }
}

