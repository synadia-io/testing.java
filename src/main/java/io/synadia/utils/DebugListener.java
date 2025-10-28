// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.utils;

import io.nats.client.*;
import io.nats.client.support.Status;

public class DebugListener implements ErrorListener, ConnectionListener {
    private String string(Connection conn) {
        return "Connection(" + conn.hashCode() + ") " + conn.getStatus();
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        Debug.info("CL", string(conn), "Event: {}", type.getEvent());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void errorOccurred(final Connection conn, final String error) {
        Debug.info("EL", "errorOccurred", string(conn), "Error: " + error);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("CallToPrintStackTrace")
    @Override
    public void exceptionOccurred(final Connection conn, final Exception exp) {
        Debug.info("EL", "exceptionOccurred:", string(conn), exp);
        if (exp.getCause() != null) {
            Debug.info("EL", "            cause:", exp.getCause());
            exp.getCause().printStackTrace();
        }
        else {
            exp.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void slowConsumerDetected(final Connection conn, final Consumer consumer) {
        Debug.info("EL", "slowConsumerDetected", string(conn), consumer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageDiscarded(final Connection conn, final Message msg) {
        Debug.info("EL", "messageDiscarded", string(conn), "Message: " + msg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeatAlarm(final Connection conn, final JetStreamSubscription sub,
                               final long lastStreamSequence, final long lastConsumerSequence) {
        Debug.info("EL", "heartbeatAlarm", string(conn), sub, "lastStreamSequence: " + lastStreamSequence, "lastConsumerSequence: " + lastConsumerSequence);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unhandledStatus(final Connection conn, final JetStreamSubscription sub, final Status status) {
        Debug.info("EL", "unhandledStatus", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusWarning(Connection conn, JetStreamSubscription sub, Status status) {
        Debug.info("EL", "pullStatusWarning", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusError(Connection conn, JetStreamSubscription sub, Status status) {
        Debug.info("EL", "pullStatusError", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flowControlProcessed(Connection conn, JetStreamSubscription sub, String id, FlowControlSource source) {
        Debug.info("EL", "flowControlProcessed", string(conn), sub, "FlowControlSource: " + source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void socketWriteTimeout(Connection conn) {
        Debug.info("EL", "socketWriteTimeout", string(conn));
    }
}
