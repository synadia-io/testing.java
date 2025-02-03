package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.jsmulti.JsMulti;
import io.nats.jsmulti.settings.Arguments;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.ActionRunner;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;
import io.synadia.TestingApplication;
import io.synadia.Workload;

import static io.nats.jsmulti.shared.Utils.*;

public class Custom extends Workload {
    public Custom(CommandLine commandLine) {
        super("custom", commandLine);
    }

    public void runWorkload() throws Exception {
        if (params.createStream) {
            createStream(params.streamConfig);
        }

        Arguments a = Arguments.instance().addJsonConfig(params.jvMultiConfig.toJson());
        a.appClass(TestingApplication.class);

        SUBJECT = params.testingStreamSubject;

        if ("publish".equals(params.customString("which"))) {
            a.customAction(CustomPublish.class);
        }

        for (int i = 0; i < a.args.size(); i++) {
            String k = a.args.get(i);
            String v = a.args.get(++i);
            debug("Custom Arg", k, v);
        }

        Context ctx = new Context(a);
        ((TestingApplication) ctx.app).initTesting(this);
        JsMulti.run(ctx);
    }

    static String SUBJECT;

    public static class CustomPublish implements ActionRunner {
        @Override
        public void run(Context ctx, Connection nc, Stats stats, int id) throws Exception {
            final JetStream js = nc.jetStream(ctx.getJetStreamOptions());
            long pubTarget = ctx.getPubCount(id);
            long published = 0;
            long unReported = 0;
            report(ctx, published, "Begin Custom Publish Run");
            while (published < pubTarget) {
                jitter(ctx);
                byte[] payload = ctx.getPayload();
                js.publish(SUBJECT, payload);
                stats.stopAndCount(ctx.payloadSize);
                unReported = reportAndTrackMaybe(ctx, ++published, ++unReported, "Custom", stats);
            }
            report(ctx, published, "Completed Custom Publish Run");
        }
    }
}
