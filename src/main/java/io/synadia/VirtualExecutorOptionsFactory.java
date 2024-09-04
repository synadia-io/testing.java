package io.synadia;

import io.nats.client.Options;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.OptionsFactory;

import java.util.concurrent.Executors;

public class VirtualExecutorOptionsFactory implements OptionsFactory {
    @Override
    public Options getOptions(Context ctx, OptionsType ot) {
        return this.getOptionsBuilder(ctx, ot)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    }
}
