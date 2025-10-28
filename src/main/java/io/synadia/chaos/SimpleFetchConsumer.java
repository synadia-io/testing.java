// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.*;
import io.synadia.chaos.support.CommandLine;
import io.synadia.chaos.support.ConsumerKind;

import java.io.IOException;

public class SimpleFetchConsumer extends ConnectableConsumer implements Runnable {
    final StreamContext sc;
    final ConsumerContext cc;
    final int batchSize;
    final long expiresIn;
    final Thread t;

    FetchConsumer fc;

    public SimpleFetchConsumer(CommandLine cmd, ConsumerKind consumerKind, int batchSize, long expiresIn) throws IOException, InterruptedException, JetStreamApiException {
        super(cmd, "fc", consumerKind);
        if (consumerKind == ConsumerKind.Ordered) {
            throw new IllegalArgumentException("Ordered Consumer not supported for App Simple Fetch");
        }

        this.batchSize = batchSize;
        this.expiresIn = expiresIn;

        sc = nc.getStreamContext(cmd.stream);

        cc = sc.createOrUpdateConsumer(newCreateConsumer().build());
        Output.write(label, cc.getConsumerName());
        t = new Thread(this);
        t.start();
    }

    @Override
    public void run() {
        FetchConsumeOptions fco = FetchConsumeOptions.builder().maxMessages(batchSize).expiresIn(expiresIn).build();
        Output.write(label, toString(fco));

        //noinspection InfiniteLoopStatement
        while (true) {
            try (FetchConsumer autoCloseableFc = cc.fetch(fco)) {
                fc = autoCloseableFc;
                Message m = fc.nextMessage();
                while (m != null) {
                    onMessage(m);
                    m = fc.nextMessage();
                }
            }
            catch (Exception e) {
                // if there was an error, just try again
            }

            // simulating some work to be done between fetches
            try {
                //noinspection BusyWait
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void refreshInfo() {
        if (fc != null) {
            updateLabel(fc.getConsumerName());
        }
    }

    public static String toString(FetchConsumeOptions fco) {
        return "FetchConsumeOptions" +
            "\nMax Messages: " + fco.getMaxMessages() +
            "\nMax Bytes: " + fco.getMaxBytes() +
            "\nExpires In: " + fco.getExpiresInMillis() +
            "\nIdleHeartbeat: " + fco.getIdleHeartbeat() +
            "\nThreshold Percent: " + fco.getThresholdPercent();
    }
}
