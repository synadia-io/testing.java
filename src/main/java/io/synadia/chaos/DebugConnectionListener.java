// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.synadia.utils.Debug;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DebugConnectionListener extends OutputConnectionListener {

    AtomicInteger connectionId = new AtomicInteger(0);
    ConcurrentHashMap<Integer, String> connectionHashToId = new ConcurrentHashMap<>();

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
            Debug.info(outputLabel, "%s/%s/%s", id(conn), conn.getStatus(), type.getEvent());
        }
    }

    @Override
    public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
        if (outputLabel != null) {
            String ts = time == null ? Debug.simpleTime() : Debug.simpleTime(time);
            Debug.info(outputLabel, "%s@%s", id(conn), ts, "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails);
        }
    }

    private String id(Connection conn) {
        return connectionHashToId.computeIfAbsent(conn.hashCode(), k -> Integer.toString(connectionId.incrementAndGet()));
    }
}
