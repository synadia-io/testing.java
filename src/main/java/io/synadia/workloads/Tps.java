package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.impl.Headers;
import io.synadia.CommandLine;
import io.synadia.Workload;
import io.synadia.chaos.OutputConnectionListener;
import io.synadia.chaos.OutputErrorListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Tps extends Workload {
    private String action;

    private static final String TPS_SENDER = "TPS Sender";
    private static final String TPS_RECEIVER = "TPS Receiver";

    long targetTps = 10000;
    String controlSubject = "control";
    String subject = "tps";
    int payloadSize = 12288;

    @Override
    public void init(CommandLine commandLine) {
        init("TPS Workload", commandLine);
        this.action = commandLine.action;
    }

    @Override
    public void runWorkload() throws Exception {
        switch (action) {
            case "send" -> tpsSend();
            case "rec"  -> tpsReceive();
        }
    }

    private static long bumpMessageId(long messageId) {
        return ((messageId + 150) / 100) * 100;
    }

    private void tpsSend() throws IOException, InterruptedException {

        Options.Builder builder  = new Options.Builder()
            .server(params.servers.get(0))
            .connectionListener(new OutputConnectionListener(TPS_SENDER))
            .errorListener(new OutputErrorListener(TPS_SENDER))
//            .reconnectBufferSize()
//            .bufferSize()
//            .maxMessagesInOutgoingQueue()
            ;

        try (Connection nc = Nats.connect(builder.build())) {

            System.out.println(TPS_SENDER);
            System.out.print(action);

            long startNanos = System.nanoTime();
            long currentSecond = 0;
            long messagesThisSecond = 0;
            long nextSecondStart = startNanos + 1_000_000_000L; // 1 second in nanos

            long messageId = 0;
            boolean sendMessageId = true;

            byte[] payload = new byte[payloadSize];
            Headers h = new Headers();
            while (true) {
                if (sendMessageId) {
                    sendMessageId = false;
                    System.out.printf("***** Sending Message Id seed message: %d%n", messageId);
                    nc.publish(controlSubject, ("" + messageId).getBytes(StandardCharsets.US_ASCII));
                    try { Thread.sleep(100); } catch (InterruptedException e) { throw new RuntimeException(e); }
                }

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
                        h.put("mid", ++messageId + "");
                        nc.publish(subject, h, payload);
                        messagesThisSecond++;

                        // Calculate sleep time to maintain even distribution
                        long remainingInSecond = nextSecondStart - System.nanoTime();
                        long remainingMessages = targetTps - messagesThisSecond;
                        if (remainingMessages > 0 && remainingInSecond > 0) {
                            long sleepTime = remainingInSecond / (remainingMessages + 1);
                            if (sleepTime > 100_000) { // Only sleep if more than 100 microseconds
                                Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                            }
                        }
                    } catch (Exception e) {
                        sendMessageId = true;
                        messageId = bumpMessageId(messageId);
                        System.out.printf("Error sending message during test: %s%n", e.getMessage());
                        Thread.sleep(1000);
                    }
                } else {
                    // Wait for next second if we've hit the target for this second
                    long sleepTime = nextSecondStart - System.nanoTime();
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
    }

    AtomicLong messagesReceived = new AtomicLong(0);
    AtomicLong lastCurrentCount = new AtomicLong(0);

    private void tpsReceive() throws IOException, InterruptedException {
        System.out.println("TPS Receiver !!! " + params.servers);
        Options.Builder builder  = new Options.Builder()
            .server(params.servers.get(1))
            .connectionListener(new OutputConnectionListener(TPS_RECEIVER))
            .errorListener(new OutputErrorListener(TPS_RECEIVER))
            ;

        AtomicLong lastMessageId = new AtomicLong(-1);

        try (Connection nc = Nats.connect(builder.build())) {
            startMetricsLogging(nc);
            System.out.println(TPS_RECEIVER);
            System.out.print(action);
            MessageHandler handler = msg -> {
                if (msg.getData().length != this.payloadSize) {
                    System.out.printf("Unexpected payload size: %d B (expected: %d B)%n", msg.getData().length, this.payloadSize);
                }
                //noinspection DataFlowIssue // headers won't be null.
                long mid = Long.parseLong(msg.getHeaders().getFirst("mid"));
                long expected = lastMessageId.incrementAndGet();
                if (expected == 0) {
                    lastMessageId.set(mid);
                }
                else if (mid != expected) {
                    lastMessageId.set(mid);
                    System.out.printf("***** Got Message Id: %d but expected: %d%n", mid, expected);
                }
                messagesReceived.incrementAndGet();
            };

            Dispatcher currentDispatcher = nc.createDispatcher();
            currentDispatcher.subscribe(controlSubject, m -> {
                long lmid = Long.parseLong(new String(m.getData()));
                lastMessageId.set(lmid);
                System.out.printf("***** Got Message Id seed message: %d%n", lmid);
            });

            // Subscribe with high-throughput settings
            Subscription subscription = currentDispatcher.subscribe(subject, handler);//, queueGroup);
            System.out.printf("Started continuous listening on subject: %s (target: %d TPS, payload: %d B)%n", subject, targetTps, this.payloadSize);
            Thread.currentThread().join();
        }
    }

    private void startMetricsLogging(Connection nc) {
        nc.getOptions().getScheduledExecutor().scheduleAtFixedRate(() -> {
            long currentCount = messagesReceived.get();
            long previousCount = lastCurrentCount.getAndSet(currentCount);
            long currentTps = currentCount - previousCount;
            String status = String.format(" | Subject: %s | Target: %d TPS", subject, targetTps);
            System.out.printf("Receive TPS: %d, Total Messages: %d, Connection: %s, Connected Server: %s%s%n",
                currentTps, currentCount, nc.getStatus(), nc.getConnectedUrl(), status);
            // Log performance warning if TPS is significantly below target (only if actively receiving)
            if (currentTps > 0 && currentTps < targetTps * 0.8) {
                System.out.printf("Performance below target: %d TPS (target: %d)%n", currentTps, targetTps);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
}
