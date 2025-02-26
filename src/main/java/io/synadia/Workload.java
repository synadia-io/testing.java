package io.synadia;

import io.nats.client.*;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.StreamConfiguration;
import io.synadia.utils.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.nats.jsmulti.shared.Utils.sleep;

@SuppressWarnings("SameParameterValue")
public abstract class Workload {
    protected String workLabel;
    protected CommandLine commandLine;
    protected Params params;

    public Workload() {}

    public abstract void init(CommandLine commandLine);

    public abstract void runWorkload() throws Exception;

    protected void init(String defaultLabel, CommandLine commandLine) {
        this.workLabel = commandLine.action == null ? defaultLabel : commandLine.action;
        this.commandLine = commandLine;
        this.params = new Params(commandLine.paramsFiles);

        Debug.info("Environment", "JNats %s", Nats.CLIENT_VERSION);
        commandLine.debug();
        params.debug();
    }

    protected void debug(String label, String k, String v) {
        Debug.info(this.workLabel + " " + label, k, v);
    }

    protected static void jitter(long jitter) {
        if (jitter > 0) {
            sleep(ThreadLocalRandom.current().nextLong(jitter));
        }
    }

    protected String getArg(String name) {
        String key = name + "=";
        for (String arg : commandLine.args) {
            if (arg.startsWith(key)) {
                return arg.substring(key.length() + 1);
            }
        }
        return null;
    }

    protected boolean containsFlag(String name) {
        return commandLine.args.contains(name);
    }

    protected String getStringArgFromPosition(int pos) {
        String arg = null;
        if (commandLine.args.size() >= pos) {
            arg = commandLine.args.get(pos - 1);
        }
        return arg;
    }

    protected int getIntArgFromPosition(int pos, int dflt) {
        String arg = getStringArgFromPosition(pos);
        return arg == null ? dflt : Integer.parseInt(arg);
    }

    protected long getLongArgFromPosition(int pos, long dflt) {
        String arg = getStringArgFromPosition(pos);
        return arg == null ? dflt : Long.parseLong(arg);
    }

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

    protected static void safeDeleteStream(JetStreamManagement jsm, String streamName) {
        try { jsm.deleteStream(streamName); } catch (Exception ignore) {}
    }

    protected void createStream(StreamConfiguration streamConfig) throws Exception {
        try (Connection nc = Nats.connect(getAdminOptions())) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            createStream(streamConfig, jsm);
        }
    }
    protected void createStream(StreamConfiguration streamConfig, JetStreamManagement jsm) throws Exception {
        Debug.info(workLabel, "Create Stream", streamConfig.getName());
        String streamName = streamConfig.getName();
        List<String> streamNames = jsm.getStreamNames();
        if (streamNames.contains(streamName)) {
            Debug.info(workLabel, "Removing existing stream", streamName);
            jsm.deleteStream(streamName);
            Thread.sleep(200); // the server needs time to process the delete
        }
        create(() -> jsm.addStream(streamConfig));
    }

    protected void createBucket(String bucket, KeyValueManagement kvm) throws Exception {
        Debug.info(workLabel, "Create Bucket", bucket);
        List<String> bucketNames = kvm.getBucketNames();
        if (bucketNames.contains(bucket)) {
            Debug.info(workLabel, "Removing existing bucket", bucket);
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
                Debug.info(workLabel, manager.manage());
                break;
            }
            catch (JetStreamApiException j) {
                if (j.getErrorCode() != 10058) {
                    throw j;
                }
            }
        }
    }

    public static final String PADDING = "                                                                                                                                                                ";
    public static String pad(Object s, int width) {
        return (s + PADDING).substring(0, width);
    }

    public List<Options> roundRobinOptions(int numConnections) {
        List<Options> options = new ArrayList<>(numConnections);
        int cx = -1;
        for (int i = 0; i < numConnections; i++) {
            if (++cx == params.servers.size()) {
                cx = 0;
            }
            options.add(getOptions(params.servers.get(cx)));
        }
        return options;
    }
}
