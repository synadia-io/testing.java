// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StreamInfo;
import io.synadia.chaos.support.CommandLine;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.chaos.Output.flat;

public class Monitor implements Runnable, java.util.function.Consumer<String> {

    static final String MONITOR_LABEL = "MONITOR";
    static final long REPORT_FREQUENCY = 10000;
    static final int SHORT_REPORTS = 50;

    final CommandLine cmd;
    final Publisher publisher;
    final List<ConnectableConsumer> consumers;
    final AtomicBoolean reportFull;

    public Monitor(CommandLine cmd, Publisher publisher, List<ConnectableConsumer> consumers) {
        this.cmd = cmd;
        this.publisher = publisher;
        this.consumers = consumers;
        reportFull = new AtomicBoolean(true);
    }

    @Override
    public void accept(String s) {
        reportFull.set(true);
    }

    @Override
    public void run() {
        OutputConnectionListener connectionListener = new OutputConnectionListener(MONITOR_LABEL);
        connectionListener.afterFunction = (c, et, t, d) -> reportFull.set(true);

        Options options = new Options.Builder()
            .servers(cmd.servers)
            .connectionListener(connectionListener)
            .errorListener(new OutputErrorListener(MONITOR_LABEL))
            .maxReconnects(-1)
            .build();

        long started = System.currentTimeMillis();
        int shortReportsOwed = 0;
        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            //noinspection InfiniteLoopStatement
            while (true) {
                //noinspection BusyWait
                Thread.sleep(REPORT_FREQUENCY);
                try {
                    StringBuilder conReport = new StringBuilder();
                    if (reportFull.get()) {
                        StreamInfo si = jsm.getStreamInfo(cmd.stream);
                        if (si == null) {
                            Output.write(MONITOR_LABEL, "Stream info not available (" + cmd.stream + ")");
                        }
                        else {
                            Output.write(MONITOR_LABEL, flat(si.getConfiguration()).replace("StreamConfiguration{", "Stream {"));
                            if (cmd.crServers > 1 && si.getClusterInfo() != null) {
                                Output.write(MONITOR_LABEL, si.getClusterInfo().toString().replace("ClusterInfo{ ", "Cluster {"));
                            }
                        }
                        reportFull.set(false);
                        if (consumers != null) {
                            for (ConnectableConsumer con : consumers) {
                                con.refreshInfo();
                            }
                        }
                    }
                    if (shortReportsOwed < 1) {
                        shortReportsOwed = SHORT_REPORTS;
                        if (consumers != null) {
                            for (ConnectableConsumer con : consumers) {
                                conReport.append("\n").append(con.label).append(" | Last Sequence: ").append(con.getLastReceivedSequence());
                            }
                        }
                    }
                    else {
                        shortReportsOwed--;
                        if (consumers != null) {
                            for (ConnectableConsumer con : consumers) {
                                conReport.append(" | ")
                                    .append(con.name)
                                    .append(": ")
                                    .append(con.getLastReceivedSequence());
                            }
                        }
                    }

                    String pubReport = "";
                    if (publisher != null) {
                        pubReport = " | Publisher: " + publisher.getLastSeqno() +
                            (publisher.isInErrorState() ? " (Paused)" : " (Running)");
                    }
                    Output.write(MONITOR_LABEL, "Uptime: " + uptime(started) + pubReport + conReport);
                }
                catch (Exception e) {
                    Output.write(MONITOR_LABEL, e.getMessage());
                    reportFull.set(true);
                }
            }
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static String uptime(long started) {
        return Duration.ofMillis(System.currentTimeMillis() - started).toString().replace("PT", "");
    }
}
