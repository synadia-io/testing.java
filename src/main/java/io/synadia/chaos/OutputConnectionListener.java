// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;

import static io.synadia.chaos.ConnectionUtils.eventMessage;

public class OutputConnectionListener implements ConnectionListener {

    protected final String outputLabel;
    protected final boolean connectionEventsOnly;
    protected CustomFunction reportFunction;
    protected CustomFunction afterFunction;

    public interface CustomFunction {
        void afterOutput(Connection conn, Events type, Long time, String uriDetails);
    }

    public OutputConnectionListener(String outputLabel) {
        this(outputLabel, false);
    }

    public OutputConnectionListener(String outputLabel, boolean connectionEventsOnly) {
        this.outputLabel = outputLabel;
        this.connectionEventsOnly = connectionEventsOnly;
        reportFunction = this::report;
    }

    public void reportFunction(CustomFunction reportFunction) {
        this.reportFunction = reportFunction == null ? this::report : reportFunction;
    }

    public void afterFunction(CustomFunction afterFunction) {
        this.afterFunction = afterFunction == null ? this::after : afterFunction;
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        if (connectionEventsOnly && !type.isConnectionEvent()) {
            return;
        }
        report(conn, type, null, null);
        afterFunction.afterOutput(conn, type, null, null);
    }

//    @Override
//    public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
//        if (connectionEventsOnly && !type.isConnectionEvent()) {
//            return;
//        }
//        report(conn, type, time, uriDetails);
//        afterFunction.afterOutput(conn, type, time, uriDetails);
//    }

    protected void after(Connection conn, Events type, Long time, String uriDetails) {
    }

    protected void report(Connection conn, Events type, Long time, String uriDetails) {
        if (uriDetails == null) {
            Output.write(outputLabel, "CL/" + eventMessage(type));
        }
        else {
            Output.write(outputLabel, "CL/" + eventMessage(type) + "/" + uriDetails);
        }
    }
}
