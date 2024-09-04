package io.synadia;

import io.nats.client.ErrorListener;
import io.nats.client.Options;
import io.synadia.tools.Debug;

public abstract class Workload {
    protected final String workloadName;
    protected final CommandLine commandLine;
    protected final Params params;

    public Workload(String workloadName, CommandLine commandLine) {
        this.workloadName = workloadName;
        this.commandLine = commandLine;
        commandLine.debug();
        params = new Params(commandLine.paramsFiles);
    }

    public abstract void runWorkload() throws Exception;

    protected Options getAdminOptions(String label) {
        Debug.info(label, "connecting to: " + params.adminServer);
        return new Options.Builder()
            .server(params.adminServer)
            .connectionListener((x, y) -> {})
            .errorListener(new ErrorListener() {})
            .build();
    }
}
