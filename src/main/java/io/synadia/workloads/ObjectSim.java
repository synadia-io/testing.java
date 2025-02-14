package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.*;
import io.nats.client.support.JsonValueUtils;
import io.nats.client.support.NatsObjectStoreUtil;
import io.synadia.CommandLine;
import io.synadia.utils.DataGenerator;
import io.synadia.utils.Debug;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static io.nats.client.support.JsonUtils.printFormatted;
import static io.nats.jsmulti.shared.Utils.sleep;

public class ObjectSim extends AbstractCustomWorkload {
    private String bucketName;
    private String bucketStreamName;
    private boolean bucketCompression;

    private String queueStreamName;
    private String queueSubject;
    private String queueConsumerName;
    private long queueMaxMessages;

    private int maxObjects;

    private String cleanupJob;
    private long cleanupFrequency;

    private String putJob;
    private int putThreadCount;
    private long putReportFrequency;
    private long putJitter;
    private String putFileName;
    private String putFileSize;
    private boolean putFileIsText;

    private String getJob;
    private int getThreadCount;
    private long getReportFrequency;
    private long getJitter;
    private String getFilePrefix;

    public void init(CommandLine commandLine) {
        customWorkloadInit("Object Store Sim", true, commandLine);

        bucketName = JsonValueUtils.readString(params.jv, "bucket_name", "bucket");
        bucketStreamName = NatsObjectStoreUtil.toStreamName(bucketName);
        bucketCompression = JsonValueUtils.readBoolean(params.jv, "bucket_compression", false);

        queueStreamName = JsonValueUtils.readString(params.jv, "queue_stream_name", "queue");
        queueSubject = JsonValueUtils.readString(params.jv, "queue_subject", "qsub");
        queueConsumerName = JsonValueUtils.readString(params.jv, "queue_consumer_name", "qcon");
        queueMaxMessages = JsonValueUtils.readLong(params.jv, "queue_max_messages", 1_000_000);

        maxObjects = JsonValueUtils.readInteger(params.jv, "max_objects", 10_000);

        cleanupJob = JsonValueUtils.readString(params.jv, "cleanup_job", "Cleanup");
        cleanupFrequency = JsonValueUtils.readLong(params.jv, "cleanup_frequency", 60000);

        putJob = JsonValueUtils.readString(params.jv, "put_job", "Put");
        putThreadCount = JsonValueUtils.readInteger(params.jv, "put_thread_count", 3);
        putReportFrequency = JsonValueUtils.readLong(params.jv, "put_report_frequency", 100);
        putJitter = JsonValueUtils.readLong(params.jv, "put_jitter", 50);
        putFileName = JsonValueUtils.readString(params.jv, "put_file_name", "objectsim-source.txt");
        putFileSize = JsonValueUtils.readString(params.jv, "put_file_size", "10m");
        putFileIsText = JsonValueUtils.readBoolean(params.jv, "put_file_is_text", true);

        getJob = JsonValueUtils.readString(params.jv, "get_job", "Get");
        getThreadCount = JsonValueUtils.readInteger(params.jv, "get_thread_count", 3);
        getJitter = JsonValueUtils.readLong(params.jv, "get_jitter", 250);
        getReportFrequency = JsonValueUtils.readLong(params.jv, "get_report_frequency", 100);
        getFilePrefix = JsonValueUtils.readString(params.jv, "get_file_prefix", "objectsim-get");

        Debug.info(workLabel, "bucketName", bucketName);
        Debug.info(workLabel, "bucketCompression", bucketCompression);

        Debug.info(workLabel, "queueStreamName", queueStreamName);
        Debug.info(workLabel, "queueSubject", queueSubject);
        Debug.info(workLabel, "queueConsumerName", queueConsumerName);
        Debug.info(workLabel, "queueMaxMessages", queueMaxMessages);

        Debug.info(workLabel, "maxObjects", maxObjects);
        Debug.info(workLabel, "cleanupFrequency", cleanupFrequency);

        Debug.info(workLabel, "putJob", putJob);
        Debug.info(workLabel, "putThreadCount", putThreadCount);
        Debug.info(workLabel, "putReportFrequency", putReportFrequency);
        Debug.info(workLabel, "putJitter", putJitter);
        Debug.info(workLabel, "putFileName", putFileName);
        Debug.info(workLabel, "putFileSize", putFileSize);
        Debug.info(workLabel, "putFileIsText", putFileIsText);

        Debug.info(workLabel, "getJob", getJob);
        Debug.info(workLabel, "getThreadCount", getThreadCount);
        Debug.info(workLabel, "getReportFrequency", getReportFrequency);
        Debug.info(workLabel, "getJitter", getJitter);
        Debug.info(workLabel, "getFilePrefix", getFilePrefix);
    }

    @Override
    protected String[] commands() {
        return new String[] {"setup", "put", "get", "watch", "cleanup", "clear queue|log|ex"};
    }

    @Override
    protected boolean subRunWorkload(String arg) throws Exception {
        switch (arg) {
            case "put"     -> doWorker(putJob, putThreadCount, this::putObjectWorker);
            case "get"     -> doWorker(getJob, getThreadCount, this::getObjectWorker);
            case "watch"   -> doWatch(bucketStreamName, queueStreamName);
            case "cleanup" -> doCleanup();
            default        -> { return false; }
        }
        return true;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void doCleanup() throws JetStreamApiException, IOException, InterruptedException {
        WorkState ws = new WorkState();
        runAdminCommand((nc, js) -> {
            JetStreamManagement jsm = nc.jetStreamManagement();
            StreamContext ctx = nc.getStreamContext(bucketStreamName);
            String filter = NatsObjectStoreUtil.toMetaStreamSubject(bucketName);

            while (true) {
                OrderedConsumerContext occ = ctx.createOrderedConsumer(
                    new OrderedConsumerConfiguration().filterSubject(filter));
                List<String> deletes = new ArrayList<>();
                try (IterableConsumer it = occ.iterate()) {
                    Message m = it.nextMessage(1000);
                    while (m != null) {
                        ObjectInfo oi = new ObjectInfo(m);
                        if (oi.isDeleted()) {
                            deletes.add(m.getSubject());
                        }
                        m = it.nextMessage(1000);
                    }
                }
                catch (Exception ignore) {}

                print(cleanupJob, ws.workId, NO_TIX, null, 0, ws.elapse(), "Cleaning up " + deletes.size() + " deleted objects.");

                for (String subject : deletes) {
                    try {
                        jsm.purgeStream(bucketStreamName, PurgeOptions.subject(subject));
                    }
                    catch (Exception ignore) {}
                }
                sleep(cleanupFrequency);
            }
        });
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

        ConsumerInfo ci = jsm.createConsumer(queueStreamName, ConsumerConfiguration.builder().durable(queueConsumerName).filterSubject(queueSubject).build());
        printFormatted(ci.getJv());

        File f = new File(putFileName);
        if (putFileIsText) {
            DataGenerator.generateTextFile(f, putFileSize);
        }
        else {
            DataGenerator.generateBinaryFile(f, putFileSize);
        }
        if (f.exists()) {
            System.out.println(f.getAbsolutePath() + " -> " + f.length() + " bytes");
        }
        else {
            throw new IOException("File not found: " + f.getAbsolutePath());
        }
    }

    @Override
    protected boolean subDoClear(String option) throws IOException, JetStreamApiException, InterruptedException {
        //noinspection SwitchStatementWithTooFewBranches
        switch (option) {
            case "queue"     -> doClear("Queue", queueStreamName);
            default          -> { return false; } // unknown option returns false, others fall through to return true
        }
        return true;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private Runnable putObjectWorker(Options options, int tix, WorkState ws) {
        return () -> {
            try (Connection nc = Nats.connect(options)) {
                printConnect(nc, putJob, ws.workId, tix);
                JetStream js = nc.jetStream();
                JetStreamManagement jsm = nc.jetStreamManagement();
                ObjectStore os = nc.objectStore(bucketName);
                jitter(putJitter);
                int cutoff = maxObjects;
                while (true) {
                    try {
                        long queueSize = getQueueSize(jsm);
                        if (queueSize >= cutoff) {
                            if (cutoff == maxObjects) { // just to avoid repeat printing when full
                                cutoff = maxObjects * 2 / 3;
                                print(putJob, ws.workId, tix, null, 0, ws.elapse(), "* System is full. " + queueSize + "/" + maxObjects);
                            }
                        }
                        else {
                            String objectName = generateName();
                            try (FileInputStream in = new FileInputStream(putFileName)) {
                                os.put(objectName, in);
                            }

                            // 3. put a record in the queue last so it's not used until messages are published
                            js.publish(queueSubject, objectName.getBytes());

                            long count = ws.increment();
                            if (count % putReportFrequency == 0) {
                                log(js, putJob, ws.workId, NO_TIX, count, ws.elapse());
                            }
                        }
                    }
                    catch (IOException | JetStreamApiException | NoSuchAlgorithmException e) {
                        log(js, putJob, ws.workId, ws.elapse(), e);
                    }
                    jitter(putJitter);
                }
            }
            catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private long getQueueSize(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        StreamInfo si = jsm.getStreamInfo(queueStreamName);
        return si.getStreamState().getMsgCount();
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private Runnable getObjectWorker(Options options, int tix, WorkState ws) {
        return () -> {
            try (Connection nc = Nats.connect(options)) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                JetStream js = nc.jetStream();
                printConnect(nc, getJob, ws.workId, tix);
                jitter(getJitter / 10);
                ConsumerContext qConsumerCtx = nc.getConsumerContext(queueStreamName, queueConsumerName);
                ObjectStore os = nc.objectStore(bucketName);
                String outFileName = getFilePrefix + "-" + ws.workId + tix + (putFileIsText ? ".txt" : ".dat");
                long min = maxObjects / 5;
                long cutoff = min;
                while (true) {
                    try {
                        long queueSize = getQueueSize(jsm);
                        if (queueSize < cutoff) {
                            if (cutoff == min) { // just to avoid repeat printing when low
                                cutoff = (long)maxObjects * 75 / 100;
                                print(getJob, ws.workId, tix, null, 0, ws.elapse(), "* System is low. " + queueSize + "/" + maxObjects);
                            }
                        }
                        else {
                            cutoff = min;
                            String objectName = queueNext(qConsumerCtx);
                            if (objectName != null) {
                                try (FileOutputStream out = new FileOutputStream(outFileName)) {
                                    os.get(objectName, out);
                                    long count = ws.increment();
                                    if (count % getReportFrequency == 0) {
                                        log(js, getJob, ws.workId, NO_TIX, count, ws.elapse());
                                    }
                                }
                                catch (IOException | JetStreamApiException e) {
                                    log(js, getJob, ws.workId, ws.elapse(), e);
                                }
                                finally {
                                    try {
                                        os.delete(objectName);
                                    }
                                    catch (Exception e) {
                                        log(js, getJob, ws.workId, ws.elapse(), e);
                                    }
                                }
                            }
                        }
                    }
                    catch (Exception ignore) {}
                    jitter(getJitter);
                }
            }
            catch (IOException | InterruptedException | JetStreamApiException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private String queueNext(ConsumerContext qConsumerCtx) throws JetStreamApiException, IOException, InterruptedException, JetStreamStatusCheckedException {
        Message qm = qConsumerCtx.next(1000);
        if (qm != null) {
            qm.ack();
            return new String(qm.getData());
        }
        return null;
    }
}
