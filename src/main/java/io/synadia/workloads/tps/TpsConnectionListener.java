// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.synadia.utils.Debug;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TpsConnectionListener implements ConnectionListener {

    public AtomicBoolean disconnected = new AtomicBoolean(false);
    public AtomicBoolean reconnected = new AtomicBoolean(false);

    private final String label;
    private final List<String> servers;
    private final boolean receiver;

    public TpsConnectionListener(String labelSuffix, List<String> servers, boolean receiver) {
        this.label = "CL-" + labelSuffix;
        this.servers = servers;
        this.receiver = receiver;
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        connectionEvent(conn, type, null, null);
    }

    @Override
    public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
        boolean print = false;
        if (type == Events.CONNECTED || type == Events.CLOSED) {
            print = true;
        }
        else if (type == Events.DISCONNECTED) {
            disconnected.set(true);
            print = true;
        }
        else if (type == Events.RECONNECTED) {
            reconnected.set(true);
            print = true;
        }
        else if (receiver && type == Events.RESUBSCRIBED) {
            print = true;
        }
        if (print) {
            int ix = servers.indexOf(uriDetails);
            String details = ix == -1 ? uriDetails : "server " + ix + " (" + uriDetails + ")";
            if (time == null) {
                Debug.info(label, "%s(%s)", type.getEvent(), conn.getStatus(), details);
            }
            else {
                Debug.info(label, "[%s]", Debug.simpleTime(time), "%s(%s)", type.getEvent(), conn.getStatus(), details);
            }
        }
    }
}
