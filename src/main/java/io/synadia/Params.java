package io.synadia;

import io.nats.client.Options;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.support.JsonParseException;
import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.synadia.utils.Debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static io.nats.client.support.JsonValueUtils.*;
import static io.synadia.utils.Constants.OS_UNIX;
import static io.synadia.utils.Constants.OS_WIN;

public class Params {
    private static final String PARAMS = "Params";

    public final JsonValue jv;
    public final String os;
    public final StreamConfiguration streamConfig;
    public final JsonValue jvMultiConfig;
    public final boolean createStream;
    public final String adminServer;
    public final String server0;
    public final String server1;
    public final String server2;
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

    public Params(List<String> paramsFiles) {
        this(readParamsFiles(paramsFiles));
    }

    public Params(JsonValue jv) {
        this.jv = jv;
        String temp = readString(jv, "os");
        os = OS_WIN.equals(temp) ? OS_WIN : OS_UNIX;
        streamConfig = loadStreamConfig("stream_config");
        createStream = streamConfig != null && readBoolean(jv, "create_stream", false);
        jvMultiConfig = readObject(jv, "multi_config");
        adminServer = readString(jv, "admin_server", Options.DEFAULT_URL);
        server0 = readString(jv, "server0");
        server1 = readString(jv, "server1");
        server2 = readString(jv, "server2");
        testingStreamName = readString(jv, "testing_stream_name");
        testingStreamSubject = readString(jv, "testing_stream_subject");
        multiBucket = readString(jv, "multi_bucket");
        statsBucket = readString(jv, "stats_bucket");
        profileBucket = readString(jv, "profile_bucket");
        profileStreamName = readString(jv, "profile_stream_name");
        profileStreamSubject = readString(jv, "profile_stream_subject");
        saveServer = readString(jv, "save_server");
        saveStreamName = readString(jv, "save_stream_name");
        saveStreamSubject = readString(jv, "save_stream_subject");
        watchWaitTime = readLong(jv, "watch_wait_time", 5000);
    }

    public String toJson() {
        return jv.toJson();
    }

    private static JsonValue readParamsFiles(List<String> paramsFiles) {
        JsonValue jv = mapBuilder().jv;
        for (String paramsFile : paramsFiles) {
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(paramsFile));
                jv.map.putAll(JsonParser.parse(bytes).map);
            }
            catch (IOException e) {
                Debug.info(PARAMS, "Unable to load params file '" + paramsFile + "', " + e);
                throw new RuntimeException(e);
            }
        }
        return jv;
    }

    public void debug() {
        _debug("os", os);
        _debug("streamConfig", streamConfig);
        _debug("createStream", createStream);
        _debug("jvMultiConfig", createStream ? jvMultiConfig : null);
        _debug("adminServer", adminServer);
        _debug("server0", server0);
        _debug("server1", server1);
        _debug("server2", server2);

        _debug("testingStreamName", testingStreamName);
        _debug("testingStreamSubject", testingStreamSubject);
        _debug("multiBucket", multiBucket);
        _debug("statsBucket", statsBucket);
        _debug("profileBucket", profileBucket);
        _debug("profileStreamName", profileStreamName);
        _debug("profileStreamSubject", profileStreamSubject);
        _debug("saveServer", saveServer);
        _debug("saveStreamName", saveStreamName);
        _debug("saveStreamSubject", saveStreamSubject);

        _debug("watchWaitTime", watchWaitTime);
    }

    private void _debug(String name, Object value) {
        if (value != null) {
            Debug.info(PARAMS, name, value);
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
                Debug.info(PARAMS, "Unable to parse stream config, " + e);
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

