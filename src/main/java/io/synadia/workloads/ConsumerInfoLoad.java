/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.jsmulti.JsMulti;
import io.nats.jsmulti.settings.Arguments;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;

public class ConsumerInfoLoad extends Workload {

    public ConsumerInfoLoad(CommandLine commandLine) {
        super(commandLine);
    }

    @Override
    public void subRunWorkload() throws Exception {
        Arguments a = Arguments.instance()
            .addJsonConfig(params.jvMultiConfig.toJson());
        Context ctx = new Context(a);
        a.printCommandLineFormatted();
        JsMulti.run(ctx);
    }

    private void foo(Context ctx, Connection nc, Stats stats, int id) throws Exception {
        JetStream js = nc.jetStream(ctx.getJetStreamOptions());
   }
}
