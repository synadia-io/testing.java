package io.nats.client.impl;

import io.synadia.utils.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.nats.jsmulti.shared.Stats.format3;
import static io.synadia.workloads.tps.TpsUtils.extractMessageId;

public class TpsWriteListener extends WriteListener {
    private final String label;
    private final String testSubject;
    private final String controlSubject;
    public final AtomicInteger phase;
    private final AtomicLong lastBufferedMessageId;
    public final AtomicInteger protocolsBuffered;
    public final AtomicInteger controlsBuffered;
    public final List<String> gapList;

    public TpsWriteListener(String labelSuffix, String testSubject, String controlSubject) {
        super(null);
        this.label = "WL-" + labelSuffix;
        this.testSubject = testSubject;
        this.controlSubject = controlSubject;
        phase = new AtomicInteger(1);
        lastBufferedMessageId = new AtomicLong(0);
        protocolsBuffered = new AtomicInteger(0);
        controlsBuffered = new AtomicInteger(0);
        gapList = new ArrayList<>();
    }

    public void startPhase2() {
        phase.set(2);
    }

    @Override
    public void buffered(NatsMessage msg, NatsConnectionWriter.Mode mode) {
        if (msg.getSubject() == null) {
            Debug.info(label, mode, msg);
            protocolsBuffered.incrementAndGet();
            return;
        }
        if (msg.getSubject().equals(controlSubject)) {
            Debug.info(label, mode, msg);
            controlsBuffered.incrementAndGet();
            return;
        }

        if (phase.get() == 1 && msg.getSubject().equals(testSubject)) {
            long mid = extractMessageId(msg);
            long expected = lastBufferedMessageId.incrementAndGet();
            if (expected == 1) {
                Debug.info(label, "buffering started");
            }
            else if (mid != expected) {
                long diff = mid - expected;
                gapList.add("expected:" + expected + ", received:" + mid + ", diff" + diff);
                Debug.info(label, "!!!!! buffered message id gap", "expected: %s", format3(expected), "actual: %s ", format3(mid), "difference: %s", diff);
            }
            lastBufferedMessageId.set(mid);
        }
    }
}
