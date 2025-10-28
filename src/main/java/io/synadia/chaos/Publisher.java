// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.PublishAck;
import io.synadia.chaos.support.CommandLine;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class Publisher implements Runnable {

    static final String LABEL = "PUBLISHER";

    final CommandLine cmd;
    final long pubDelay;
    final AtomicLong lastSeqno = new AtomicLong(-1);
    final AtomicLong errorRun = new AtomicLong(0);

    public Publisher(CommandLine cmd, long pubDelay) {
        this.cmd = cmd;
        this.pubDelay = pubDelay;
    }

    public long getLastSeqno() {
        return lastSeqno.get();
    }
    public boolean isInErrorState() {
        return errorRun.get() > 0;
    }

    @Override
    public void run() {
        Options options = new Options.Builder()
            .servers(cmd.servers)
            .connectionListener(new OutputConnectionListener(LABEL))
            .errorListener(new OutputErrorListener(LABEL) {})
            .maxReconnects(-1)
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStream js = nc.jetStream();
            //noinspection InfiniteLoopStatement
            while (true) {
                if (lastSeqno.get() == -1) {
                    Output.write(LABEL, "Starting Publish");
                    lastSeqno.set(0);
                }
                try {
                    PublishAck pa = js.publish(cmd.subject, null);
                    lastSeqno.set(pa.getSeqno());
                    if (errorRun.get() > 0) {
                        Output.write(LABEL, "Restarting Publish");
                    }
                    errorRun.set(0);
                }
                catch (Exception e) {
                    if (errorRun.incrementAndGet() == 1) {
                        Output.write(LABEL, e.getMessage());
                    }
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(ThreadLocalRandom.current().nextLong(pubDelay));
                }
                catch (InterruptedException ignore) {}
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
