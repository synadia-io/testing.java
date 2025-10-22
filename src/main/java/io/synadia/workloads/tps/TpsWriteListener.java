package io.synadia.workloads.tps;

import io.nats.client.WriteListener;
import io.nats.client.impl.NatsMessage;
import io.synadia.utils.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.nats.jsmulti.shared.Stats.format3;
import static io.synadia.workloads.tps.TpsUtils.extractMessageId;

public class TpsWriteListener extends WriteListener {
    private final AtomicLong lastBufferedMessageId;
    private final AtomicBoolean phase1;
    private final List<String> gapList;
    private final String label;
    private final String testSubject;

    public TpsWriteListener(String labelSuffix, String testSubject) {
        this.label = "WL-" + labelSuffix;
        this.testSubject = testSubject;
        lastBufferedMessageId = new AtomicLong(0);
        gapList = new ArrayList<>();
        phase1 = new AtomicBoolean(true);
    }

    public void startPhase2() {
        phase1.set(false);
    }

    @Override
    public void buffered(NatsMessage msg) {
        if (phase1.get() && msg.getSubject().equals(testSubject)) {
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

    public List<String> getGapList() {
        return gapList;
    }
}
