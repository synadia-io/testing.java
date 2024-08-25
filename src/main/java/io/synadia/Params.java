package io.synadia;

import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.synadia.types.ExecutorServiceType;
import io.synadia.types.PublishType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.nats.client.support.JsonValueUtils.*;

public class Params {
    private final JsonValue jv;
    public final String streamName;
    public final String publishSubjectPrefix;
    public final ExecutorServiceType executorServiceType;
    public final PublishType publishType;
    public final int publishThreads;
    public final int publishSawtoothBatch;
    public final long messageCount;
    public final long printWait;
    public final long reportIncrement;
    public final boolean createStream;
    public final String streamConfigJson;

    public Params(String paramsFile) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(paramsFile));
            jv = JsonParser.parse(bytes);
            streamName = readString(jv, "stream_name");
            publishSubjectPrefix = readString(jv, "publish_subject_prefix");
            executorServiceType = ExecutorServiceType.get(readString(jv, "executor_service_type"), ExecutorServiceType.Original);
            publishType = PublishType.get(readString(jv, "publish_type"), PublishType.Sync);
            publishThreads = readInteger(jv, "publish_threads", 1);
            publishSawtoothBatch = readInteger(jv, "publish_sawtooth_batch", 10);
            messageCount = readLong(jv, "message_count", 0);
            printWait = readLong(jv, "print_wait", 500);
            reportIncrement = readLong(jv, "report_increment", 50000);
            createStream = readBoolean(jv, "create_stream", false);
            streamConfigJson = readObject(jv, "stream_config").toJson();
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

