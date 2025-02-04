package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.ObjectStoreConfiguration;
import io.nats.jsmulti.JsMulti;
import io.nats.jsmulti.settings.Arguments;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.ActionRunner;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;
import io.synadia.TestingApplication;
import io.synadia.Workload;
import io.synadia.utils.GZipper;

import java.io.IOException;

import static io.nats.jsmulti.shared.Utils.report;
import static io.nats.jsmulti.shared.Utils.reportAndTrackMaybe;

public class ObjectStoreMulti extends Workload {
    public void init(CommandLine commandLine) {
        init("os", commandLine);
    }

    public void runWorkload() throws Exception {
        if (params.createStream) {
            createStream(params.streamConfig);
        }
        OsActionRunner.BUCKET = params.testingStreamName;

        Arguments a = Arguments.instance().addJsonConfig(params.jvMultiConfig.toJson());
        a.appClass(TestingApplication.class);

        // hack to get custom config to the runner itself
        // store the state in static variables since the class
        // is initiated by a factory
        OsActionRunner.COMPRESSION = params.customBoolean("compression");
        OsActionRunner.GZIP = params.customBoolean("gzip");
        OsActionRunner.CLEANUP = params.customInt("cleanup", 100);

        a.customAction(OsActionRunner.class);
        for (int i = 0; i < a.args.size(); i++) {
            String k = a.args.get(i);
            String v = a.args.get(++i);
            debug("Object Store Arg", k, v);
        }

        Context ctx = new Context(a);
        ((TestingApplication) ctx.app).initTesting(this);
        JsMulti.run(ctx);
    }

    static class OsActionRunner implements ActionRunner {
        static String BUCKET = "b";
        static boolean COMPRESSION = false;
        static boolean GZIP = false;
        static int CLEANUP = 1;

        Connection nc;
        ObjectStoreOptions oso;
        ObjectStoreManagement osm;
        ObjectStore os;

        @Override
        public void run(Context ctx, Connection nc, Stats stats, int id) throws Exception {
            try {
                oso = ObjectStoreOptions.builder(ctx.getJetStreamOptions()).build();
                osm = nc.objectStoreManagement(oso);
                initBucket();
                long pubTarget = ctx.getPubCount(id);
                long published = 0;
                long unReported = 0;
                report(ctx, published, "Begin Object Store Run");
                while (published < pubTarget) {
                    jitter(ctx.jitter);
                    byte[] payload = ctx.getPayload();
                    String name = "key" + published;
                    stats.start();
                    if (GZIP) {
                        GZipper gz = new GZipper();
                        gz.zip(payload);
                        payload = gz.finish();
                    }
                    os.put(name, payload);
                    stats.stopAndCount(ctx.payloadSize);
                    unReported = reportAndTrackMaybe(ctx, ++published, ++unReported, "Custom", stats);
                    if (published % CLEANUP == 0) {
                        initBucket();
                    }
                }
                report(ctx, published, "Completed Object Store Run");
            }
            finally {
                deleteBucket();
                this.nc = null;
                oso = null;
                osm = null;
                os = null;
            }
        }

        private void initBucket() throws IOException, JetStreamApiException {
            deleteBucket();
            osm.create(ObjectStoreConfiguration.builder()
                .name(BUCKET)
                .compression(COMPRESSION)
                .build());
            os = nc.objectStore(BUCKET, oso);
        }

        private void deleteBucket() throws IOException {
            try {
                osm.delete(BUCKET);
            }
            catch (JetStreamApiException ignore) {}
        }
    }
}
