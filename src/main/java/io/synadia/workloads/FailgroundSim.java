package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.*;
import io.nats.client.support.JsonValueUtils;
import io.nats.jsmulti.shared.FailureException;
import io.synadia.CommandLine;
import io.synadia.utils.Debug;
import io.synadia.workloads.support.WorkContext;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

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
    private void pubWorker(WorkContext wctx) {
        long lastSeq = -1;
        while (true) {
            try {
                String pubId = NUID.nextGlobal();
                PublishOptions po = lastSeq == -1
                    ? PublishOptions.builder().build()
                    : PublishOptions.builder().expectedLastSequence(lastSeq).build();
                PublishAck pa = wctx.js.publish(dataSubject, getData(pubId), po);
                lastSeq = pa.getSeqno();

                long count = wctx.ws.increment();
                if (count % publishReportFrequency == 0) {
                    log(publishJob, wctx, count);
                }
                jitter(publishJitter);
            }
            catch (IOException | JetStreamApiException e) {
                sleepThenLog(publishJob, publishJitter, wctx, e);
                lastSeq = -1; // clean start
            }
        }
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void orderedWorker(WorkContext wctx) {
        while (true) {
            try {
                StreamContext ctx = wctx.nc.getStreamContext(dataStreamName);
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
                        long count = wctx.ws.increment();
                        if (--available < 1) {
                            log(orderedJob, wctx, count);
                            break;
                        }
                        if (count % orderedReportFrequency == 0) {
                            log(orderedJob, wctx, count);
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
                sleepThenLog(orderedJob, orderedJitter, wctx, e);
            }
        }
    }
}
