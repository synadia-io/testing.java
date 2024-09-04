package io.synadia;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.jsmulti.JsMulti;
import io.nats.jsmulti.settings.Arguments;
import io.nats.jsmulti.settings.Context;
import io.synadia.tools.Debug;

import java.io.IOException;

public class MultiWorkload extends Workload {
    public MultiWorkload(String workloadName, CommandLine commandLine) {
        super(workloadName, commandLine);
    }

    public void runWorkload() throws Exception {
        if (params.createStream) {
            createStream();
        }

        Arguments a = Arguments.instance().addJsonConfig(params.jvMultiConfig.toJson());
        a.appClass(TestingApplication.class);
        Debug.info(workloadName, "Multi-Arguments");
        a.printCommandLineFormatted();
        Context ctx = new Context(a);

        System.out.println("\n\n");
        Debug.info(workloadName, "trackingBucket %s", params.trackingBucket);
        ((TestingApplication) ctx.app).setBucket(params.trackingBucket);

        JsMulti.run(ctx);
    }

    private void createStream() throws IOException, JetStreamApiException, InterruptedException {
        Debug.info(workloadName, "Create Stream", params.streamConfig.getName());
        try (Connection nc = Nats.connect(params.adminServer)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            try {
                Debug.info(workloadName, "Delete Stream", params.streamConfig.getName());
                jsm.deleteStream(params.streamConfig.getName());
            }
            catch (Exception ignore) {
            }
            while (true) {
                try {
                    Debug.info(workloadName, "Add Stream", jsm.addStream(params.streamConfig));
                    break;
                }
                catch (JetStreamApiException j) {
                    if (j.getErrorCode() != 10058) {
                        throw j;
                    }
                }
            }
        }
        catch (Exception e) {
            Debug.info(workloadName, "Exception", e);
            throw e;
        }
    }
}
