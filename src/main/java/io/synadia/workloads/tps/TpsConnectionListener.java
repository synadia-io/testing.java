// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.synadia.utils.Debug;

import java.util.concurrent.atomic.AtomicBoolean;

public class TpsConnectionListener implements ConnectionListener {

    public AtomicBoolean disconnected = new AtomicBoolean(false);
    public AtomicBoolean reconnected = new AtomicBoolean(false);

    private final String label;

    public TpsConnectionListener(String labelSuffix) {
        this.label = "CL-" + labelSuffix;
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        connectionEvent(conn, type, null, null);
    }

    @Override
    public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
        if (time == null) {
            Debug.info(label, "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails);
        }
        else {
            Debug.info(label, "[%s]", Debug.simpleTime(time), "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails);
        }
        if (type == Events.DISCONNECTED) {
            disconnected.set(true);
        }
        else if (type == Events.RECONNECTED) {
            reconnected.set(true);
        }
    }
}
