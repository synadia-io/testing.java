package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NoOpStatistics;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.synadia.CommandLine;
import io.synadia.Params;
import io.synadia.Workload;
import io.synadia.chaos.DebugConnectionListener;
import io.synadia.chaos.OutputConnectionListener;
import io.synadia.chaos.TpsStatsCollector;
import io.synadia.utils.Debug;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.nats.client.support.JsonValueUtils.*;
import static io.nats.jsmulti.shared.Stats.*;

public class Tps extends Workload {
    private String action;

    private static final String TPS_SENDER = "TPS Sender";
    private static final String TPS_RECEIVER = "TPS Receiver";

    int targetTps;
    String subject;
    String messageIdKey;
    int payloadSize;
    int sendLogRate;
    int metricsInitialDelay;
    int metricsLogRate;

    @Override
    public void init(CommandLine commandLine) {
        this.workLabel = "TPS Workload";
        this.commandLine = commandLine;
        this.params = new Params(commandLine.paramsFiles);

        Debug.info("Environment", "JNats %s", Nats.CLIENT_VERSION);
        commandLine.debug();

        this.action = commandLine.action;

        targetTps = readInteger(params.jv, "target.tps", 10000);
        subject = JsonValueUtils.readString(params.jv, "subject", "tps");
        messageIdKey = JsonValueUtils.readString(params.jv, "message.id.key", "mid");
        payloadSize = readInteger(params.jv, "payload.size", 12 * 1024);
        sendLogRate = readInteger(params.jv, "send.log.rate", 5);
        metricsInitialDelay = readInteger(params.jv, "metrics.initial.delay", 5);
        metricsLogRate = readInteger(params.jv, "metrics.log.rate", 10);

        Debug.info(workLabel, "targetTps", targetTps);
        Debug.info(workLabel, "subject", subject);
        Debug.info(workLabel, "messageIdKey", messageIdKey);
        Debug.info(workLabel, "payloadSize", payloadSize);
        Debug.info(workLabel, "sendLogRate", sendLogRate);
        Debug.info(workLabel, "metricsLogRate", metricsLogRate);
    }

    @Override
    public void runWorkload() throws Exception {
        switch (action) {
            case "send" -> tpsSend();
            case "rec"  -> tpsReceive();
        }
    }

    AtomicLong lastSendMessageId = new AtomicLong(-1);
    AtomicLong sendMessageId = new AtomicLong();
    AtomicLong lastSendReportTime = new AtomicLong();
    TpsStatsCollector sendStatsCollector = new TpsStatsCollector();

    private void tpsSend() throws IOException, InterruptedException {
        Options options = buildOptions(params, 0, true, sendStatsCollector, null);
        try (Connection nc = Nats.connect(options)) {
            startSendLogging(nc);
            long startNanos = System.nanoTime();
            long currentSecond = 0;
            long messagesThisSecond = 0;
            long nextSecondStart = startNanos + 1_000_000_000L; // 1 second in nanos

            byte[] payload = new byte[payloadSize];
            Headers h = new Headers();
            while (true) {
                long now = System.nanoTime();
                // Check if we've moved to a new second
                if (now >= nextSecondStart) {
                    currentSecond++;
                    messagesThisSecond = 0;
                    nextSecondStart = startNanos + (currentSecond + 1) * 1_000_000_000L;
                }

                // Only send if we haven't hit the target for this second
                if (messagesThisSecond < targetTps) {
                    try {
                        h.put(messageIdKey, sendMessageId.incrementAndGet() + "");
                        nc.publish(subject, h, payload);
                        messagesThisSecond++;

                        // Calculate sleep time to maintain even distribution
                        long remainingInSecond = nextSecondStart - System.nanoTime();
                        long remainingMessages = targetTps - messagesThisSecond;
                        if (remainingMessages > 0 && remainingInSecond > 0) {
                            long sleepTime = remainingInSecond / (remainingMessages + 1);
                            if (sleepTime > 100_000) { // Only sleep if more than 100 microseconds
                                sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                            }
                        }
                    }
                    catch (Exception e) {
                        Debug.info(TPS_SENDER, "Error sending message id %s during test: %s", sendMessageId.get(), e.getMessage());
                        sendMessageId.decrementAndGet();
                        sleep(1000);
                    }
                }
                else {
                    // Wait for next second if we've hit the target for this second
                    long sleepTime = nextSecondStart - System.nanoTime();
                    if (sleepTime > 0) {
                        sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                    }
                }
            }
        }
    }

    private void startSendLogging(Connection nc) {
        nc.getOptions().getScheduledExecutor().scheduleAtFixedRate(() -> {
            long reportTime = System.currentTimeMillis();
            long elapsedMs = reportTime - lastSendReportTime.get();
            long sent = sendMessageId.get();
            if (lastSendMessageId.get() == -1) {
                Debug.info(TPS_SENDER, "Last Message Id %s", sent);
            }
            else {
                long diff = sent - lastSendMessageId.get();
                Debug.info(TPS_SENDER, "Last Id %s", sent, "Messages %s", diff,
                    "Per Sec %s", format3NoGrouping(1000f * diff /elapsedMs),
                    FORMATTER.format(sendStatsCollector.getOutMsgs()),
                    FORMATTER.format(sendStatsCollector.getOutBytes()),
                    FORMATTER.format(sendStatsCollector.getWriteBytes())
                );
            }
            lastSendMessageId.set(sent);
            lastSendReportTime.set(reportTime);
        }, sendLogRate, sendLogRate, TimeUnit.SECONDS);
    }

    AtomicLong receivedMessages = new AtomicLong(0);
    AtomicLong receivedLastCurrentCount = new AtomicLong(0);
    AtomicLong receivedLastMessageId = new AtomicLong(-1);
    AtomicLong receivedTotalLost = new AtomicLong(0);
    AtomicLong receivedLosses = new AtomicLong(0);
    AtomicLong receivedTotalLostAfterConn = new AtomicLong(0);
    AtomicLong receivedLossesAfterConn = new AtomicLong(0);
    AtomicBoolean connectionEvent = new AtomicBoolean(false);

    private void tpsReceive() throws IOException, InterruptedException {
        int firstServerIx = commandLine.args.isEmpty() ? 1 : Integer.parseInt(commandLine.args.getFirst());
        Options options = buildOptions(params, firstServerIx, false, new NoOpStatistics(), (c, et, t, d) -> {
            receivedLastMessageId.set(-1);
            connectionEvent.set(true);
        });
        try (Connection nc = Nats.connect(options)) {
            startMetricsLogging(nc);
            MessageHandler handler = msg -> {
                if (msg.getData().length != this.payloadSize) {
                    Debug.info(TPS_RECEIVER, "Unexpected payload size: %s B (expected: %s B)", msg.getData().length, this.payloadSize);
                }
                //noinspection DataFlowIssue // headers won't be null.
                long mid = Long.parseLong(msg.getHeaders().getFirst(messageIdKey));
                long expected = receivedLastMessageId.incrementAndGet();
                if (expected == 0) {
                    receivedLastMessageId.set(mid);
                }
                else if (mid != expected) {
                    long diff = mid - expected;
                    if (diff > 0) {
                        receivedLastMessageId.set(-1);
                        String mark;
                        long totalLosses;
                        long numLosses;
                        if (connectionEvent.get()) {
                            mark = "****** After Connection Event";
                            totalLosses = receivedTotalLostAfterConn.addAndGet(diff);
                            numLosses = receivedLossesAfterConn.incrementAndGet();
                            connectionEvent.set(false);
                        }
                        else {
                            mark = "******";
                            totalLosses = receivedTotalLost.addAndGet(diff);
                            numLosses = receivedLosses.incrementAndGet();
                        }
                        Debug.info(TPS_RECEIVER, mark
                            , "Got Message Id: %s but expected: %s", format3(mid), format3(expected)
                            , "Loss of %s", format3(diff)
                            , "Average Loss of %s", format3((float) totalLosses / numLosses));
                    }
                    else {
                        receivedLastMessageId.set(mid);
                    }
                }
                receivedMessages.incrementAndGet();
            };

            Dispatcher currentDispatcher = nc.createDispatcher();

            // Subscribe with high-throughput settings
            Subscription subscription = currentDispatcher.subscribe(subject, handler);//, queueGroup);
            Debug.info(TPS_RECEIVER, "Started continuous listening on subject: %s (target: %s TPS, payload: %s B)", subject, targetTps, this.payloadSize);
            Thread.currentThread().join();
        }
    }

    private void startMetricsLogging(Connection nc) {
        nc.getOptions().getScheduledExecutor().scheduleAtFixedRate(() -> {
            long currentCount = receivedMessages.get();
            long previousCount = receivedLastCurrentCount.getAndSet(currentCount);
            long currentTps = (currentCount - previousCount) / metricsLogRate;
            Debug.info(TPS_RECEIVER, "Receive TPS: %s/%s", currentTps, targetTps, "Total Messages %s", format3(currentCount));
//            Debug.info(TPS_RECEIVER, "Receive TPS: %s/%s", currentTps, targetTps,
//                "Total Messages %s", currentCount,
//                "%s %s", statusMessage(nc.getStatus()), nc.getConnectedUrl());
            // Log performance warning if TPS is significantly below target (only if actively receiving)
//            if (currentTps > 0 && currentTps < targetTps * 0.8) {
//                Debug.info(TPS_RECEIVER, "Performance below target: %s/%s", currentTps, targetTps);
//            }
        }, metricsInitialDelay, metricsLogRate, TimeUnit.SECONDS);
    }

    @SuppressWarnings("SameParameterValue")
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis, int nanos) {
        try {
            Thread.sleep(millis, nanos);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Options buildOptions(Params params, int firstServerIx, boolean sender,
                                        StatisticsCollector collector,
                                        OutputConnectionListener.CustomFunction behavior) {
        String label = sender ? TPS_SENDER : TPS_RECEIVER;
        JsonValue jv = params.jv;
        DebugConnectionListener dbcl = new DebugConnectionListener(true);
        dbcl.afterFunction(behavior);

        String[] servers = figureServers(params.servers, firstServerIx);

        Options.Builder builder  = new Options.Builder()
            .servers(servers)
            .ignoreDiscoveredServers()
            .noRandomize()
            .statisticsCollector(collector)
            .connectionListener(dbcl)
//            .errorListener(new DebugErrorListener())
            .errorListener(new ErrorListener() {})
            .connectionTimeout(readLong(jv, "nats.connection.timeout.millis", 5000))
            .maxReconnects(readInteger(jv, "nats.max.reconnects", -1))
            .reconnectBufferSize(readLong(jv, "nats.connection.max.buffer", 500000000))
            .bufferSize(readInteger(jv, "nats.connection.io.buffer", Options.DEFAULT_BUFFER_SIZE))
            .maxMessagesInOutgoingQueue(readInteger(jv, "nats.connection.outgoing.max.messages", Options.DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE));

        // Conditionally set receive buffer size based on separate flag
        boolean receiveBufferEnabled = readBoolean(jv, "nats.receive.buffer.enabled", false);
        if (receiveBufferEnabled) {
            builder.receiveBufferSize(readInteger(jv, "nats.connection.receive.buffer", 87380));
        }

        // Conditionally set send buffer size based on separate flag
        boolean sendBufferEnabled = readBoolean(jv, "nats.send.buffer.enabled", false);
        if (sendBufferEnabled) {
            builder.sendBufferSize(readInteger(jv, "nats.connection.send.buffer", 16384));
        }

        long millis = readLong(jv, "nats.reconnect.wait.millis", -1);
        if (millis != -1) {
            builder.reconnectWait(Duration.ofMillis(millis));
        }
        millis = readLong(jv, "nats.ping.interval.millis", -1);
        if (millis != -1) {
            builder.pingInterval(Duration.ofMillis(millis));
        }

        builder.maxPingsOut(readInteger(jv, "nats.connection.max.ping.out", 2));

        millis = readLong(jv, "nats.socket.write.timeout.millis", -1);
        if (millis != -1) {
            builder.socketWriteTimeout(millis);
        }

        millis = readLong(jv, "nats.socket.read.timeout.millis", -1);
        if (millis != -1) {
            builder.socketReadTimeoutMillis((int)millis);
        }

        Options o = builder.build();
        Debug.info(label, "server", o.getServers());
        Debug.info(label, "connectionTimeout", o.getConnectionTimeout());
        Debug.info(label, "maxReconnects", o.getMaxReconnect());
        Debug.info(label, "reconnectBufferSize", o.getReconnectBufferSize());
        Debug.info(label, "bufferSize", o.getBufferSize());
        Debug.info(label, "maxMessagesInOutgoingQueue", o.getMaxMessagesInOutgoingQueue());

        Debug.info(label, "receiveBufferSize", o.getReceiveBufferSize());
        Debug.info(label, "sendBufferSize", o.getSendBufferSize());
        Debug.info(label, "reconnectWait", o.getReconnectWait());
        Debug.info(label, "pingInterval", o.getPingInterval());
        Debug.info(label, "maxPingsOut", o.getMaxPingsOut());
        Debug.info(label, "socketWriteTimeout", o.getSocketWriteTimeout());
        Debug.info(label, "socketReadTimeoutMillis", o.getSocketReadTimeoutMillis());

        return o;
    }

    private static String[] figureServers(List<String> paramsServers, int firstServerIx) {
        String firstServer = paramsServers.get(firstServerIx);
        List<String> ordered = new ArrayList<>(paramsServers);
        ordered.remove(firstServer);
        Collections.shuffle(ordered);
        ordered.addFirst(firstServer);
        return ordered.toArray(new String[0]);
    }
}
