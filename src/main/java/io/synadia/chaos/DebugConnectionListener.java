// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.synadia.utils.Debug;

public class DebugConnectionListener extends OutputConnectionListener {

    public DebugConnectionListener() {
        this(null, false);
    }

    public DebugConnectionListener(String clLabel) {
        super(clLabel, false);
    }

    public DebugConnectionListener(boolean connectionEventsOnly) {
        this(null, connectionEventsOnly);
    }

    public DebugConnectionListener(String clLabel, boolean connectionEventsOnly) {
        super(clLabel == null ? "CL" : clLabel, connectionEventsOnly);
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "%s/%s/%s", Integer.toHexString(conn.hashCode()), conn.getStatus(), type.getEvent());
        }
    }

    @Override
    public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "%s@%s", Integer.toHexString(conn.hashCode()).toUpperCase(), time, "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails);
        }
    }
}
