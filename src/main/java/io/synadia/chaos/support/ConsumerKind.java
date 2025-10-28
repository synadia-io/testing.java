// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos.support;

public enum ConsumerKind {
    Ephemeral, Durable, Ordered;

    public static ConsumerKind instance(String text) {
        for (ConsumerKind k : ConsumerKind.values()) {
            if (k.name().equalsIgnoreCase(text)) {
                return k;
            }
        }
        throw new IllegalArgumentException("Invalid ConsumerKind: " + text);
    }
}
