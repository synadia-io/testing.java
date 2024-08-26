package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.synadia.CommandLine;
import io.synadia.Debug;
import io.synadia.Params;

public abstract class Workload {
    protected final CommandLine commandLine;
    protected final Params params;

    public Workload(CommandLine commandLine) {
        this.commandLine = commandLine;
        params = new Params(commandLine.paramsFile);
    }

    abstract void subRunWorkload() throws Exception;

    public void runWorkload() throws Exception {
        if (params.createStream) {
            try (Connection nc = Nats.connect(params.managementServer)) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                try {
                    //noinspection DataFlowIssue
                    jsm.deleteStream(params.streamConfig.getName());
                }
                catch (Exception ignore) {
                }
                while (true) {
                    try {
                        jsm.addStream(params.streamConfig);
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
                Debug.info("Workload", e);
                throw e;
            }
        }
        subRunWorkload();
    }
}
