// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.synadia.utils.Debug;

import static io.synadia.chaos.ConnectionUtils.eventMessage;

public class DebugConnectionListener extends OutputConnectionListener {

    public DebugConnectionListener(String outputLabel) {
        super(outputLabel, true);
    }

    public DebugConnectionListener(String outputLabel, boolean connectionEventsOnly) {
        super(outputLabel, connectionEventsOnly);
    }

    @Override
    public void reportFunction(CustomFunction reportFunction) {
        throw new UnsupportedOperationException("Report Function cannot be overridden.");
    }

    @Override
    protected void report(Connection conn, Events type, String uriDetails) {
        if (uriDetails == null) {
            Debug.info(outputLabel, "CL", eventMessage(type));
        }
        else {
            Debug.info(outputLabel, "CL", eventMessage(type), uriDetails);
        }
    }
}
