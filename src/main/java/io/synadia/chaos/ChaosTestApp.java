// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.synadia.chaos.support.CommandLine;
import io.synadia.chaos.support.CommandLineConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ChaosTestApp {

    public static final String MANAGE_LABEL = "MANAGE";
    public static final String APP_LABEL = "APP";

    public static String[] MANUAL_ARGS = (
              "--stream manual-chaos-stream"
            + " --subject manual-chaos-subject"
            + " --create"
            + " --publish"
            + " --pubjitter 100"
            + " --simple ordered 100 5000"
            + " --simple durable 100 5000"
            + " --fetch durable 100 5000"
            + " --push ordered"
            + " --push durable"
    ).split(" ");

    public static void main(String[] args) throws Exception {
        CommandLine cmd = new CommandLine(args == null || args.length == 0 ? MANUAL_ARGS : args);
        Monitor monitor;

        try {
            Output.write(APP_LABEL, cmd.toString().replace(" --", "    \n--"));
            CountDownLatch waiter = new CountDownLatch(1);

            ChaosArguments chaosArgs = new ChaosArguments()
                .servers(cmd.crServers)
                .initialDelay(cmd.crInitialDelay)
                .delay(cmd.crDelay)
                .downTime(cmd.crDownTime)
                .random(cmd.crRandom);

            if (cmd.crWorkDirectory != null) {
                chaosArgs.workDirectory(cmd.crWorkDirectory);
            }

            ChaosRunner chaosRunner = ChaosRunner.start(chaosArgs, new Output());
            HealthChecker healthChecker = new HealthChecker(chaosRunner);
            Thread hcThread = new Thread(healthChecker);
            hcThread.start();

            if (cmd.create) {
                Options options = cmd.makeManagmentOptions(MANAGE_LABEL);
                try (Connection nc = Nats.connect(options)) {
                    Output.write(MANAGE_LABEL, nc.getServerInfo().toString());
                    JetStreamManagement jsm = nc.jetStreamManagement();
                    createOrReplaceStream(cmd, jsm);
                }
                catch (Exception e) {
                    Output.errorMessage(MANAGE_LABEL, e.getMessage());
                }
            }

            List<ConnectableConsumer> cons = null;
            if (!cmd.commandLineConsumers.isEmpty()) {
                cons = new ArrayList<>();
                for (CommandLineConsumer clc : cmd.commandLineConsumers) {
                    ConnectableConsumer con = switch (clc.consumerType) {
                        case Push -> new PushConsumer(cmd, clc.consumerKind);
                        case Simple -> new SimpleConsumer(cmd, clc.consumerKind, clc.batchSize, clc.expiresIn);
                        case Fetch -> new SimpleFetchConsumer(cmd, clc.consumerKind, clc.batchSize, clc.expiresIn);
                    };
                    Output.write(APP_LABEL, "Create Consumer", clc.consumerType, clc.consumerKind, con.label);
                    cons.add(con);
                }
            }

            Publisher publisher = null;
            if (cmd.publish) {
                publisher = new Publisher(cmd, cmd.pubjitter);
                Thread pubThread = new Thread(publisher);
                pubThread.start();
            }

            monitor = new Monitor(cmd, publisher, cons);
            Thread monThread = new Thread(monitor);
            monThread.start();

            long runtime = cmd.runtime < 1 ? Long.MAX_VALUE : cmd.runtime;
            //noinspection ResultOfMethodCallIgnored
            waiter.await(runtime, TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
        finally {
            System.exit(0);
        }
    }

    public static void createOrReplaceStream(CommandLine cmd, JetStreamManagement jsm) {
        try {
            jsm.deleteStream(cmd.stream);
        }
        catch (Exception ignore) {}
        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(cmd.stream)
                .storageType(StorageType.File)
                .subjects(cmd.subject)
                .replicas(cmd.r3 ? 3 : 1)
                .build();
            StreamInfo si = jsm.addStream(sc);
            Output.write(APP_LABEL, "Create Stream\n" + Output.formatted(si.getConfiguration()));
        }
        catch (Exception e) {
            Output.fatalMessage(APP_LABEL, "Failed creating stream: '" + cmd.stream + "' " + e);
            System.exit(-1);
        }
    }
}
