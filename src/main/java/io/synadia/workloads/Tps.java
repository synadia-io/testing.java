package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NoOpStatistics;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.synadia.CommandLine;
import io.synadia.Params;
import io.synadia.Workload;
import io.synadia.utils.Debug;
import io.synadia.workloads.tps.TpsConnectionListener;
import io.synadia.workloads.tps.TpsErrorListener;
import io.synadia.workloads.tps.TpsStatsCollector;
import io.synadia.workloads.tps.TpsWriteListener;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static io.nats.client.support.JsonValueUtils.*;
import static io.nats.jsmulti.shared.Stats.format3;
import static io.synadia.workloads.tps.TpsUtils.*;

public class Tps extends Workload {

    private static final String TPS_SENDER = "SENDER";
    private static final String TPS_RECEIVER = "RECEIVER";

    // Common
    String action;
    int targetTps;
    String subject;
    int payloadSize;

    List<String> sendResults = new ArrayList<>();
    List<String> receiveResults = new ArrayList<>();

    @Override
    public void init(CommandLine commandLine) {
        this.workLabel = "TPS";
        this.commandLine = commandLine;
        this.params = new Params(commandLine.paramsFiles);

        Debug.info("Environment", "JNats %s", Nats.CLIENT_VERSION);
        commandLine.debug();

        this.action = commandLine.action;

        targetTps = readInteger(params.jv, "target.tps", 10000);
        subject = JsonValueUtils.readString(params.jv, "subject", "tps");
        MESSAGE_ID_KEY = JsonValueUtils.readString(params.jv, "message.id.key", "mid");
        payloadSize = readInteger(params.jv, "payload.size", 12 * 1024);

        reportApplicationOptions(workLabel);
    }

    @Override
    public void runWorkload() throws Exception {
        switch (action) {
            case "send": send(); break;
            case "rec": receive(); break;
            case "both":
                // start the receiver first
                Thread r = new Thread(() -> {
                    try { receive(); } catch (Exception ignored) {}
                });
                r.start();

                sleep(100); // give time to make sure receive is started
                Thread s = new Thread(() -> {
                    try { send(); } catch (Exception ignored) {}
                });
                s.start();

                s.join();
                r.join();
        }

        sleep(100); // give callbacks time to finish
        if (receiveResults.size() > 0) {
            for (String r : receiveResults) {
                System.out.println(r);
            }
        }
        if (sendResults.size() > 0) {
            for (String r : sendResults) {
                System.out.println(r);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Sender
    // ----------------------------------------------------------------------------------------------------
    AtomicLong pubId;
    TpsStatsCollector sendStats;
    TpsWriteListener sendWL;
    TpsConnectionListener sendCL;
    TpsErrorListener sendEL;

    private void send() throws IOException, InterruptedException {
        pubId = new AtomicLong();
        sendStats = new TpsStatsCollector(payloadSize);
        sendWL = new TpsWriteListener(TPS_SENDER);

        sendCL = new TpsConnectionListener(TPS_SENDER);
        sendEL = new TpsErrorListener(TPS_SENDER);

        Options options = buildOptions(0)
            .writeListener(sendWL)
            .statisticsCollector(sendStats)
            .connectionListener(sendCL)
            .errorListener(sendEL)
            .build();

        reportConnectionOptions(TPS_SENDER, options);

        try (Connection nc = Nats.connect(options)) {

            byte[] payload = new byte[payloadSize];
            Headers h = new Headers();

            long currentSecond = -1;
            long messagesThisSecond = 0;
            long nextSecondStart = -1;
            long startNanos = System.nanoTime();

            while (nc.getStatus() == Connection.Status.CONNECTED && !sendEL.readClosed && !sendCL.disconnected)
            {
                // Check if we've moved to a new second
                long now = System.nanoTime();
                if (now >= nextSecondStart) {
                    if (messagesThisSecond > 0) {
                        Debug.info(TPS_SENDER, "Messages Last Second: " + messagesThisSecond);
                    }
                    currentSecond++;
                    messagesThisSecond = 0;
                    nextSecondStart = startNanos + ((currentSecond + 1) * 1_000_000_000L);
                }

                // Only send if we haven't hit the target for this second
                if (messagesThisSecond < targetTps) {
                    try {
                        h.put(MESSAGE_ID_KEY, pubId.incrementAndGet() + "");
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
                        Debug.info(TPS_SENDER, "Error sending message id %s during test: %s", pubId.get(), e.getMessage());
                        pubId.decrementAndGet();
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
            sendStats.startPhase2();
            sendWL.startPhase2();
            // publish the end marker for the receiver
            Debug.info(TPS_SENDER, "Publishing end marker message");
            nc.publish(subject, null);

            Debug.info(TPS_SENDER, "Waiting for %s queued messages to be sent...", nc.outgoingPendingMessageCount());
            while (nc.outgoingPendingMessageCount() > 0) {
                sleep(10);
            }

            sendResults.add("\n" + TPS_SENDER);
            sendResults.add("Before Disconnect...");
//            results.add(Debug.stringify("  Total Buffered Messages: %s", sendStats.getOutMsgs()));
//            results.add(Debug.stringify("  Total Buffered Bytes: %s", format3(sendStats.getOutBytes())));
            sendResults.add(Debug.stringify("  Total Socket Written Bytes: %s", format3(sendStats.getWriteBytes())));
            sendResults.add(Debug.stringify("  Total Buffered Payload Messages: %s", sendStats.getPayloadMsgs()));
            sendResults.add(Debug.stringify("  Total Buffered Payload Bytes: %s", format3(sendStats.getPayloadBytes())));
            sendResults.add(Debug.stringify("  Last Message Id Buffered: %s", sendWL.getLastBufferedMessageId()));
            sendResults.add("After Disconnect...");
//            results.add(Debug.stringify("  Total Buffered Messages: %s", sendStats.getAfterBufferedMsgs()));
//            results.add(Debug.stringify("  Total Buffered Bytes: %s", format3(sendStats.getAfterBufferedBytes())));
            sendResults.add(Debug.stringify("  Total Buffered Payload Messages: %s", sendStats.getAfterPayloadMsgs()));
            sendResults.add(Debug.stringify("  Total Buffered Payload Bytes: %s", format3(sendStats.getAfterPayloadBytes())));
            sendResults.add("Analysis ...");
//            results.add(Debug.stringify("  Total Published Messages: %s", pubId.get()));
//            sendResults.add(Debug.stringify("  Diff (Buffered - Written) Bytes: %s", format3(sendStats.outDiff())));
//            sendResults.add(Debug.stringify("  Diff Approximate Messages: %s", sendStats.approximateDiffMessages()));
            sendResults.add(Debug.stringify("  Last Write Messages: %s", sendStats.getLastWriteMessages()));
            sendResults.add(Debug.stringify("  Last Write Bytes: %s", format3(sendStats.getLastWriteBytes())));
            sendResults.add(Debug.stringify("  Buffered Not Written Messages: %s", sendStats.getNotWrittenMessages()));
            sendResults.add(Debug.stringify("  Buffered Not Written Bytes: %s", format3(sendStats.getNotWrittenBytes())));

            List<String> skipList = sendWL.getGapList();
            if (skipList.isEmpty()) {
                sendResults.add("  No Writer Gaps");
            }
            else {
                sendResults.add("  Writer Gaps");
                for (String s : skipList) {
                    sendResults.add(" " + s);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Receiver
    // ----------------------------------------------------------------------------------------------------
    AtomicLong receivedMessages = new AtomicLong(0);
    AtomicLong receivedLastMessageId = new AtomicLong(-1);
    AtomicLong receiveGap = new AtomicLong(0);
    CountDownLatch doneLatch = new CountDownLatch(1);
    TpsConnectionListener receiveCL;
    TpsErrorListener receiveEL;

    private void receive() throws IOException, InterruptedException {
        int firstServerIx = commandLine.args.isEmpty() ? 1 : Integer.parseInt(commandLine.args.getFirst());
        receiveCL = new TpsConnectionListener(TPS_RECEIVER);
        receiveEL = new TpsErrorListener(TPS_RECEIVER);

        Options options = buildOptions(firstServerIx)
            .statisticsCollector(new NoOpStatistics())
            .connectionListener(receiveCL)
            .errorListener(receiveEL)
            .build();

        reportConnectionOptions(TPS_RECEIVER, options);

        try (Connection nc = Nats.connect(options)) {
            MessageHandler handler = msg -> {
                if (doneLatch.getCount() == 0) {
                    return;
                }

                int dlen = msg.getData().length;
                if (dlen == 0) {
                    doneLatch.countDown();
                    return;
                }

                //noinspection DataFlowIssue this will never return null
                long mid = extractMessageId(msg);
                long rcvd = receivedMessages.incrementAndGet();
                if (rcvd == 1) {
                    receivedLastMessageId.set(mid);
                    Debug.info(TPS_RECEIVER, "Started Receiving...");
                    return;
                }

                long expected = receivedLastMessageId.incrementAndGet();
                receivedLastMessageId.set(mid);
                if (mid != expected) {
                    long diff = mid - expected;
                    receiveGap.set(diff);
                    Debug.info(TPS_RECEIVER, "******"
                        , "Got Message Id: %s but expected: %s", format3(mid), format3(expected)
                        , "Loss of %s", format3(diff));
                }
            };

            Dispatcher currentDispatcher = nc.createDispatcher();

            // Subscribe with high-throughput settings
            Subscription subscription = currentDispatcher.subscribe(subject, handler);//, queueGroup);

            doneLatch.await();
            receiveResults.add("\n" + TPS_RECEIVER);
            receiveResults.add(Debug.stringify("  Total Received Messages: %s", receivedMessages.get()));
            receiveResults.add(Debug.stringify("  Total Receive Gap: %s", receiveGap.get()));
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------
    public Options.Builder buildOptions(int firstServerIx) {
        JsonValue jv = params.jv;

        String[] servers = figureServers(params.servers, firstServerIx);

        int mmiq = Math.max(targetTps, readInteger(jv, "nats.connection.outgoing.max.messages", Options.DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE));

        Options.Builder builder  = new Options.Builder()
            .servers(servers)
            .ignoreDiscoveredServers()
            .noRandomize()
            .connectionTimeout(readLong(jv, "nats.connection.timeout.millis", 5000))
            .maxReconnects(readInteger(jv, "nats.max.reconnects", -1))
            .reconnectBufferSize(readLong(jv, "nats.connection.max.buffer", 500000000))
            .bufferSize(readInteger(jv, "nats.connection.io.buffer", Options.DEFAULT_BUFFER_SIZE))
            .maxMessagesInOutgoingQueue(mmiq);

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

        return builder;
    }

    public static String[] figureServers(List<String> paramsServers, int firstServerIx) {
        String firstServer = paramsServers.get(firstServerIx);
        List<String> ordered = new ArrayList<>(paramsServers);
        ordered.remove(firstServer);
        Collections.shuffle(ordered);
        ordered.addFirst(firstServer);
        return ordered.toArray(new String[0]);
    }

    private void reportApplicationOptions(String label) {
        Debug.info(label, "----- Application Options -----");
        Debug.info(label, "targetTps", targetTps);
        Debug.info(label, "subject", subject);
        Debug.info(label, "messageIdKey", MESSAGE_ID_KEY);
        Debug.info(label, "payloadSize", payloadSize);
    }

    private void reportConnectionOptions(String label, Options o) {
        Debug.info(label, "----- Connection Options -----");
        Debug.info(label, "servers", o.getServers());
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
    }
}
