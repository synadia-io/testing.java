// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.impl.NatsServerPool;
import io.nats.client.support.NatsUri;
import io.synadia.utils.Debug;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class TpsServerPool extends NatsServerPool {
    private final String label;
    private final List<String> servers;

    public TpsServerPool(String labelSuffix, List<String> servers) {
        this.label = "SP-"+ labelSuffix;
        this.servers = servers;
    }

    @Override
    public @Nullable NatsUri nextServer() {
        NatsUri nuri = super.nextServer();
        int ix = servers.indexOf(nuri.toString());
        if (ix == -1) {
            Debug.info(label, "nextServer: %s", nuri);
        }
        else {
            String details = "server " + ix + " (" + nuri + ")";
            Debug.info(label, "nextServer: %s", details);
        }

        return nuri;
    }
}
