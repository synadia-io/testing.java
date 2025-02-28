package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.*;
import io.nats.client.support.JsonValueUtils;
import io.nats.jsmulti.shared.FailureException;
import io.synadia.CommandLine;
import io.synadia.utils.Debug;
import io.synadia.workloads.support.WorkState;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static io.nats.jsmulti.shared.Utils.sleep;
import static io.synadia.utils.Commons.NO_TIX;

public class FailgroundSim extends AbstractCustomWorkload {
    private String dataStreamName;
    private String dataSubject;
    private long dataMaxMessages;

    private String publishJob;
    private long publishReportFrequency;
    private long publishJitter;
    private int publishMinMessageSize;
    private int publishMaxMessageSize;

    private String orderedJob;
    private long orderedReportFrequency;
    private long orderedJitter;

    @Override
    public void init(CommandLine commandLine) {
        init("Failground Sim", commandLine);

        dataStreamName = JsonValueUtils.readString(params.jv, "data_stream_name", "data");

        initCustom(
            new String[]{"pub", "ordered", "un"},
            new String[]{dataStreamName}
        );

        dataSubject = JsonValueUtils.readString(params.jv, "data_subject", "d");
        dataMaxMessages = JsonValueUtils.readLong(params.jv, "data_max_messages", 1_000_000);
        Debug.info(workLabel, "dataStreamName", dataStreamName);
        Debug.info(workLabel, "dataSubject", dataSubject);
        Debug.info(workLabel, "dataMaxMessages", dataMaxMessages);

        publishJob = JsonValueUtils.readString(params.jv, "publish_job", "Publish");
        publishReportFrequency = JsonValueUtils.readLong(params.jv, "publish_report_frequency", 100);
        publishJitter = JsonValueUtils.readLong(params.jv, "publish_jitter", 10);
        publishMinMessageSize = JsonValueUtils.readInteger(params.jv, "publish_min_message_size", 100);
        publishMaxMessageSize = JsonValueUtils.readInteger(params.jv, "publish_max_message_size", 1000);
        Debug.info(workLabel, "publishJob", publishJob);
        Debug.info(workLabel, "publishReportFrequency", publishReportFrequency);
        Debug.info(workLabel, "publishJitter", publishJitter);
        Debug.info(workLabel, "publishMinMessageSize", publishMinMessageSize);
        Debug.info(workLabel, "publishMaxMessageSize", publishMaxMessageSize);


        orderedJob = JsonValueUtils.readString(params.jv, "ordered_job", "Ordered");
        orderedReportFrequency = JsonValueUtils.readLong(params.jv, "ordered_report_frequency", 10000);
        orderedJitter = JsonValueUtils.readLong(params.jv, "ordered_jitter", 10);
        Debug.info(workLabel, "orderedJob", orderedJob);
        Debug.info(workLabel, "orderedReportFrequency", orderedReportFrequency);
        Debug.info(workLabel, "orderedJitter", orderedJitter);
    }

    @Override
    protected void subDoSetup(Connection nc, JetStreamManagement jsm) throws IOException, JetStreamApiException, InterruptedException {
        addStream(jsm, StreamConfiguration.builder()
            .name(dataStreamName)
            .subjects(dataSubject)
            .maxMessages(dataMaxMessages)
            .build());
    }

    @Override
    protected boolean subRunWorkload(String arg) throws Exception {
        switch (arg) {
            case "pub"     -> doWorker(publishJob, this::pubWorker);
            case "ordered" -> doWorker(orderedJob, this::orderedWorker);
            default        -> { return false; }
        }
        return true;
    }

    private byte[] getData(String pubId) {
        byte[] idData = (pubId + " ").getBytes();
        int len = ThreadLocalRandom.current().nextInt(publishMinMessageSize, publishMaxMessageSize) + idData.length;
        byte[] data = new byte[len];
        ThreadLocalRandom.current().nextBytes(data);
        System.arraycopy(idData, 0, data, 0, idData.length);
        return data;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private Runnable pubWorker(Options options, WorkState ws) {
        return () -> {
            try (Connection nc = Nats.connect(options)) {
                JetStream js = nc.jetStream();
                printConnect(nc, publishJob, ws.workId, NO_TIX);
                jitter(publishJitter / 10);
                long lastSeq = -1;
                while (true) {
                    try {
                        String pubId = NUID.nextGlobal();
                        PublishOptions po = lastSeq == -1
                            ? PublishOptions.builder().build()
                            : PublishOptions.builder().expectedLastSequence(lastSeq).build();
                        PublishAck pa = js.publish(dataSubject, getData(pubId), po);
                        lastSeq = pa.getSeqno();

                        long count = ws.increment();
                        if (count % publishReportFrequency == 0) {
                            log(js, publishJob, ws.workId, NO_TIX, count, ws.elapse());
                        }
                        jitter(publishJitter);
                    }
                    catch (IOException | JetStreamApiException e) {
                        sleep(publishJitter);
                        lastSeq = -1;
                        log(js, publishJob, ws.workId, ws.elapse(), e);
                    }
                }
            }
            catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private Runnable orderedWorker(Options options, WorkState ws) {
        return () -> {
            try (Connection nc = Nats.connect(options)) {
                JetStream js = nc.jetStream();
                printConnect(nc, orderedJob, ws.workId, NO_TIX);
                jitter(orderedJitter / 10);
                while (true) {
                    try {
                        StreamContext ctx = nc.getStreamContext(dataStreamName);
                        StreamInfo si = ctx.getStreamInfo(StreamInfoOptions.builder().filterSubjects(dataSubject).build());
                        long available = si.getStreamState().getSubjectMap().get(dataSubject);
                        OrderedConsumerContext occ = ctx.createOrderedConsumer(new OrderedConsumerConfiguration());
                        long nextExpectedSequence = -1;
                        try (IterableConsumer it = occ.iterate()) {
                            Message m = it.nextMessage(1000);
                            while (m != null) {
                                long seq = m.metaData().streamSequence();
                                if (nextExpectedSequence != -1) {
                                    if (nextExpectedSequence != seq) {
                                        throw new FailureException("Ordered consumer returned incorrect sequence");
                                    }
                                }
                                nextExpectedSequence = seq + 1;
                                long count = ws.increment();
                                if (--available < 1) {
                                    log(js, orderedJob, ws.workId, NO_TIX, count, ws.elapse());
                                    break;
                                }
                                if (count % orderedReportFrequency == 0) {
                                    log(js, orderedJob, ws.workId, NO_TIX, count, ws.elapse());
                                }
                                m = it.nextMessage(1000);
                            }
                        }
                        catch (IOException | JetStreamApiException | FailureException e) {
                            throw e; // rethrow this
                        }
                        catch (Exception e) {
                            // auto closeable problem, ignore
                        }
                        jitter(orderedJitter);
                    }
                    catch (IOException | JetStreamApiException | FailureException e) {
                        sleep(orderedJitter);
                        log(js, orderedJob, ws.workId, ws.elapse(), e);
                    }
                }
            }
            catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
