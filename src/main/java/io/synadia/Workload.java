package io.synadia;

import io.nats.client.*;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.StreamConfiguration;
import io.synadia.utils.Debug;

import java.util.List;

public abstract class Workload {
    public final String label;
    public final CommandLine commandLine;
    public final Params params;

    public Workload(String defaultLabel, CommandLine commandLine) {
        this.label = commandLine.action == null ? defaultLabel : commandLine.action;
        this.commandLine = commandLine;
        this.params = new Params(commandLine.paramsFiles);

        Debug.info("Environment", "JNats %s", Nats.CLIENT_VERSION);
        commandLine.debug();
        params.debug();
    }

    public void debug(String label, String k, String v) {
        Debug.info(this.label + " " + label, k, v);
    }

    public abstract void runWorkload() throws Exception;

    protected Options getAdminOptions() {
        return getOptions(params.adminServer);
    }

    protected Options getOptions(String server) {
        return new Options.Builder()
            .server(server)
            .connectionListener((x, y) -> {})
            .errorListener(new ErrorListener() {})
            .build();
    }

    protected void createStream(StreamConfiguration streamConfig) throws Exception {
        try (Connection nc = Nats.connect(getAdminOptions())) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            createStream(streamConfig, jsm);
        }
    }
    protected void createStream(StreamConfiguration streamConfig, JetStreamManagement jsm) throws Exception {
        Debug.info(label, "Create Stream", streamConfig.getName());
        String streamName = streamConfig.getName();
        List<String> streamNames = jsm.getStreamNames();
        if (streamNames.contains(streamName)) {
            Debug.info(label, "Removing existing stream", streamName);
            jsm.deleteStream(streamName);
            Thread.sleep(200); // the server needs time to process the delete
        }
        create(() -> jsm.addStream(streamConfig));
    }

    protected void createBucket(String bucket, KeyValueManagement kvm) throws Exception {
        Debug.info(label, "Create Bucket", bucket);
        List<String> bucketNames = kvm.getBucketNames();
        if (bucketNames.contains(bucket)) {
            Debug.info(label, "Removing existing bucket", bucket);
            kvm.delete(bucket);
            Thread.sleep(200); // the server needs time to process the delete
        }
        KeyValueConfiguration kvc = KeyValueConfiguration.builder()
            .name(bucket)
            .maxHistoryPerKey(1)
            .build();
        create(() -> kvm.create(kvc));
    }

    interface Manager {
        Object manage() throws Exception;
    }

    private void create(Manager manager) throws Exception {
        while (true) {
            try {
                Debug.info(label, manager.manage());
                break;
            }
            catch (JetStreamApiException j) {
                if (j.getErrorCode() != 10058) {
                    throw j;
                }
            }
        }

    }
}
