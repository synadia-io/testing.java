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
import java.util.Arrays;
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
    private static final String CONTROL_SUBJECT = "ctrl";
    private static final byte[] TERMINATE_BYTES = new byte[] {0};

    // Common
    String action;
    int targetTps;
    int payloadSize;

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
        if (commandLine.args.size() == 1) {
            payloadSize = Integer.parseInt(commandLine.args.getFirst());
        }

        reportApplicationOptions();
        reportSocketBufferSize();
    }

    @Override
    public void runWorkload() throws Exception {
        Thread r = new Thread(() -> { try { receive(); } catch (Exception ignored) {} });
        r.setName("R-main");
        r.start();
        while (!receiverReady.get()) {
            sleep(10);
        }

        Thread s = new Thread(() -> { try { send(); } catch (Exception ignored) {} });
        s.setName("S-main");
        s.start();

        s.join();
        r.join();

        reportSocketBufferSize();

        sleep(100); // give callbacks time to finish

        System.out.println("\n" + TPS_RECEIVER);
        System.out.println(stringify("  Total Received Messages: %s", format3(receivedMessages)));
        if (!gaps.isEmpty()) {
            for (Gap g : gaps) {
                g.print(payloadSize);
            }
        }

        System.out.println("\n" + TPS_SENDER);
        System.out.println("Before Disconnect...");
        printSendResult("Buffered vs Socket Messages",
            sendStats.pay.bufferedMessages, sendStats.pay.writtenMessages);
        printSendResult("Buffered vs Socket Bytes   ",
            sendStats.pay.bufferedBytes, sendStats.pay.writtenBytes);

        System.out.println("After Disconnect...");
        printSendResult("Buffered vs Socket Messages",
            sendStats.pay2.bufferedMessages, sendStats.pay2.writtenMessages);
        printSendResult("Buffered vs Socket Bytes   ",
            sendStats.pay2.bufferedBytes, sendStats.pay2.writtenBytes);

        System.out.println("\nETC");
        printSendResult("Protocol Messages Buffered", sendWL.protocolsBuffered.get());
        printSendResult("Control Messages Buffered", sendWL.controlsBuffered.get());
        printSendResult("Last Write Messages", sendStats.lastWriteMessages);
        printSendResult("Last Write Bytes   ", sendStats.lastWriteBytes);

        printSendResult("Buffered Not Written Messages", sendStats.pay.notWrittenMessages);
        printSendResult("Buffered Not Written Bytes   ", sendStats.pay.notWrittenBytes);

        if (sendWL.gapList.isEmpty()) {
            System.out.println("  No Writer Gaps");
        }
        else {
            System.out.println("  Writer Gaps");
            for (String g : sendWL.gapList) {
                System.out.println(" " + g);
            }
        }
    }

    private void printSendResult(String s, Number n) {
        System.out.println(stringify("  " + s + ": %s", format3(n)));
    }

    private void printSendResult(String s, Number n1, Number n2) {
        long diff = n1.longValue() - n2.longValue();
        System.out.println(stringify("  " + s + ": %s vs %s ... %s", format3(n1), format3(n2), format3(diff)));
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
        pubId = new AtomicLong(0);
        sendStats = new TpsStatsCollector(payloadSize);
        sendWL = new TpsWriteListener(TPS_SENDER, TEST_SUBJECT, CONTROL_SUBJECT);

        sendCL = new TpsConnectionListener(TPS_SENDER, params.servers, false);
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

            while (nc.getStatus() == Connection.Status.CONNECTED
                && !sendEL.connectionException.get() && !sendCL.disconnected.get())
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

            sendStats.pay.debug(TPS_SENDER, "Before Disconnect Payloads");

            sendStats.startPhase2();
            sendWL.startPhase2();

            while (!sendCL.reconnected.get()) {
                Debug.info(TPS_SENDER, "Waiting for Reconnect");
                sleep(10);
            }

            Debug.info(TPS_SENDER, "Publishing Control Terminate Message");
            nc.publish(CONTROL_SUBJECT, TERMINATE_BYTES);

            waitForPending(nc);
        }
    }

    private void waitForPending(Connection nc) {
        long pending = nc.outgoingPendingMessageCount();
        long rounds = 10000;
        Debug.info(TPS_SENDER, "Waiting for %s queued messages to be sent...", pending);
        while (rounds-- > 0 && pending > 0) {
            if (nc.getStatus() != Connection.Status.CONNECTED) {
                rounds = 10000;
                sleep(1000);
                continue;
            }

            sleep(10);
            if (rounds % 100 == 0) {
                Debug.info(TPS_SENDER, "Waiting for %s queued messages to be sent...", pending);
            }
            pending = nc.outgoingPendingMessageCount();
        }
        pending = nc.outgoingPendingMessageCount();
        if (pending > 0) {
            Debug.info(TPS_SENDER, "!!!!! Queue Failed to Empty: %s", pending);
        }
        else {
            Debug.info(TPS_SENDER, "Queue empty");
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Receiver
    // ----------------------------------------------------------------------------------------------------
    long receivedMessages = 0;
    long receivedLastMessageId = -1;
    TpsConnectionListener receiveCL;
    TpsErrorListener receiveEL;
    AtomicBoolean receiverReady = new AtomicBoolean(false);
    List<Gap> gaps = new ArrayList<>();

    static class Gap {
        final long expected;
        final long mid;

        public Gap(long expected, long mid) {
            this.expected = expected;
            this.mid = mid;
        }

        public void print(int payloadSize) {
            System.out.println(stringify("  Received Gap Message: %s", format3(mid)));
            System.out.println(stringify("  Expected Gap Message: %s", format3(expected)));
            long diff = mid - expected;
            System.out.println(stringify("  Gap: %s", diff));
            System.out.println(stringify("  Gap Bytes (Approximate): %s", format3(diff * payloadSize)));
        }
    }

    private void receive() throws IOException, InterruptedException {
        int firstServerIx = commandLine.args.isEmpty() ? 1 : Integer.parseInt(commandLine.args.getFirst());
        receiveCL = new TpsConnectionListener(TPS_RECEIVER, params.servers, true);
        receiveEL = new TpsErrorListener(TPS_RECEIVER);

        Options options = buildOptions(firstServerIx)
            .statisticsCollector(new NoOpStatistics())
            .connectionListener(receiveCL)
            .errorListener(receiveEL)
            .build();

        reportConnectionOptions(TPS_RECEIVER, options);

        try (Connection nc = Nats.connect(options)) {
            Dispatcher d = nc.createDispatcher();

            AtomicBoolean gapped = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);

            AtomicLong rf = new AtomicLong(1000);
            d.subscribe(TEST_SUBJECT, msg -> {
                if (++receivedMessages == 1) {
                    receivedLastMessageId = 1;
                    Debug.info(TPS_RECEIVER, "Started Receiving");
                    return;
                }

                if (receivedMessages % rf.get() == 0) {
                    Debug.info(TPS_RECEIVER, "Received %s", receivedMessages);
                    return;
                }

                long expected = receivedLastMessageId + 1;
                long mid = extractMessageId(msg);
                receivedLastMessageId = mid;
                if (mid != expected) {
                    gaps.add(new Gap(expected, mid));
                    long gap = mid - expected;
                    Debug.info(TPS_RECEIVER, "******"
                        , "Got Message Id: %s but expected: %s", format3(mid), format3(expected)
                        , "Gap: %s", format3(gap));
                    rf.set(500);
                }
            });

            d.subscribe(CONTROL_SUBJECT, msg -> {
                if (Arrays.equals(TERMINATE_BYTES, msg.getData())) {
                    Debug.info(TPS_RECEIVER, "Received Control - Terminate Message.");
                    latch.countDown();
                }
            });

            sleep(50);
            receiverReady.set(true);

            Debug.info(TPS_RECEIVER, "Waiting for Terminate Message");
            if (!latch.await(60, TimeUnit.SECONDS)) {
                Debug.info(TPS_RECEIVER, "!!!!! Terminate Message NOT Received");
            }
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
        if (millis > 0) {
            builder.reconnectWait(Duration.ofMillis(millis));
        }
        millis = readLong(jv, "nats.ping.interval.millis", -1);
        if (millis > 0) {
            builder.pingInterval(Duration.ofMillis(millis));
        }

        builder.maxPingsOut(readInteger(jv, "nats.connection.max.ping.out", 2));

        millis = readLong(jv, "nats.socket.write.timeout.millis", -1);
        if (millis > 0) {
            builder.socketWriteTimeout(millis);
        }

        millis = readLong(jv, "nats.socket.read.timeout.millis", -1);
        if (millis > 0) {
            builder.socketReadTimeoutMillis((int)millis);
        }

        return builder;
    }

    private static String[] figureServers(List<String> paramsServers, int firstServerIx) {
        if (firstServerIx == 0) {
            return paramsServers.toArray(new String[0]);
        }
        String firstServer = paramsServers.get(firstServerIx);
        List<String> figured = new ArrayList<>(paramsServers);
        figured.remove(firstServer);
        figured.addFirst(firstServer);
        return figured.toArray(new String[0]);
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
        if (o.getSocketReadTimeoutMillis() > 0) {
            Debug.info(label, "socketReadTimeoutMillis", o.getSocketReadTimeoutMillis());
        }
    }
}
