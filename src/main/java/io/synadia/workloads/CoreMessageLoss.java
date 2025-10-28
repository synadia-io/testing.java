package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NoOpStatistics;
import io.synadia.CommandLine;
import io.synadia.Params;
import io.synadia.Workload;
import io.synadia.utils.Debug;
import io.synadia.workloads.cml.CmlConnectionListener;
import io.synadia.workloads.cml.CmlErrorListener;
import io.synadia.workloads.cml.CmlStatsCollector;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.nats.client.support.JsonValueUtils.readInteger;
import static io.nats.jsmulti.shared.Stats.format3;
import static io.nats.jsmulti.shared.Stats.format3Right;
import static io.synadia.utils.Debug.stringify;
import static io.synadia.workloads.cml.CmlUtils.*;

public class CoreMessageLoss extends Workload {

    // Labels
    private static final String TPS_SENDER = "SENDER";
    private static final String TPS_RECEIVER = "RECEIVER";

    // Run values
    private static final String TEST_SUBJECT = "test";
    private static final String TERMINATE_SUBJECT = "term";
    private static final String TEST_QUEUE = "q";
    private static final long WAIT_FOR_MESSAGES = 5000;

    // argument defaults
    private static final int DEFAULT_TPS = 10_000;
    private static final int DEFAULT_PAYLOAD = 12 * 1024;
    private static final int CONNECTION_TIMEOUT_MILLIS = 5000;

    // arguments
    int targetTps;
    int payloadSize;
    int numReceivers = 1;
    int sendBufferSize = 1;
    long connectionTimeoutMillis = 5000;
    int maxMessagesInOutgoingQueue;

    // common
    final ScheduledExecutorService scheduler;

    public CoreMessageLoss() {
        scheduler = Executors.newScheduledThreadPool(1);
    }

    @Override
    public void init(CommandLine commandLine) {
        this.workLabel = "TPS";
        this.commandLine = commandLine;
        this.params = new Params(commandLine.paramsFiles);

        Debug.info("Environment", "JNats %s", Nats.CLIENT_VERSION);
        commandLine.debug();

        targetTps = commandLine.getIntArg("t", readInteger(params.jv, "target.tps", DEFAULT_TPS));
        payloadSize = commandLine.getIntArg("p", readInteger(params.jv, "payload.size", DEFAULT_PAYLOAD));
        numReceivers = commandLine.getIntArg("r", readInteger(params.jv, "num.receivers", 1));
        sendBufferSize = commandLine.getIntArg("b", readInteger(params.jv, "send.buffer.size", -1));
        connectionTimeoutMillis = commandLine.getIntArg("c", readInteger(params.jv, "connection.timeout.millis", -1));
        int mmiq = targetTps * 125 / 100; // 125 % of target tps
        maxMessagesInOutgoingQueue = Math.max(mmiq, Options.DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE);

        Debug.info(workLabel, "----- Application Options -----");
        Debug.info(workLabel, "Servers", params.servers.toArray(new String[0]));
        Debug.info(workLabel, "Target TPS", targetTps);
        Debug.info(workLabel, "Payload Size", payloadSize);
        Debug.info(workLabel, "Num Receivers", numReceivers);
        Debug.info(workLabel, "Send Buffer Size", sendBufferSize);
        Debug.info(workLabel, "Max Messages In Outgoing Queue", maxMessagesInOutgoingQueue);
        Debug.info(workLabel, "Connection Timeout Millis", connectionTimeoutMillis);

        reportSocketBufferSize();
    }

    @Override
    public void runWorkload() throws Exception {
        for (int ix = 0; ix < numReceivers; ix++) {
            receivers.add(new Receiver());
        }

        List<Thread> threads = new ArrayList<>();
        for (int rx = 0; rx < numReceivers; rx++) {
            int finalRx = rx;
            Thread r = new Thread(() -> { try { receive(finalRx); } catch (Exception ignored) {} });
            r.setName("R-" + rx + "-main");
            r.start();
            threads.add(r);
        }
        for (int ix = 0; ix < numReceivers; ix++) {
            AtomicBoolean ready = receivers.get(ix).ready;
            while (!ready.get()) {
                sleep(10);
            }
        }

        scheduler.scheduleAtFixedRate(
            () -> {
                long receivedMessages = 0;
                for (int ix = 0; ix < numReceivers; ix++) {
                    long rm = receivers.get(ix).receivedMessages;
                    receivedMessages += rm;
                }
                Debug.info(TPS_RECEIVER, "Total Received Messages: %s", receivedMessages, System.currentTimeMillis() - lastReceive.get());
                if (System.currentTimeMillis() - lastReceive.get() > WAIT_FOR_MESSAGES) {
                    for (CountDownLatch l : latches) {
                        l.countDown();
                    }
                }
            },
            2500, 2500, TimeUnit.MILLISECONDS);

        Thread s = new Thread(() -> { try { send(); } catch (Exception ignored) {} });
        s.setName("S-main");
        s.start();

        s.join();
        for (Thread t : threads) {
            t.join();
        }

        scheduler.shutdown();

        reportSocketBufferSize();

        sleep(100); // give callbacks time to finish

        List<Long> drained = new ArrayList<>();
        messageIds.drainTo(drained);
        drained.sort(Long::compareTo);

        // ----------------------------------------------------------------------------------------------------
        // Report Receivers
        // ----------------------------------------------------------------------------------------------------
        System.out.println("\n" + TPS_RECEIVER);
        long receivedMessages = 0;
        for (int ix = 0; ix < numReceivers; ix++) {
            long rm = receivers.get(ix).receivedMessages;
            receivedMessages += rm;
            System.out.println(stringify("  Receiver %s Received Messages:  %s", ix, format3Right(rm, 7)));
        }
        System.out.println("  ------------------------------ -------");
        System.out.println(stringify("  Total Received Messages:       %s", format3Right(receivedMessages, 7)));
        System.out.println();
        System.out.println(stringify("  Highest Messages Id Received:  %s", format3Right(highestMessageId.get(), 7)));

        long expected = drained.getFirst();
        for (Long mid : drained) {
            if (mid != expected) {
                System.out.println(stringify("\n  Received Gap Message: %s", format3(mid)));
                System.out.println(stringify("  Expected Gap Message: %s", format3(expected)));
                long diff = mid - expected;
                System.out.println(stringify("  Gap: %s", diff));
                System.out.println(stringify("  Gap Bytes (Approximate): %s", format3(diff * payloadSize)));
            }
            expected = mid + 1;
        }

        // ----------------------------------------------------------------------------------------------------
        // Report Sender
        // ----------------------------------------------------------------------------------------------------
        System.out.println("\n" + TPS_SENDER);
        System.out.println("Before Disconnect...");
        printSendResultAndDiff("Buffered vs Socket Messages",
            sendStats.pay.bufferedMessages, sendStats.pay.writtenMessages);
        printSendResultAndDiff("Buffered vs Socket Bytes   ",
            sendStats.pay.bufferedBytes, sendStats.pay.writtenBytes);

        System.out.println("After Disconnect...");
        printSendResultAndDiff("Buffered vs Socket Messages",
            sendStats.pay2.bufferedMessages, sendStats.pay2.writtenMessages);
        printSendResultAndDiff("Buffered vs Socket Bytes   ",
            sendStats.pay2.bufferedBytes, sendStats.pay2.writtenBytes);
    }

    private void printSendResult(String s, Number n) {
        System.out.println(stringify("  " + s + ": %s", format3(n)));
    }

    private void printSendResultAndDiff(String s, Number n1, Number n2) {
        long diff = n1.longValue() - n2.longValue();
        System.out.println(stringify("  " + s + ": %s vs %s ... %s", format3(n1), format3(n2), format3(diff)));
    }

    // ----------------------------------------------------------------------------------------------------
    // Sender
    // ----------------------------------------------------------------------------------------------------
    AtomicLong pubId;
    CmlStatsCollector sendStats;
    CmlConnectionListener sendCL;
    CmlErrorListener sendEL;

    private void send() throws IOException, InterruptedException {
        pubId = new AtomicLong(0);
        sendStats = new CmlStatsCollector(payloadSize);

        sendCL = new CmlConnectionListener(TPS_SENDER, params.servers, false);
        sendEL = new CmlErrorListener(TPS_SENDER);

        Options options  = new Options.Builder()
            .servers(params.servers.toArray(new String[0]))
            .ignoreDiscoveredServers()
            .noRandomize()
            .connectionTimeout(CONNECTION_TIMEOUT_MILLIS)
            .sendBufferSize(sendBufferSize)
            .maxMessagesInOutgoingQueue(maxMessagesInOutgoingQueue)
            .statisticsCollector(sendStats)
            .connectionListener(sendCL)
            .errorListener(sendEL)
            .build();

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

            while (!sendCL.reconnected.get()) {
                Debug.info(TPS_SENDER, "Waiting for Reconnect");
                sleep(10);
            }

            Debug.info(TPS_SENDER, "Publishing Control Terminate Message");
            nc.publish(TERMINATE_SUBJECT, null);

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
            if (rounds % 250 == 0) {
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
    static class Receiver {
        long receivedMessages;
        CmlConnectionListener receiveCL;
        CmlErrorListener receiveEL;
        AtomicBoolean ready = new AtomicBoolean(false);
    }

    AtomicLong highestMessageId = new AtomicLong(0);
    AtomicLong lastReceive = new AtomicLong(System.currentTimeMillis());
    LinkedBlockingQueue<Long> messageIds = new LinkedBlockingQueue<>();
    List<Receiver> receivers = new ArrayList<>();
    List<CountDownLatch> latches = new ArrayList<>();

    private void receive(int rx) throws IOException, InterruptedException {
        Receiver r = receivers.get(rx);
        receivers.add(r);

        CountDownLatch latch = new CountDownLatch(1);
        latches.add(latch);

        String label = TPS_RECEIVER + "-" + rx;
        r.receiveCL = new CmlConnectionListener(label, params.servers, true);
        r.receiveEL = new CmlErrorListener(label);

        int sIx = rx % 2 == 0 ? 1 : 2;

        Options options  = new Options.Builder()
            .server(params.servers.get(rx % 2 == 0 ? 2 : 1))
            .ignoreDiscoveredServers()
            .noRandomize()
            .connectionTimeout(CONNECTION_TIMEOUT_MILLIS)
            .statisticsCollector(new NoOpStatistics())
            .connectionListener(r.receiveCL)
            .errorListener(r.receiveEL)
            .build();

        try (Connection nc = Nats.connect(options)) {
            Dispatcher d = nc.createDispatcher();

            d.subscribe(TEST_SUBJECT, TEST_QUEUE, msg -> {
                long mid = extractMessageId(msg);
                messageIds.add(mid);
                highestMessageId.set(Math.max(highestMessageId.get(), mid));
                lastReceive.set(System.currentTimeMillis());
                if (++r.receivedMessages == 1) {
                    Debug.info(label, "Started Receiving");
                }
            });

            d.subscribe(TERMINATE_SUBJECT, msg -> {
                Debug.info(label, "Received Control - Terminate Message.");
                latch.countDown();
            });

            sleep(50);
            r.ready.set(true);

            if (!latch.await(60, TimeUnit.SECONDS)) {
                Debug.info(label, "!!!!! Terminate Message NOT Received");
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------
    private void reportSocketBufferSize() {
        try {
            Socket socket = new Socket();
            Debug.info(workLabel, "Socket Receive Buffer: %s bytes", socket.getReceiveBufferSize());
            Debug.info(workLabel, "Socket Send Buffer: %s bytes", socket.getSendBufferSize());
            socket.close();
        }
        catch (IOException ioe) {
            Debug.info(workLabel, "Exception Reporting Socket Buffer Size", ioe);
        }
    }
}
