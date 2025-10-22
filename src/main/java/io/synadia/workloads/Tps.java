package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NoOpStatistics;
import io.nats.client.impl.TpsWriteListener;
import io.nats.client.support.JsonValue;
import io.synadia.CommandLine;
import io.synadia.Params;
import io.synadia.Workload;
import io.synadia.utils.Debug;
import io.synadia.workloads.tps.TpsConnectionListener;
import io.synadia.workloads.tps.TpsErrorListener;
import io.synadia.workloads.tps.TpsStatsCollector;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.nats.client.support.JsonValueUtils.*;
import static io.nats.jsmulti.shared.Stats.format3;
import static io.synadia.utils.Debug.stringify;
import static io.synadia.workloads.tps.TpsUtils.*;

public class Tps extends Workload {

    private static final String TPS_SENDER = "SENDER";
    private static final String TPS_RECEIVER = "RECEIVER";
    private static final String TEST_SUBJECT = "test";
    private static final String CONTROL_SUBJECT = "control";

    // Common
    String action;
    int targetTps;
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
        payloadSize = readInteger(params.jv, "payload.size", 12 * 1024);

        reportApplicationOptions();
        reportSocketBufferSize();
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

                Thread s = new Thread(() -> {
                    try { send(); } catch (Exception ignored) {}
                });

                while (!receiverReady.get()) {
                    sleep(10);
                }
                s.start();

                s.join();
                r.join();
        }

        reportSocketBufferSize();

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
        sendWL = new TpsWriteListener(TPS_SENDER, TEST_SUBJECT, CONTROL_SUBJECT);

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

            Debug.info(TPS_SENDER, "Publishing Control Start Message");
            nc.publish(CONTROL_SUBJECT, null);

            while (nc.getStatus() == Connection.Status.CONNECTED && !sendEL.connectionException && !sendCL.disconnected)
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
                        nc.publish(TEST_SUBJECT, h, payload);
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

            Debug.info(TPS_SENDER, "Publishing Control Terminate Message");
            nc.publish(CONTROL_SUBJECT, null);

            long pending = nc.outgoingPendingMessageCount();
            while (pending > 0) {
                Debug.info(TPS_SENDER, "Waiting for %s queued messages to be sent...", pending);
                pending = nc.outgoingPendingMessageCount();
                sleep(10);
            }
            Debug.info(TPS_SENDER, "Queue empty");
            sleep(100); // just for good measure

            sendResults.add("\n" + TPS_SENDER);
            sendResults.add("Before Disconnect...");
            addSendResult("Socket Written Messages", sendStats.pay.writtenMessages);
            addSendResult("Socket Written Bytes", sendStats.pay.writtenBytes);
            addSendResult("Buffered Messages", sendStats.pay.bufferedMessages);
            addSendResult("Buffered Bytes", sendStats.pay.bufferedBytes);

            sendResults.add("After Disconnect...");
            addSendResult("Socket Written Messages", sendStats.pay2.writtenMessages);
            addSendResult("Socket Written Bytes", sendStats.pay2.writtenBytes);
            addSendResult("Buffered Messages", sendStats.pay2.bufferedMessages);
            addSendResult("Buffered Bytes", sendStats.pay2.bufferedBytes);

            sendResults.add("Etc ...");
            addSendResult("Control Messages Buffered", sendWL.getControlsBuffered());
            addSendResult("Last Write Messages", sendStats.lastWriteMessages);
            addSendResult("Last Write Bytes", sendStats.lastWriteBytes);

            addSendResult("Buffered Not Written Messages", sendStats.pay.notWrittenMessages);
            addSendResult("Buffered Not Written Bytes", sendStats.pay.notWrittenBytes);

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

    private void addSendResult(String s, Number n) {
        sendResults.add(stringify("  " + s + ": %s", format3(n)));
    }

    // ----------------------------------------------------------------------------------------------------
    // Receiver
    // ----------------------------------------------------------------------------------------------------
    long receivedMessages = 0;
    long receivedLastMessageId = -1;
    int control = 0;
    CountDownLatch controlLatch = new CountDownLatch(2);
    TpsConnectionListener receiveCL;
    TpsErrorListener receiveEL;
    AtomicBoolean receiverReady = new AtomicBoolean(false);

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

            Dispatcher d = nc.createDispatcher();

            d.subscribe(TEST_SUBJECT, msg -> {
                if (++receivedMessages == 1) {
                    receivedLastMessageId = 1;
                    Debug.info(TPS_RECEIVER, "Started Receiving");
                    return;
                }

                long expected = receivedLastMessageId + 1;
                long mid = extractMessageId(msg);
                receivedLastMessageId = mid;
                if (mid != expected) {
                    long gap = mid - expected;
                    receiveResults.add(stringify("  Received Gap Message: %s", format3(mid)));
                    receiveResults.add(stringify("  Expected Gap Message: %s", format3(expected)));
                    receiveResults.add(stringify("  Gap: %s", gap));
                    Debug.info(TPS_RECEIVER, "******"
                        , "Got Message Id: %s but expected: %s", format3(mid), format3(expected)
                        , "Loss of %s", format3(gap));
                }
            });

            d.subscribe(CONTROL_SUBJECT, msg -> {
                if (control++ == 0) {
                    Debug.info(TPS_RECEIVER, "Received Control Start Message.");
                }
                else {
                    Debug.info(TPS_RECEIVER, "Received Control Terminate Message.");
                }
                controlLatch.countDown();
            });

            sleep(50);
            receiverReady.set(true);

            if (!controlLatch.await(15, TimeUnit.SECONDS)) {
                Debug.info(TPS_RECEIVER, "!!!!! Terminate Message NOT Received");
            }

            receiveResults.addFirst(stringify("  Total Received Messages: %s", format3(receivedMessages)));
            receiveResults.addFirst("\n" + TPS_RECEIVER);

            d.unsubscribe(TEST_SUBJECT);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------
    private Options.Builder buildOptions(int firstServerIx) {
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

    private static String[] figureServers(List<String> paramsServers, int firstServerIx) {
        String firstServer = paramsServers.get(firstServerIx);
        List<String> ordered = new ArrayList<>(paramsServers);
        ordered.remove(firstServer);
        Collections.shuffle(ordered);
        ordered.addFirst(firstServer);
        return ordered.toArray(new String[0]);
    }

    private void reportSocketBufferSize() {
        try {
            Socket socket = new Socket();
            Debug.info(workLabel, "Receive Buffer %s bytes", socket.getReceiveBufferSize());
            Debug.info(workLabel, "Send Buffer %s bytes", socket.getSendBufferSize());
            socket.close();
        }
        catch (IOException ignore) {}
    }

    private void reportApplicationOptions() {
        Debug.info(workLabel, "----- Application Options -----");
        Debug.info(workLabel, "targetTps", targetTps);
        Debug.info(workLabel, "subject", TEST_SUBJECT);
        Debug.info(workLabel, "messageIdKey", MESSAGE_ID_KEY);
        Debug.info(workLabel, "payloadSize", payloadSize);
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
