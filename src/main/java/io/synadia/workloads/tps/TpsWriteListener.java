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
    private final AtomicBoolean running;
    private final List<String> gapList;

    public TpsWriteListener() {
        lastBufferedMessageId = new AtomicLong(0);
        gapList = new ArrayList<>();
        running = new AtomicBoolean(true);
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void runStarted(int instanceHashCode) {
//        Debug.info("WL", "%s run started", printable(instanceHashCode));
    }

    @Override
    public void runEnded(int instanceHashCode) {
//        Debug.info("WL", "%s run ended", printable(instanceHashCode));
    }

    private static String printable(int instanceHashCode) {
        return Integer.toHexString(instanceHashCode).toUpperCase();
    }

    @Override
    public void buffered(NatsMessage msg) {
        if (running.get()) {
            long mid = extractMessageId(msg);
            long expected = lastBufferedMessageId.incrementAndGet();
            if (expected == 1) {
                Debug.info("WL", "buffering started");
            }
            else if (mid != expected) {
                long diff = mid - expected;
                gapList.add("expected:" + expected + ", received:" + mid + ", diff" + diff );
                Debug.info("WL", "!!!!! buffered message id gap", "expected: %s", format3(expected), "actual: %s ", format3(mid), "difference: %s", diff);
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
