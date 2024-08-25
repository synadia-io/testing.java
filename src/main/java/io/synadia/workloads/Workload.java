package io.synadia.workloads;

import io.nats.client.Options;
import io.synadia.CommandLine;
import io.synadia.DebugListener;
import io.synadia.Params;
import io.synadia.types.ExecutorServiceType;

import java.util.concurrent.Executors;

public class Workload {
    protected final CommandLine commandLine;
    protected final Params params;

    protected final DebugListener listener;
    protected final Options options;

    public Workload(CommandLine commandLine) {
        this.commandLine = commandLine;
        params = new Params(commandLine.paramsFile);
        listener = new DebugListener();

        Options.Builder builder = Options.builder()
            .server(Options.DEFAULT_URL)
            .connectionListener(listener)
            .errorListener(listener);

        if (params.executorServiceType == ExecutorServiceType.Virtual) {
            builder.executor(Executors.newVirtualThreadPerTaskExecutor());
        }

        options = builder.build();
    }
}
