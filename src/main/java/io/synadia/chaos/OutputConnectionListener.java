// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;

import static io.synadia.chaos.ConnectionUtils.eventMessage;

public class OutputConnectionListener implements ConnectionListener {

    protected final String outputLabel;
    protected final AfterFunction afterFunction;

    public interface AfterFunction {
        void afterOutput(Connection conn, Events type);
    }

    public OutputConnectionListener(String outputLabel) {
        this(outputLabel, (c, t) -> {});
    }

    public OutputConnectionListener(String outputLabel, AfterFunction afterFunction) {
        this.outputLabel = outputLabel;
        this.afterFunction = afterFunction;
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        Output.write(outputLabel, "CL/" + eventMessage(type));
        afterFunction.afterOutput(conn, type);
    }

    @Override
    public void connectionEvent(Connection conn, Events type, String uriDetails) {
        Output.write(outputLabel, "CL/" + eventMessage(type) + "/" + uriDetails);
        afterFunction.afterOutput(conn, type);
    }
}
