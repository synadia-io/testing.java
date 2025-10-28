// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos.support;

public class CommandLineConsumer {
    public final ConsumerType consumerType;
    public final ConsumerKind consumerKind;
    public final int batchSize;
    public final long expiresIn;

    public CommandLineConsumer(String consumerKind) {
        this.consumerType = ConsumerType.Push;
        this.consumerKind = ConsumerKind.instance(consumerKind);
        batchSize = 0;
        expiresIn = 0;
    }

    public CommandLineConsumer(String consumerType, String consumerKind, int batchSize, long expiresIn) {
        this.consumerType = ConsumerType.instance(consumerType);
        this.consumerKind = ConsumerKind.instance(consumerKind);
        if (batchSize < 1) {
            throw new IllegalArgumentException("Invalid Batch Size:" + batchSize);
        }
        this.batchSize = batchSize;
        if (expiresIn < 1_000) {
            throw new IllegalArgumentException("Expires must be >= 1000ms");
        }
        this.expiresIn = expiresIn;
    }

    @Override
    public String toString() {
        if (consumerType == ConsumerType.Simple) {
            return consumerType.toString().toLowerCase() +
                " " + consumerKind.toString().toLowerCase() +
                " " + batchSize +
                " " + expiresIn;
        }
        return consumerType.toString().toLowerCase() +
            " " + consumerKind.toString().toLowerCase();
    }
}
