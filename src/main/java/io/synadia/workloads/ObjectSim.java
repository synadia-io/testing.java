package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ObjectStoreConfiguration;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.support.JsonValueUtils;
import io.synadia.CommandLine;
import io.synadia.utils.Debug;

import java.io.IOException;

public class ObjectSim extends AbstractCustomWorkload {
    protected String bucketName;
    protected boolean bucketCompression;

    protected String queueStreamName;
    protected String queueSubject;
    protected String queueConsumerName;
    protected long queueMaxMessages;

    private String putJob;
    private int putThreadCount;
    private long putReportFrequency;
    private long putJitter;
    private long putFullJitter;
    private int putMessageMin;
    private int putMessageMax;

    private String getJob;
    private int getThreadCount;
    private int getBatch;
    private long getReportFrequency;
    private long getJitter;

    public void init(CommandLine commandLine) {
        customWorkloadInit("Object Store Sim", true, commandLine);

        bucketName = JsonValueUtils.readString(params.jv, "bucket_name", "bucket");
        bucketCompression = JsonValueUtils.readBoolean(params.jv, "bucket_compression", false);

        queueStreamName = JsonValueUtils.readString(params.jv, "queue_stream_name", "queue");
        queueSubject = JsonValueUtils.readString(params.jv, "queue_subject", "qsub");
        queueConsumerName = JsonValueUtils.readString(params.jv, "queue_consumer_name", "qcon");
        queueMaxMessages = JsonValueUtils.readLong(params.jv, "queue_max_messages", 1_000_000);

        putJob = JsonValueUtils.readString(params.jv, "put_job", "Put");
        putThreadCount = JsonValueUtils.readInteger(params.jv, "put_thread_count", 3);
        putReportFrequency = JsonValueUtils.readLong(params.jv, "put_report_frequency", 100);
        putJitter = JsonValueUtils.readLong(params.jv, "put_jitter", 50);
        putFullJitter = JsonValueUtils.readLong(params.jv, "put_full_jitter", 1000);
        putMessageMin = JsonValueUtils.readInteger(params.jv, "put_message_min", 10);
        putMessageMax = JsonValueUtils.readInteger(params.jv, "put_message_max", 100);

        getJob = JsonValueUtils.readString(params.jv, "get_job", "Get");
        getThreadCount = JsonValueUtils.readInteger(params.jv, "get_thread_count", 3);
        getBatch = JsonValueUtils.readInteger(params.jv, "get_batch", 10);
        getJitter = JsonValueUtils.readLong(params.jv, "get_jitter", 250);
        getReportFrequency = JsonValueUtils.readLong(params.jv, "get_report_frequency", 100);

        Debug.info(workLabel, "bucketName", bucketName);
        Debug.info(workLabel, "bucketCompression", bucketCompression);

        Debug.info(workLabel, "queueStreamName", queueStreamName);
        Debug.info(workLabel, "queueSubject", queueSubject);
        Debug.info(workLabel, "queueConsumerName", queueConsumerName);
        Debug.info(workLabel, "queueMaxMessages", queueMaxMessages);

        Debug.info(workLabel, "putJob", putJob);
        Debug.info(workLabel, "putThreadCount", putThreadCount);
        Debug.info(workLabel, "putReportFrequency", putReportFrequency);
        Debug.info(workLabel, "putJitter", putJitter);
        Debug.info(workLabel, "putFullJitter", putFullJitter);
        Debug.info(workLabel, "putMessageMin", putMessageMin);
        Debug.info(workLabel, "putMessageMax", putMessageMax);

        Debug.info(workLabel, "getJob", getJob);
        Debug.info(workLabel, "getThreadCount", getThreadCount);
        Debug.info(workLabel, "getBatch", getBatch);
        Debug.info(workLabel, "getReportFrequency", getReportFrequency);
        Debug.info(workLabel, "getJitter", getJitter);
    }

    @Override
    protected String[] commands() {
        return new String[] {"setup", "put", "get", "info", "watch", "clear consumers|bucket|queue|log|ex", "purge <job>"};
    }

    @Override
    protected boolean subRunWorkload(String arg) throws Exception {
        switch (arg) {
            case "put"     -> doWorker(putJob, putThreadCount, this::putWorker);
            case "get"     -> doWorker(getJob, getThreadCount, this::getWorker);
            case "watch"   -> doWatch(queueStreamName);
            default        -> { return false; }
        }
        return true;
    }

    @Override
    protected void subDoSetup(Connection nc, JetStreamManagement jsm) throws IOException, JetStreamApiException, InterruptedException {
        ObjectStoreManagement osm = nc.objectStoreManagement();
        try {
            osm.delete(bucketName);
        }
        catch (JetStreamApiException ignore) {}

        osm.create(ObjectStoreConfiguration.builder()
            .name(bucketName)
            .compression(bucketCompression)
            .build());

        addStream(jsm, StreamConfiguration.builder()
            .name(queueStreamName)
            .subjects(queueSubject)
            .retentionPolicy(RetentionPolicy.WorkQueue)
            .maxMessages(queueMaxMessages)
            .build());

        jsm.createConsumer(queueStreamName, ConsumerConfiguration.builder().durable(queueConsumerName).filterSubject(queueSubject).build());
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private Runnable putWorker(Options options, int tix, WorkState ws) {
        return () -> {
            try (Connection nc = Nats.connect(options)) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                printConnect(nc, putJob, ws.workId, tix);
            }
            catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private Runnable getWorker(Options options, int tix, WorkState ws) {
        return () -> {
            try (Connection nc = Nats.connect(options)) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                printConnect(nc, putJob, ws.workId, tix);
            }
            catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
