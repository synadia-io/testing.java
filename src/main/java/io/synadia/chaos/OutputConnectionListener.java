// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;

public class OutputConnectionListener implements ConnectionListener {
    private static String message(Events event) {
        return switch (event) {
            case CONNECTED -> "Connected";
            case CLOSED -> "Closed";
            case DISCONNECTED -> "Disconnected";
            case RECONNECTED -> "Re-Connected";
            case RESUBSCRIBED -> "Subscriptions Re-Established";
            case DISCOVERED_SERVERS -> "Servers Discovered";
            case LAME_DUCK -> "Entering lame duck mode";
        };
    };

    private final String outputLabel;
    final OutputListenerReducer reducer;

    public OutputConnectionListener(String outputLabel) {
        this.outputLabel = outputLabel;
        reducer = new OutputListenerReducer(outputLabel);
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        reducer.output("CL/" + message(type));
    }
}
