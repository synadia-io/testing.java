package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.jsmulti.JsMulti;
import io.nats.jsmulti.settings.Arguments;
import io.nats.jsmulti.settings.Context;
import io.synadia.CommandLine;
import io.synadia.TestingApplication;
import io.synadia.Workload;
import io.synadia.utils.TestingOptionsFactory;

public class Multi extends Workload {
    public void init(CommandLine commandLine) {
        init("Multi", commandLine);
    }

    public void runWorkload() throws Exception {
        if (params.createStream) {
            createStream(params.streamConfig);
        }

        TestingOptionsFactory.COMMAND_LINE = commandLine;
        TestingOptionsFactory.PARAMS = params;

        Options options = getAdminOptions();
        try (Connection nc = Nats.connect(options)) {
            KeyValue kv = nc.keyValue(params.statsBucket);
            String cv = System.getenv("JNATS_VERSION");
            if (cv != null && !cv.isEmpty()) {
                kv.put(Watch.CV, cv);
                kv.put(Watch.CV_SOURCE, "Env JNATS_VERSION");
            }
            else {
                kv.put(Watch.CV, Nats.CLIENT_VERSION);
                kv.put(Watch.CV_SOURCE, "Nats.CLIENT_VERSION");
            }
        }

        Arguments a = Arguments.instance().addJsonConfig(params.jvMultiConfig.toJson());
        a.appClass(TestingApplication.class);
        a.optionsFactory("io.synadia.utils.TestingOptionsFactory");
        for (int i = 0; i < a.args.size(); i++) {
            String k = a.args.get(i);
            String v = a.args.get(++i);
            debug("Multi Arg", k, v);
        }

        Context ctx = new Context(a);
        ((TestingApplication) ctx.app).initTesting(this);
        JsMulti.run(ctx);
    }
}
