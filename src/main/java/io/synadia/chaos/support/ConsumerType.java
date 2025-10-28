// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos.support;

public enum ConsumerType {
    Simple, Fetch, Push;

    public static ConsumerType instance(String text) {
        for (ConsumerType t : ConsumerType.values()) {
            if (t.name().equalsIgnoreCase(text)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Invalid ConsumerType: " + text);
    }
}
