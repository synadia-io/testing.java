package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.jsmulti.JsMulti;
import io.nats.jsmulti.settings.Arguments;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.ActionRunner;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;
import io.synadia.TestingApplication;
import io.synadia.Workload;

import static io.nats.jsmulti.shared.Utils.report;
import static io.nats.jsmulti.shared.Utils.reportAndTrackMaybe;

public class CustomMulti extends Workload {
    public void init(CommandLine commandLine) {
        init("custom multi", commandLine);
    }

    public void runWorkload() throws Exception {
        if (params.createStream) {
            createStream(params.streamConfig);
        }

        Arguments a = Arguments.instance().addJsonConfig(params.jvMultiConfig.toJson());
        a.appClass(TestingApplication.class);

        STREAM = params.testingStreamName;
        SUBJECT = params.testingStreamSubject;

        if ("consumers".equals(params.customString("which"))) {
            a.customAction(CustomActionRunner.class);
            PREFIX = params.customString("prefix");
        }

        for (int i = 0; i < a.args.size(); i++) {
            String k = a.args.get(i);
            String v = a.args.get(++i);
            debug("CustomMulti Arg", k, v);
        }

        Context ctx = new Context(a);
        ((TestingApplication) ctx.app).initTesting(this);
        JsMulti.run(ctx);
    }

    static String STREAM;
    static String SUBJECT;
    static String PREFIX;

    public static class CustomActionRunner implements ActionRunner {
        @Override
        public void run(Context ctx, Connection nc, Stats stats, int id) throws Exception {
            final JetStreamManagement jsm = nc.jetStreamManagement(ctx.getJetStreamOptions());
            long consumerCount = ctx.getPubCount(id);
            long consumers = 0;
            long unReported = 0;
            report(ctx, consumers, "Begin Custom Run");
            while (consumers < consumerCount) {
                stats.start();
                ConsumerConfiguration config = ConsumerConfiguration.builder()
                    .durable(PREFIX + (consumers + 1))
                    .filterSubject(SUBJECT)
                    .build();
                jsm.createConsumer(STREAM, config);
                stats.stopAndCount(ctx.payloadSize);
                unReported = reportAndTrackMaybe(ctx, ++consumers, ++unReported, "Custom Consumers", stats);
            }
            report(ctx, consumers, "Completed Custom Run");
        }
    }
}
