// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.synadia.chaos.support.CommandLine;
import io.synadia.chaos.support.ConsumerKind;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ConnectableConsumer {

    protected final Connection nc;
    protected final JetStream js;
    protected final OutputErrorListener errorListener;
    protected final OutputConnectionListener connectionListener;
    protected final AtomicLong lastReceivedSequence;
    protected final MessageHandler handler;
    protected final ConsumerKind consumerKind;

    protected final CommandLine cmd;
    protected String initials;
    protected String name;
    protected String durableName;
    protected String label;

    public ConnectableConsumer(CommandLine cmd, String initials, ConsumerKind consumerKind) throws IOException, InterruptedException, JetStreamApiException {
        this.cmd = cmd;
        lastReceivedSequence = new AtomicLong(0);
        this.consumerKind = consumerKind;
        switch (consumerKind) {
            case Durable:
                durableName = initials + "-dur-" + new NUID().nextSequence();
                name = durableName;
                break;
            case Ephemeral:
                durableName = null;
                name = initials + "-eph-" + new NUID().nextSequence();
                break;
            case Ordered:
                durableName = null;
                name = initials + "-ord-" + new NUID().nextSequence();
                break;
        }
        this.initials = initials;
        label = name + " (" + consumerKind.name() + ")";

        connectionListener = new OutputConnectionListener(label);
        connectionListener.afterFunction = (c, et, t, d) -> refreshInfo();
        errorListener = new OutputErrorListener(label);

        Options options = cmd.makeOptions(connectionListener, errorListener);
        nc = Nats.connect(options);
        js = nc.jetStream();

        handler = this::onMessage;
    }

    public void onMessage(Message m) throws InterruptedException {
        m.ack();
        long seq = m.metaData().streamSequence();
        long lastSeq = lastReceivedSequence.get();
        lastReceivedSequence.set(seq);
    }

    public abstract void refreshInfo();

    protected void updateLabel(String conName) {
        if (!name.contains(conName))
        {
            int at = name.lastIndexOf("-");
            name = name.substring(0, at + 1) + conName;
            label = name + " (" + consumerKind.name() + ")";
        }
    }

    public long getLastReceivedSequence() {
        return lastReceivedSequence.get();
    }

    protected ConsumerConfiguration.Builder newCreateConsumer() {
        return recreateConsumer(0);
    }

    private ConsumerConfiguration.Builder recreateConsumer(long last) {
        return ConsumerConfiguration.builder()
            .name(consumerKind == ConsumerKind.Ordered ? null : name)
            .durable(durableName)
            .deliverPolicy(last == 0 ? DeliverPolicy.All : DeliverPolicy.ByStartSequence)
            .startSequence(last == 0 ? -1 : last + 1)
            .filterSubject(cmd.subject);
    }
}
