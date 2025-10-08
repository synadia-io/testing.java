package io.synadia.workloads.support;

import io.nats.client.impl.Headers;
import io.nats.jsmulti.shared.HeaderSupplier;

import java.util.concurrent.atomic.AtomicInteger;

import static io.nats.client.support.NatsJetStreamConstants.*;

public class BenchHeaderSupplier implements HeaderSupplier {
    AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Headers getHeaders() {
        int count = counter.incrementAndGet();
        Headers headers = new Headers();
        headers.put(NATS_SUBJECT, "subject-" + count);
        headers.put(NATS_SEQUENCE, "seq-" + count);
        headers.put(NATS_TIMESTAMP, "ts-" + count);
        headers.put(NATS_STREAM, "stream-" + count);
        headers.put(NATS_LAST_SEQUENCE, "lseq-" + count);
        headers.put(NATS_NUM_PENDING, "nnp-" + count);
        headers.put(CONSUMER_STALLED_HDR, "csh-" + count);
        headers.put(MSG_SIZE_HDR, "msh-" + count);
        headers.put(NATS_MARKER_REASON_HDR, "nmrh-" + count);
        headers.put(NATS_PENDING_MESSAGES, "npm-" + count);
        headers.put(NATS_PENDING_BYTES, "npb-" + count);
        headers.put("N-Starts-With-Known-Byte", "Starts-With-Known-Byte-N-" + count);
        headers.put("K-Starts-With-Known-Byte", "Starts-With-Known-Byte-K-" + count);
        headers.put("X-Starts-With-Unknown-Byte", "Starts-With-Unknown-Byte-" + count);
        return headers;
    }
}
