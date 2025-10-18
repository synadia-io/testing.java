// Copyright 2021 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.support.Status;
import io.synadia.utils.Debug;

public class DebugErrorListener extends OutputErrorListener {
    boolean printStackTrace;

    public DebugErrorListener() {
        this(null, false);
    }

    public DebugErrorListener(boolean printStackTrace) {
        this(null, printStackTrace);
    }

    public DebugErrorListener(String label) {
        this(label, false);
    }

    public DebugErrorListener(String elLabel, boolean printStackTrace) {
        super(elLabel == null ? "EL" : elLabel);
        this.printStackTrace = printStackTrace;
    }

    private String string(Connection conn) {
        return "Connection(" + conn.hashCode() + ") " + conn.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void errorOccurred(final Connection conn, final String error) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "errorOccurred", string(conn), "Error: " + error);
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("CallToPrintStackTrace")
    @Override
    public void exceptionOccurred(final Connection conn, final Exception exp) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "exceptionOccurred:", string(conn), exp);
            if (exp.getCause() != null) {
                Debug.info(outputLabel, "            cause:", exp.getCause());
                if (printStackTrace) {
                    exp.getCause().printStackTrace();
                }
            }
            else if (printStackTrace) {
                exp.printStackTrace();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void slowConsumerDetected(final Connection conn, final Consumer consumer) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "slowConsumerDetected", string(conn), consumer);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageDiscarded(final Connection conn, final Message msg) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "messageDiscarded", string(conn), "Message: " + msg);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeatAlarm(final Connection conn, final JetStreamSubscription sub,
                               final long lastStreamSequence, final long lastConsumerSequence) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "heartbeatAlarm", string(conn), sub, "lastStreamSequence: " + lastStreamSequence, "lastConsumerSequence: " + lastConsumerSequence);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unhandledStatus(final Connection conn, final JetStreamSubscription sub, final Status status) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "unhandledStatus", string(conn), sub, "Status: " + status);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusWarning(Connection conn, JetStreamSubscription sub, Status status) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "pullStatusWarning", string(conn), sub, "Status: " + status);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusError(Connection conn, JetStreamSubscription sub, Status status) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "pullStatusError", string(conn), sub, "Status: " + status);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flowControlProcessed(Connection conn, JetStreamSubscription sub, String id, FlowControlSource source) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "flowControlProcessed", string(conn), sub, "FlowControlSource: " + source);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void socketWriteTimeout(Connection conn) {
        if (outputLabel != null) {
            Debug.info(outputLabel, "socketWriteTimeout", string(conn));
        }
    }
}
