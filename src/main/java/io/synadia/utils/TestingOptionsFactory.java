package io.synadia.utils;

import io.nats.client.Options;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.OptionsFactory;
import io.synadia.CommandLine;
import io.synadia.Params;

import java.util.concurrent.Executors;

public class TestingOptionsFactory implements OptionsFactory {
    public static CommandLine COMMAND_LINE;
    public static Params PARAMS;

    @Override
    public Options getOptions(Context ctx, OptionsType ot) {
        Options.Builder b = this.getOptionsBuilder(ctx, ot);
        if (PARAMS.optionsVirtualThreads) {
            b.executor(Executors.newVirtualThreadPerTaskExecutor());
        }
        return b.build();
    }
}
