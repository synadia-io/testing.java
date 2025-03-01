package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonSerializable;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.synadia.CommandLine;
import io.synadia.utils.Debug;
import io.synadia.workloads.support.Event;
import io.synadia.workloads.support.WorkContext;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nats.jsmulti.shared.Utils.sleep;
import static io.synadia.utils.Commons.NO_TIX;
import static io.synadia.utils.Commons.generateName;

public class ConsumerInfoSim extends AbstractCustomWorkload {
    private String dataStreamName;
    private String dataSubjectPrefix;
    private String dataStreamSubject;
    private long dataMaxMessages;

    private String queueStreamName;
    private String queueSubject;
    private String queueConsumerName;
    private long queueMaxMessages;

    private int maxConsumers;

    private String produceJob;
    private int produceThreadCount;
    private long produceReportFrequency;
    private long produceJitter;
    private int produceMessageMin;
    private int produceMessageMax;

    private String consumeJob;
    private int consumeThreadCount;
    private long consumeReportFrequency;
    private long consumeJitter;
    private int consumeBatch;

    private String infoJob;
    private int infoThreadCount;
    private long infoReportFrequency;
    private long infoJitter;

    @Override
    public void init(CommandLine commandLine) {
        init("Consumer Info Sim", commandLine);

        dataStreamName = JsonValueUtils.readString(params.jv, "data_stream_name", "data");
        queueStreamName = JsonValueUtils.readString(params.jv, "queue_stream_name", "queue");

        initCustom(
            new String[]{"list (consumers)", "produce", "consume", "info", "combo", "clear (consumers)"},
            new String[]{dataStreamName, queueStreamName}
        );

        dataSubjectPrefix = JsonValueUtils.readString(params.jv, "data_subject_prefix", "data.");
        dataStreamSubject = JsonValueUtils.readString(params.jv, "data_stream_subject", "data.>");
        dataMaxMessages = JsonValueUtils.readLong(params.jv, "data_max_messages", 1_000_000);
        Debug.info(workLabel, "dataStreamName", dataStreamName);
        Debug.info(workLabel, "dataSubjectPrefix", dataSubjectPrefix);
        Debug.info(workLabel, "dataStreamSubject", dataStreamSubject);
        Debug.info(workLabel, "dataMaxMessages", dataMaxMessages);

        queueSubject = JsonValueUtils.readString(params.jv, "queue_subject", "qsub");
        queueConsumerName = JsonValueUtils.readString(params.jv, "queue_consumer_name", "qcon");
        queueMaxMessages = JsonValueUtils.readLong(params.jv, "queue_max_messages", 1_000_000);
        Debug.info(workLabel, "queueStreamName", queueStreamName);
        Debug.info(workLabel, "queueSubject", queueSubject);
        Debug.info(workLabel, "queueConsumerName", queueConsumerName);
        Debug.info(workLabel, "queueMaxMessages", queueMaxMessages);

        maxConsumers = JsonValueUtils.readInteger(params.jv, "max_consumers", 10_000);
        Debug.info(workLabel, "maxConsumers", maxConsumers);

        produceJob = JsonValueUtils.readString(params.jv, "produce_job", "Produce");
        produceThreadCount = JsonValueUtils.readInteger(params.jv, "produce_thread_count", 3);
        produceReportFrequency = JsonValueUtils.readLong(params.jv, "produce_report_frequency", 100);
        produceJitter = JsonValueUtils.readLong(params.jv, "produce_jitter", 1000);
        produceMessageMin = JsonValueUtils.readInteger(params.jv, "produce_message_min", 10);
        produceMessageMax = JsonValueUtils.readInteger(params.jv, "produce_message_max", 100);
        Debug.info(workLabel, "produceJob", produceJob);
        Debug.info(workLabel, "produceThreadCount", produceThreadCount);
        Debug.info(workLabel, "produceReportFrequency", produceReportFrequency);
        Debug.info(workLabel, "produceJitter", produceJitter);
        Debug.info(workLabel, "produceMessageMin", produceMessageMin);
        Debug.info(workLabel, "produceMessageMax", produceMessageMax);

        consumeJob = JsonValueUtils.readString(params.jv, "consume_job", "Consume");
        consumeThreadCount = JsonValueUtils.readInteger(params.jv, "consume_thread_count", 3);
        consumeReportFrequency = JsonValueUtils.readLong(params.jv, "consume_report_frequency", 100);
        consumeJitter = JsonValueUtils.readLong(params.jv, "consume_jitter", 500);
        consumeBatch = JsonValueUtils.readInteger(params.jv, "consume_batch", 10);
        Debug.info(workLabel, "consumeJob", consumeJob);
        Debug.info(workLabel, "consumeThreadCount", consumeThreadCount);
        Debug.info(workLabel, "consumeReportFrequency", consumeReportFrequency);
        Debug.info(workLabel, "consumeJitter", consumeJitter);
        Debug.info(workLabel, "consumeBatch", consumeBatch);

        infoJob = JsonValueUtils.readString(params.jv, "info_job", "Info");
        infoThreadCount = JsonValueUtils.readInteger(params.jv, "info_thread_count", 8);
        infoReportFrequency = JsonValueUtils.readLong(params.jv, "info_report_frequency", 1000);
        infoJitter = JsonValueUtils.readLong(params.jv, "info_jitter", 10_000);
        Debug.info(workLabel, "infoJob", infoJob);
        Debug.info(workLabel, "infoThreadCount", infoThreadCount);
        Debug.info(workLabel, "infoReportFrequency", infoReportFrequency);
        Debug.info(workLabel, "infoJitter", infoJitter);
    }

    @Override
    protected boolean subRunWorkload(String arg) throws Exception {
        switch (arg) {
            case "list"    -> doListConsumers();
            case "produce" -> doWorker(produceJob, produceThreadCount, this::produceWorker);
            case "consume" -> doWorker(consumeJob, consumeThreadCount, this::consumeWorker);
            case "info"    -> doWorker(infoJob, infoThreadCount, this::infoWorker);
            case "clear"   -> doClearConsumers();
            default        -> { return false; }
        }
        return true;
    }

    @Override
    protected void subDoSetup(Connection nc, JetStreamManagement jsm) throws IOException, JetStreamApiException, InterruptedException {
        addStream(jsm, StreamConfiguration.builder()
            .name(dataStreamName)
            .subjects(dataStreamSubject)
            .retentionPolicy(RetentionPolicy.WorkQueue)
            .maxMessages(dataMaxMessages)
            .build());

        addStream(jsm, StreamConfiguration.builder()
            .name(queueStreamName)
            .subjects(queueSubject)
            .retentionPolicy(RetentionPolicy.WorkQueue)
            .maxMessages(queueMaxMessages)
            .build());

        jsm.createConsumer(queueStreamName, ConsumerConfiguration.builder().durable(queueConsumerName).filterSubject(queueSubject).build());
    }

    private void doListConsumers() throws IOException, JetStreamApiException, InterruptedException {
        doAdmin(wctx -> {
            startProgressJob("List Consumers");
            List<String> consumerNames = wctx.jsm.getConsumerNames(dataStreamName);
            consumerNames.forEach(cn -> System.out.println("Consumer: " + cn));
            System.out.println("Total: " + consumerNames.size());
        });
    }

    private void doClearConsumers() throws IOException, JetStreamApiException, InterruptedException {
        doAdmin(wctx -> {
            startProgressJob("Clear Consumers");
            List<String> list = wctx.jsm.getConsumerNames(dataStreamName);
            int index = 0;
            while (index < list.size()) {
                String cn = list.get(index);
                wctx.jsm.deleteConsumer(dataStreamName, cn);
                showProgressMaybe(++index, "Clear Consumers");
            }
            endProgress(index);
        });
    }

    static class QueueData implements JsonSerializable {
        public final String consumerName;
        public final String dataSubject;
        public final int messageCount;

        @Override
        public String toString() {
            return "QueueData{" +
                "consumerName='" + consumerName + '\'' +
                ", dataSubject='" + dataSubject + '\'' +
                ", messageCount=" + messageCount +
                '}';
        }

        public QueueData(String consumerName, String dataSubject, int messageCount) {
            this.consumerName = consumerName;
            this.dataSubject = dataSubject;
            this.messageCount = messageCount;
        }

        public QueueData(byte[] jsonBytes) {
            JsonValue jv = JsonParser.parseUnchecked(jsonBytes);
            this.consumerName = JsonValueUtils.readString(jv, "consumer_name");
            this.dataSubject = JsonValueUtils.readString(jv, "data_subject");
            this.messageCount = JsonValueUtils.readInteger(jv, "message_count", 0);
        }

        @Override
        public String toJson() {
            JsonValueUtils.MapBuilder mb = JsonValueUtils.mapBuilder();
            mb.put("consumer_name", consumerName);
            mb.put("data_subject", dataSubject);
            mb.put("message_count", messageCount);
            return mb.jv.toJson();
        }
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void produceWorker(WorkContext wctx) {
        AtomicInteger cutoff = new AtomicInteger(maxConsumers);
        boolean reachedCutoff = false;
        while (true) {
            try {
                StreamInfo si = wctx.jsm.getStreamInfo(dataStreamName);
                long siCount = si.getStreamState().getConsumerCount();
                int co = cutoff.get();
                if (siCount >= co) {
                    if (co == maxConsumers) { // just to avoid repeat printing when full
                        cutoff.set(maxConsumers * 10 / 100); // 10 percent
                        print(produceJob, wctx, "* System is full. " + siCount + "/" + maxConsumers);
                        Event event = new Event(this, produceJob, wctx.ws.workId, NO_TIX, null, -wctx.get(), wctx.elapse(), null);
                        publish(wctx.js, event);
                    }
                    sleep(produceJitter); // extra full sleep since it's full
                    reachedCutoff = true;
                }
                else {
                    cutoff.set(maxConsumers);
                    String consumerName = generateName();
                    String dataSubject = toDataSubject(consumerName);
                    int messageCount = ThreadLocalRandom.current().nextInt(produceMessageMin, produceMessageMax + 1);

                    // 1. create the consumer
                    wctx.jsm.createConsumer(dataStreamName, ConsumerConfiguration.builder()
                        .durable(consumerName)
                        .filterSubject(dataSubject)
                        .build());

                    // 2. publish messages
                    for (int i = 0; i < messageCount; i++) {
                        wctx.js.publish(dataSubject, null);
                    }

                    // 3. put a record in the queue last so it's not used until messages are published
                    wctx.js.publish(queueSubject, new QueueData(consumerName, dataSubject, messageCount).serialize());

                    long count = wctx.increment();
                    if (count % produceReportFrequency == 0) {
                        logNoTix(produceJob, wctx, count);
                    }
                }
                if (reachedCutoff) {
                    jitter(produceJitter);
                }
            }
            catch (IOException | JetStreamApiException e) {
                sleepThenLog(produceJob, produceJitter, wctx, e);
            }
        }
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void consumeWorker(WorkContext wctx) {
        ConsumerContext qConsumerCtx = null;
        while (true) {
            try {
                if (qConsumerCtx == null) {
                    qConsumerCtx = wctx.nc.getConsumerContext(queueStreamName, queueConsumerName);
                }
                QueueData qd = queueNext(qConsumerCtx);
                if (qd != null) {
                    StreamContext sctx = wctx.nc.getStreamContext(dataStreamName);
                    ConsumerContext cctx = sctx.getConsumerContext(qd.consumerName);
                    try (FetchConsumer fc = cctx.fetch(FetchConsumeOptions.builder().maxMessages(consumeBatch).noWait().build())) {
                        Message m = fc.nextMessage();
                        while (m != null) {
                            m.ack();
                            m = fc.nextMessage();
                        }
                        long count = wctx.increment();
                        if (count % consumeReportFrequency == 0) {
                            logNoTix(consumeJob, wctx, count);
                        }
                    }
                    catch (IOException | JetStreamApiException e) {
                        log(consumeJob, wctx, e);
                    }
                    finally {
                        try {
                            wctx.jsm.deleteConsumer(dataStreamName, qd.consumerName);
                        }
                        catch (Exception e) {
                            log(consumeJob, wctx, e);
                        }
                        try {
                            wctx.jsm.purgeStream(dataStreamName, PurgeOptions.subject(qd.dataSubject));
                        }
                        catch (Exception e) {
                            log(consumeJob, wctx, e);
                        }
                    }
                }
                jitter(consumeJitter);
            }
            catch (Exception e) {
                sleepThenLog(consumeJob, consumeJitter, wctx, e);
                qConsumerCtx = null; // clean start
            }
        }
    }

    private QueueData queueNext(ConsumerContext qConsumerCtx) throws JetStreamApiException, IOException, InterruptedException, JetStreamStatusCheckedException {
        Message qm = qConsumerCtx.next(1000);
        if (qm != null) {
            qm.ack();
            return new QueueData(qm.getData());
        }
        return null;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void infoWorker(WorkContext wctx) {
        long ownCount = 0;
        while (true) {
            try {
                List<String> consumerNames = wctx.jsm.getConsumerNames(dataStreamName);
                Collections.shuffle(consumerNames);
                for (String consumerName : consumerNames) {
                    try {
                        wctx.jsm.getConsumerInfo(dataStreamName, consumerName);
                        long groupCount = wctx.ws.increment();
                        if (groupCount % infoReportFrequency == 0) {
                            log(infoJob, wctx, groupCount);
                        }
                        if (++ownCount % infoReportFrequency == 0) {
                            logNoConsole(infoJob, wctx, ownCount);
                        }
                    }
                    catch (IOException | JetStreamApiException e) {
                        if (!e.getMessage().contains("10014")) { // it's fine the consumer is missing
                            log(infoJob, wctx, e);
                        }
                    }
                }
                jitter(infoJitter);
            }
            catch (IOException | JetStreamApiException e) {
                sleepThenLog(infoJob, infoJitter, wctx, e);
            }
        }
    }

    private String toDataSubject(String consumerName) {
        return dataSubjectPrefix + consumerName;
    }
}
