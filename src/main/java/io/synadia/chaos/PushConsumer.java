// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Dispatcher;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.PushSubscribeOptions;
import io.synadia.chaos.support.CommandLine;
import io.synadia.chaos.support.ConsumerKind;

import java.io.IOException;

public class PushConsumer extends ConnectableConsumer {
    final Dispatcher d;
    final JetStreamSubscription sub;

    public PushConsumer(CommandLine cmd, ConsumerKind consumerKind) throws IOException, InterruptedException, JetStreamApiException {
        super(cmd, "pu", consumerKind);

        d = nc.createDispatcher();

        PushSubscribeOptions pso = PushSubscribeOptions.builder()
            .stream(cmd.stream)
            .configuration(newCreateConsumer()
                .idleHeartbeat(1000)
                .build())
            .ordered(consumerKind == ConsumerKind.Ordered)
            .build();

        sub = js.subscribe(cmd.subject, d, handler, false, pso);
        Output.write(label, sub.getConsumerName());
    }

    @Override
    public void refreshInfo() {
        updateLabel(sub.getConsumerName());
    }
}
