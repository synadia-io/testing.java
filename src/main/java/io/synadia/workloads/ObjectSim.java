package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.*;
import io.nats.client.support.JsonValueUtils;
import io.nats.client.support.NatsObjectStoreUtil;
import io.synadia.CommandLine;
import io.synadia.utils.Commons;
import io.synadia.utils.DataGenerator;
import io.synadia.utils.Debug;
import io.synadia.workloads.support.WorkContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

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
        init("Object Store Sim", commandLine);

        bucketName = JsonValueUtils.readString(params.jv, "bucket_name", "bucket");
        bucketStreamName = NatsObjectStoreUtil.toStreamName(bucketName);
        bucketCompression = JsonValueUtils.readBoolean(params.jv, "bucket_compression", false);

        queueStreamName = JsonValueUtils.readString(params.jv, "queue_stream_name", "queue");
        queueSubject = JsonValueUtils.readString(params.jv, "queue_subject", "qsub");
        queueConsumerName = JsonValueUtils.readString(params.jv, "queue_consumer_name", "qcon");
        queueMaxMessages = JsonValueUtils.readLong(params.jv, "queue_max_messages", 1_000_000);

        initCustom(
            new String[] {"put", "get", "cleanup"},
            new String[] {bucketStreamName, queueStreamName}
        );

        maxObjects = JsonValueUtils.readInteger(params.jv, "max_objects", 10_000);

        cleanupJob = JsonValueUtils.readString(params.jv, "cleanup_job", "Cleanup");
        cleanupFrequency = JsonValueUtils.readLong(params.jv, "cleanup_frequency", 10000);

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
    protected boolean subRunWorkload(String arg) throws Exception {
        switch (arg) {
            case "put"     -> doWorker(putJob, putThreadCount, this::putObjectWorker);
            case "get"     -> doWorker(getJob, getThreadCount, this::getObjectWorker);
            case "cleanup" -> doAdmin(this::doCleanup);
            default        -> { return false; }
        }
        return true;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void doCleanup(WorkContext wctx) throws JetStreamApiException, IOException, InterruptedException {
        StreamContext ctx = wctx.nc.getStreamContext(bucketStreamName);
        String filter = NatsObjectStoreUtil.toMetaStreamSubject(bucketName);

        while (true) {
            OrderedConsumerContext occ = ctx.createOrderedConsumer(
                new OrderedConsumerConfiguration().filterSubject(filter));
            List<String> subjectsToDelete = new ArrayList<>();
            try (IterableConsumer it = occ.iterate()) {
                Message m = it.nextMessage(1000);
                while (m != null) {
                    ObjectInfo oi = new ObjectInfo(m);
                    if (oi.isDeleted()) {
                        subjectsToDelete.add(m.getSubject());
                    }
                    m = it.nextMessage(1000);
                }
            }
            catch (Exception ignore) {}

            print(cleanupJob, wctx, "Cleaning up " + subjectsToDelete.size() + " deleted objects.");

            for (String subject : subjectsToDelete) {
                try {
                    wctx.jsm.purgeStream(bucketStreamName, PurgeOptions.subject(subject));
                }
                catch (Exception ignore) {}
            }
            sleep(cleanupFrequency);
        }
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
    }

    private final ReentrantLock powLock = new ReentrantLock();

    @SuppressWarnings("InfiniteLoopStatement")
    private void putObjectWorker(WorkContext wctx) {
        powLock.lock(); // all threads need the file. tix 0 is the first instance created
        try {
            if (wctx.tix == 0) {
                try {
                    generateObject();
                }
                catch (IOException e) {
                    print(putJob, wctx, e.getMessage());
                    System.exit(-1);
                }
            }
        }
        finally {
            powLock.unlock();
        }

        ObjectStore os = null;
        int cutoff = maxObjects;
        while (true) {
            try {
                if (os == null) {
                    os = wctx.nc.objectStore(bucketName);
                }
                long queueSize = getQueueSize(wctx.jsm);
                if (queueSize >= cutoff) {
                    if (cutoff == maxObjects) { // just to avoid repeat printing when full
                        cutoff = maxObjects * 2 / 3;
                        print(putJob, wctx, "* System is full. " + queueSize + "/" + maxObjects);
                    }
                }
                else {
                    String objectName = Commons.generateName();
                    try (FileInputStream in = new FileInputStream(putFileName)) {
                        os.put(objectName, in);
                    }

                    // 3. put a record in the queue last so it's not used until messages are published
                    wctx.js.publish(queueSubject, objectName.getBytes());

                    long count = wctx.increment();
                    if (count % putReportFrequency == 0) {
                        log(putJob, wctx, count);
                    }
                }
                jitter(putJitter);
            }
            catch (IOException | JetStreamApiException | NoSuchAlgorithmException e) {
                sleepThenLog(putJob, putJitter, wctx, e);
                os = null; // clean start
            }
        }
    }

    private void generateObject() throws IOException {
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

    private long getQueueSize(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        StreamInfo si = jsm.getStreamInfo(queueStreamName);
        return si.getStreamState().getMsgCount();
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void getObjectWorker(WorkContext wctx) {
        String outFileName = getFilePrefix + "-" + wctx.ws.workId + wctx.tix + (putFileIsText ? ".txt" : ".dat");
        long min = maxObjects / 5;
        long cutoff = min;
        ObjectStore os = null;
        ConsumerContext qConsumerCtx = null;
        while (true) {
            try {
                if (os == null) {
                    os = wctx.nc.objectStore(bucketName);
                    qConsumerCtx = wctx.nc.getConsumerContext(queueStreamName, queueConsumerName);
                }
                long queueSize = getQueueSize(wctx.jsm);
                if (queueSize < cutoff) {
                    if (cutoff == min) { // just to avoid repeat printing when low
                        cutoff = (long) maxObjects * 75 / 100;
                        print(getJob, wctx, "* System is low. " + queueSize + "/" + maxObjects);
                    }
                }
                else {
                    cutoff = min;
                    String objectName = queueNext(qConsumerCtx);
                    if (objectName != null) {
                        try (FileOutputStream out = new FileOutputStream(outFileName)) {
                            os.get(objectName, out);
                            long count = wctx.increment();
                            if (count % getReportFrequency == 0) {
                                log(getJob, wctx, count);
                            }
                        }
                        catch (IOException | JetStreamApiException e) {
                            log(getJob, wctx, e);
                        }
                        finally {
                            try {
                                os.delete(objectName);
                            }
                            catch (Exception e) {
                                log(getJob, wctx, e);
                            }
                        }
                    }
                }
                jitter(getJitter);
            }
            catch (Exception e) {
                sleepThenLog(getJob, getJitter, wctx, e);
                os = null; // clean start
                qConsumerCtx = null;
            }
        }
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
