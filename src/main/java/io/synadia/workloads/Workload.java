package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.synadia.CommandLine;
import io.synadia.Params;
import io.synadia.tools.Debug;

public abstract class Workload {
    protected final CommandLine commandLine;
    protected final Params params;

    public Workload(CommandLine commandLine) {
        this.commandLine = commandLine;
        commandLine.debug();
        params = new Params(commandLine.paramsFile);
    }

    abstract void subRunWorkload() throws Exception;

    public void runWorkload() throws Exception {
        if (params.createStream) {
            Debug.info("Create Stream", params.streamConfig.getName());
            try (Connection nc = Nats.connect(params.managementServer)) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                try {
                    //noinspection DataFlowIssue
                    Debug.info("Delete Stream", params.streamConfig.getName());
                    jsm.deleteStream(params.streamConfig.getName());
                }
                catch (Exception ignore) {
                }
                while (true) {
                    try {
                        Debug.info("Add Stream", jsm.addStream(params.streamConfig));
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
