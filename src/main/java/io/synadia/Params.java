package io.synadia;

import io.nats.client.Options;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.nats.client.support.JsonValueUtils.*;

public class Params {
    public final JsonValue jv;
    public final String managementServer;
    public final StreamConfiguration streamConfig;
    public final boolean createStream;
    public final JsonValue jvMultiConfig;

    public Params(String paramsFile) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(paramsFile));
            jv = JsonParser.parse(bytes);
            managementServer = readString(jv, "management_server", Options.DEFAULT_URL);
            JsonValue streamConfigJv = readValue(jv, "stream_config");
            if (streamConfigJv == null) {
                streamConfig = null;
            }
            else {
                streamConfig = StreamConfiguration.instance(streamConfigJv.toJson());
            }
            createStream = streamConfig != null && readBoolean(jv, "create_stream", false);
            jvMultiConfig = readObject(jv, "multi_config");
        }
        catch (IOException e) {
            Debug.info("Params", "Unable to load params file '" +  paramsFile + "', " + e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + jv.toJson();
    }
}

