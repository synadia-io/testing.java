// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.cml;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.synadia.utils.Debug;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.workloads.cml.CmlUtils.id;

public class CmlConnectionListener implements ConnectionListener {

    private final String label;
    private final List<String> servers;
    private final boolean receiver;

    public final AtomicBoolean disconnected;
    public final AtomicBoolean reconnected;

    public CmlConnectionListener(String labelSuffix, List<String> servers, boolean receiver) {
        this.label = "CL-" + labelSuffix;
        this.servers = servers;
        this.receiver = receiver;
        disconnected = new AtomicBoolean(false);
        reconnected = new AtomicBoolean(false);
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        connectionEvent(conn, type, null, null);
    }

    @Override
    public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
        boolean print = false;
        String cid = null;
        if (type == Events.CONNECTED || type == Events.CLOSED) {
            cid = id(conn);
            print = true;
        }
        else if (type == Events.DISCONNECTED) {
            disconnected.set(true);
            print = true;
        }
        else if (type == Events.RECONNECTED) {
            reconnected.set(true);
            cid = id(conn);
            print = true;
        }
        else if (receiver && type == Events.RESUBSCRIBED) {
            print = true;
        }
        if (print) {
            int ix = servers.indexOf(uriDetails);
            String details = ix == -1 ? uriDetails : "server " + ix + " (" + uriDetails + ")";
            if (time == null) {
                Debug.info(label, "%s -> %s", type.getEvent(), conn.getStatus(), details, cid);
            }
            else {
                Debug.info(label, "%s -> %s @ %s", type.getEvent(), conn.getStatus(), Debug.simpleTime(time), details, cid);
            }
        }
    }
}
