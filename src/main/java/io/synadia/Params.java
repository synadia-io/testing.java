package io.synadia;

import io.nats.client.Options;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.support.JsonParseException;
import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.synadia.tools.Debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static io.nats.client.support.JsonValueUtils.*;

public class Params {
    public final JsonValue jv;
    public final StreamConfiguration streamConfig;
    public final JsonValue jvMultiConfig;
    public final boolean createStream;
    public final String adminServer;
    public final String server0;
    public final String server1;
    public final String server2;
    public final String bucket;

    public Params(List<String> paramsFiles) {
        jv = mapBuilder().jv;
        for (String paramsFile : paramsFiles) {
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(paramsFile));
                jv.map.putAll(JsonParser.parse(bytes).map);
            }
            catch (IOException e) {
                Debug.info("Params", "Unable to load params file '" + paramsFile + "', " + e);
                throw new RuntimeException(e);
            }
        }
        streamConfig = loadStreamConfig("stream_config");
        createStream = streamConfig != null && readBoolean(jv, "create_stream", false);
        jvMultiConfig = readObject(jv, "multi_config");
        adminServer = readString(jv, "admin_server", Options.DEFAULT_URL);
        server0 = readString(jv, "server0");
        server1 = readString(jv, "server1");
        server2 = readString(jv, "server2");
        bucket = readString(jv, "tracking_bucket");

        System.out.println(jv.toJson());
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
                Debug.info("Params", "Unable to parse stream config, " + e);
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

