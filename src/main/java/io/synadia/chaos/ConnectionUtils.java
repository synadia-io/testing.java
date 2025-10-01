// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;

public class ConnectionUtils {
    public static String eventMessage(ConnectionListener.Events event) {
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

    public static String statusMessage(Connection.Status status) {
        return switch (status) {
            case CONNECTED -> "Connected";
            case CLOSED -> "Closed";
            case DISCONNECTED -> "Disconnected";
            case RECONNECTING -> "Re-Connecting";
            case CONNECTING -> "Connecting";
        };
    };
}
