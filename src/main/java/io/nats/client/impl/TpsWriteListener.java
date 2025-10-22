package io.nats.client.impl;

import io.nats.client.WriteListener;
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
    private final AtomicInteger controlsBuffered;
    private final List<String> gapList;

    public TpsWriteListener(String labelSuffix, String testSubject, String controlSubject) {
        super(null);
        this.label = "WL-" + labelSuffix;
        this.testSubject = testSubject;
        this.controlSubject = controlSubject;
        phase = new AtomicInteger(1);
        lastBufferedMessageId = new AtomicLong(0);
        controlsBuffered = new AtomicInteger(0);
        gapList = new ArrayList<>();
    }

    public void startPhase2() {
        phase.set(2);
    }

    public void startPhase3() {
        phase.set(3);
    }

    @Override
    public void buffered(NatsMessage msg) {
        if (msg.getSubject().equals(controlSubject)) {
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

    public long getLastBufferedMessageId() {
        return lastBufferedMessageId.get();
    }

    public int getControlsBuffered() {
        return controlsBuffered.get();
    }

    public List<String> getGapList() {
        return gapList;
    }
}
