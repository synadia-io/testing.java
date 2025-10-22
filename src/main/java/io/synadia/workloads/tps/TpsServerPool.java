// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.impl.NatsServerPool;
import io.nats.client.support.NatsUri;
import io.synadia.utils.Debug;
import org.jspecify.annotations.Nullable;

public class TpsServerPool extends NatsServerPool {
    private final String label;

    public TpsServerPool(String label) {
        this.label = label;
    }

    @Override
    public @Nullable NatsUri nextServer() {
        NatsUri nuri = super.nextServer();
        Debug.info(label, "nextServer: %s", nuri);
        return nuri;
    }
}
