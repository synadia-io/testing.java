package io.synadia;

import io.nats.client.ErrorListener;
import io.nats.client.Options;
import io.synadia.tools.Debug;

public abstract class Workload {
    public final String workloadName;
    public final CommandLine commandLine;
    public final Params params;

    public Workload(String workloadName, CommandLine commandLine) {
        this.workloadName = workloadName;
        this.commandLine = commandLine;
        commandLine.debug();
        params = new Params(commandLine.paramsFiles);
    }

    public abstract void runWorkload() throws Exception;

    protected Options getAdminOptions() {
        return getAdminOptions(params.adminServer);
    }

    protected Options getAdminOptions(String server) {
        Debug.info(workloadName, "admin options", server);
        return new Options.Builder()
            .server(server)
            .connectionListener((x, y) -> {})
            .errorListener(new ErrorListener() {})
            .build();
    }
}
