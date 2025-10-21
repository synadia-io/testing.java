// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.Message;
import io.synadia.utils.Debug;

public class TpsErrorListener implements ErrorListener {

    public volatile boolean readClosed = false;

    private final String label;

    public TpsErrorListener(String labelSuffix) {
        this.label = "EL-" + labelSuffix;
    }

    private String string(Connection conn) {
        return "Connection(" + conn.hashCode() + ") " + conn.getStatus();
    }

    @Override
    public void errorOccurred(final Connection conn, final String error) {
        Debug.info(label, "errorOccurred", string(conn), "Error: " + error);
        if (error.contains("Read channel closed")) {
            readClosed = true;
        }
    }

    @Override
    public void exceptionOccurred(final Connection conn, final Exception exp) {
        Debug.info(label, "exceptionOccurred:", string(conn), exp);
        if (exp.getMessage().contains("NullPointerException")) {
            System.out.println("\n---------------------------------");
            exp.printStackTrace(System.out);
            System.out.println("---------------------------------\n");
        }
        if (exp.getCause() != null) {
            Debug.info(label, "            cause:", exp.getCause());
        }
    }

    @Override
    public void slowConsumerDetected(final Connection conn, final Consumer consumer) {
        Debug.info(label, "slowConsumerDetected", string(conn), consumer);
    }

    @Override
    public void messageDiscarded(final Connection conn, final Message msg) {
        Debug.info(label, "messageDiscarded", string(conn), "Message: " + msg);
    }

    @Override
    public void socketWriteTimeout(Connection conn) {
        Debug.info(label, "socketWriteTimeout", string(conn));
    }
}
