// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.*;
import io.nats.client.api.OrderedConsumerConfiguration;
import io.synadia.chaos.support.CommandLine;
import io.synadia.chaos.support.ConsumerKind;

import java.io.IOException;

public class SimpleConsumer extends ConnectableConsumer {
    final StreamContext sc;
    final ConsumerContext cc;
    final OrderedConsumerContext occ;
    final MessageConsumer mc;

    public SimpleConsumer(CommandLine cmd, ConsumerKind consumerKind, int batchSize, long expiresIn) throws IOException, InterruptedException, JetStreamApiException {
        super(cmd, "sc", consumerKind);

        sc = nc.getStreamContext(cmd.stream);

        ConsumeOptions co = ConsumeOptions.builder()
            .batchSize(batchSize)
            .expiresIn(expiresIn)
            .build();

        if (consumerKind == ConsumerKind.Ordered) {
            OrderedConsumerConfiguration ocConfig = new OrderedConsumerConfiguration().filterSubjects(cmd.subject);
            cc = null;
            occ = sc.createOrderedConsumer(ocConfig);
            mc = occ.consume(co, handler);
        }
        else {
            occ = null;
            cc = sc.createOrUpdateConsumer(newCreateConsumer().build());
            mc = cc.consume(co, handler);
        }
        Output.write(label, mc.getConsumerName());
    }

    @Override
    public void refreshInfo() {
        updateLabel(mc.getConsumerName());
    }
}
