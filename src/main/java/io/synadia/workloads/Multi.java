package io.synadia.workloads;

import io.nats.jsmulti.JsMulti;
import io.nats.jsmulti.settings.Arguments;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;
import io.synadia.TestingApplication;
import io.synadia.Workload;
import io.synadia.utils.TestingOptionsFactory;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

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
        List<Stats> stats = JsMulti.run(ctx);

        String fileName = "stats_" + commandLine.action + "_" + commandLine.id + ".txt";
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            PrintStream ps = new PrintStream(fos);
            Stats.report(stats, ps);
            ps.flush();
        }
    }
}
