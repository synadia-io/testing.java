// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.synadia.utils.Debug;

import static io.synadia.chaos.ConnectionUtils.eventMessage;

public class DebugConnectionListener implements ConnectionListener {

    protected final String outputLabel;
    protected final boolean connectionEventsOnly;

    public DebugConnectionListener(String outputLabel) {
        this.outputLabel = outputLabel;
        this.connectionEventsOnly = false;
    }

    public DebugConnectionListener(String outputLabel, boolean connectionEventsOnly) {
        this.outputLabel = outputLabel;
        this.connectionEventsOnly = connectionEventsOnly;
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        if (connectionEventsOnly && !type.isConnectionEvent()) {
            return;
        }
        Debug.info(outputLabel, "CL", eventMessage(type));
    }

    @Override
    public void connectionEvent(Connection conn, Events type, String uriDetails) {
        if (connectionEventsOnly && !type.isConnectionEvent()) {
            return;
        }
        Debug.info(outputLabel, "CL", eventMessage(type), uriDetails);
    }
}
