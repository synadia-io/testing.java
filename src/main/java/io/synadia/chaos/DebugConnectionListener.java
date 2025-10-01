// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.synadia.utils.Debug;

import static io.synadia.chaos.ConnectionUtils.eventMessage;

public class DebugConnectionListener extends OutputConnectionListener {
    public DebugConnectionListener(String outputLabel) {
        super(outputLabel);
    }

    public DebugConnectionListener(String outputLabel, AfterFunction afterFunction) {
        super(outputLabel, afterFunction);
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        Debug.info(outputLabel, "CL", eventMessage(type));
        afterFunction.afterOutput(conn, type);
    }

    @Override
    public void connectionEvent(Connection conn, Events type, String uriDetails) {
        Debug.info(outputLabel, "CL", eventMessage(type), uriDetails);
        afterFunction.afterOutput(conn, type);
    }
}
