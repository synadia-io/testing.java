// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.synadia.utils.Debug;

public class TpsConnectionListener implements ConnectionListener {

    public volatile int connects = 0;
    public volatile int disconnects = 0;
    public volatile int closes = 0;
    public volatile int reconnects = 0;

    @Override
    public void connectionEvent(Connection conn, Events type) {
        connectionEvent(conn, type, null, null);
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    @Override
    public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
        if (time == null) {
            Debug.info("CL", "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails);
        }
        else {
            Debug.info("CL", "[%s]", Debug.simpleTime(time), "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails);
        }
        switch (type) {
            case CONNECTED: connects++; break;
            case DISCONNECTED: disconnects++; break;
            case CLOSED: closes++; break;
            case RECONNECTED: reconnects++; break;
        }
    }
}
