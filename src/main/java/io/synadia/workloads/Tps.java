package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NoOpStatistics;
import io.nats.client.impl.TpsWriteListener;
import io.synadia.CommandLine;
import io.synadia.Params;
import io.synadia.Workload;
import io.synadia.utils.Debug;
import io.synadia.workloads.tps.TpsConnectionListener;
import io.synadia.workloads.tps.TpsErrorListener;
import io.synadia.workloads.tps.TpsStatsCollector;

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
import static io.synadia.workloads.tps.TpsUtils.*;

public class Tps extends Workload {

    // Labels
    private static final String TPS_SENDER = "SENDER";
    private static final String TPS_RECEIVER = "RECEIVER";

    // Run values
    private static final String TEST_SUBJECT = "test";
    private static final String TERMINATE_SUBJECT = "term";
    private static final String TEST_QUEUE = "q";

    // argument defaults
    private static final int DEFAULT_TPS = 10_000;
    private static final int DEFAULT_PAYLOAD = 12 * 1024;
    private static final int CONNECTION_TIMEOUT_MILLIS = 5000;

    // arguments
    int targetTps;
    int payloadSize;
    int numReceivers = 1;
    int sendBufferSize = 1;
    int mmioq;

    // common
    final ScheduledExecutorService scheduler;

    public Tps() {
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
        sendBufferSize = commandLine.getIntArg("b", readInteger(params.jv, "nats.connection.send.buffer", -1));
        mmioq = Math.max(targetTps, Options.DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE);

        if (commandLine.args.size() == 1) {
            payloadSize = Integer.parseInt(commandLine.args.getFirst());
        }

        Debug.info(workLabel, "----- Application Options -----");
        Debug.info(workLabel, "Servers", params.servers.toArray(new String[0]));
        Debug.info(workLabel, "Target TPS", targetTps);
        Debug.info(workLabel, "Payload Size", payloadSize);
        Debug.info(workLabel, "Num Receivers", numReceivers);
        Debug.info(workLabel, "Send Buffer Size", sendBufferSize);
        Debug.info(workLabel, "Send Outgoing Queue Max", mmioq);

        reportSocketBufferSize();
    }

    @Override
    public void runWorkload() throws Exception {
        for (int ix = 0; ix < numReceivers; ix++) {
            receivers.add(new Receiver());
        }

        List<Thread> threads = new ArrayList<>();
        for (int ix = 0; ix < numReceivers; ix++) {
            int finalIx = ix;
            Thread r = new Thread(() -> { try { receive(finalIx); } catch (Exception ignored) {} });
            r.setName("R-" + ix + "-main");
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
                Debug.info(TPS_RECEIVER, "Total Received Messages: %s", receivedMessages);
            },
            1, 1, TimeUnit.SECONDS);

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
        printSendResult("Buffered Not Written Messages", sendStats.pay.notWrittenMessages);
        printSendResult("Buffered Not Written Bytes   ", sendStats.pay.notWrittenBytes);

        if (!sendWL.gapList.isEmpty()) {
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
        sendWL = new TpsWriteListener(TPS_SENDER, TEST_SUBJECT, TERMINATE_SUBJECT);

        sendCL = new TpsConnectionListener(TPS_SENDER, params.servers, false);
        sendEL = new TpsErrorListener(TPS_SENDER);

        Options options  = new Options.Builder()
            .servers(figureServers(params.servers, 0))
            .ignoreDiscoveredServers()
            .noRandomize()
            .connectionTimeout(CONNECTION_TIMEOUT_MILLIS)
            .sendBufferSize(sendBufferSize)
            .maxMessagesInOutgoingQueue(mmioq)
            .writeListener(sendWL)
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
            sendWL.startPhase2();

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
        TpsConnectionListener receiveCL;
        TpsErrorListener receiveEL;
        AtomicBoolean ready = new AtomicBoolean(false);
    }

    LinkedBlockingQueue<Long> messageIds = new LinkedBlockingQueue<>();
    List<Receiver> receivers = new ArrayList<>();

    private void receive(int ix) throws IOException, InterruptedException {
        Receiver r = receivers.get(ix);
        receivers.add(r);

        String label = TPS_RECEIVER + "-" + ix;
        r.receiveCL = new TpsConnectionListener(label, params.servers, true);
        r.receiveEL = new TpsErrorListener(label);

        int sIx = ix % 2 == 0 ? 1 : 2;

        Options options  = new Options.Builder()
            .servers(figureServers(params.servers, sIx))
            .ignoreDiscoveredServers()
            .noRandomize()
            .connectionTimeout(CONNECTION_TIMEOUT_MILLIS)
            .statisticsCollector(new NoOpStatistics())
            .connectionListener(r.receiveCL)
            .errorListener(r.receiveEL)
            .build();

        try (Connection nc = Nats.connect(options)) {
            Dispatcher d = nc.createDispatcher();

            CountDownLatch latch = new CountDownLatch(1);

            d.subscribe(TEST_SUBJECT, TEST_QUEUE, msg -> {
                messageIds.add(extractMessageId(msg));
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
        catch (IOException ioe) {
            Debug.info(workLabel, "Exception Reporting Socket Buffer Size", ioe);
        }
    }
}
