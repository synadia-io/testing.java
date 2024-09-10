package io.synadia;

import io.nats.jsmulti.JsMulti;
import io.nats.jsmulti.settings.Arguments;
import io.nats.jsmulti.settings.Context;

public class MultiWorkload extends Workload {
    public MultiWorkload(String defaultLabel, CommandLine commandLine) {
        super(defaultLabel, commandLine);
    }

    public void runWorkload() throws Exception {
        if (params.createStream) {
            createStream(params.streamConfig);
        }

        Arguments a = Arguments.instance().addJsonConfig(params.jvMultiConfig.toJson());
        a.appClass(TestingApplication.class);
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
